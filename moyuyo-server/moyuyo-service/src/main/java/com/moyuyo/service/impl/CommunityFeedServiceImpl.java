package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.moyuyo.common.dto.community.CommunityFeedPageVO;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.dao.entity.CommunityNotInterestedEntity;
import com.moyuyo.dao.entity.CommunityPostEntity;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.CommunityNotInterestedMapper;
import com.moyuyo.dao.mapper.CommunityPostMapper;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.PetMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.CommunityFeedService;
import com.moyuyo.service.CommunityInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区个性化信息流实现。
 *
 * 分页采用「Redis 快照 + 偏移游标」：
 * - 首次请求生成快照（ZSET，TTL 10 分钟），后续翻页只读同一快照
 * - 避免推荐分数变化导致翻页重复/漏帖，也避免数据库深 offset
 * - Redis 不可用时降级为按发布时间倒序的 offset 分页
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CommunityFeedServiceImpl implements CommunityFeedService {

  private static final int RECALL_LIMIT = 100;
  private static final int SNAPSHOT_MAX = 300;
  private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
  private static final String SNAPSHOT_PREFIX = "feed:snap:";
  private static final String SNAPSHOT_INDEX_PREFIX = "feed:snaps:";
  private static final String HOT_KEY = "feed:hot";
  private static final String SEEN_PREFIX = "feed:seen:";
  private static final Duration SEEN_TTL = Duration.ofDays(7);
  private static final String FALLBACK_CURSOR = "fb";

  private static final int FOLLOW_DAYS = 7;
  private static final int INTEREST_DAYS = 30;
  private static final int FRESH_HOURS = 48;
  private static final int MAX_AUTHOR_IDS = 500;
  private static final int USER_INTEREST_TOPICS = 10;
  private static final int MAX_PER_AUTHOR = 8;
  private static final int MAX_PER_TOPIC = 12;

  private final CommunityPostMapper postMapper;
  private final CommunityNotInterestedMapper notInterestedMapper;
  private final FollowMapper followMapper;
  private final PetMapper petMapper;
  private final UserMapper userMapper;
  private final CommunityInterestService interestService;
  private final CommunityInboxService inboxService;
  private final StringRedisTemplate redisTemplate;

  @Override
  public CommunityFeedPageVO getFeed(Long userId, String tab, String cursor, int size, String topic) {
    int pageSize = Math.min(Math.max(size, 1), 50);
    boolean followTab = "follow".equalsIgnoreCase(tab);
    if (followTab && userId == null) {
      return emptyPage("anon", null);
    }

    String version = feedVersion(userId);
    Cursor parsed = parseCursor(cursor);
    if (parsed.snapId == null) {
      String snapId = followTab
          ? createFollowSnapshot(userId)
          : createRecommendSnapshot(userId, topic);
      if (snapId == null) {
        // Redis 不可用或没有候选：降级为按时间倒序的 offset 分页
        return fallbackChronological(userId, followTab, cursor, pageSize, topic);
      }
      return readSnapshot(userId, snapId, 0, pageSize, version);
    }
    if (FALLBACK_CURSOR.equals(parsed.snapId)) {
      return fallbackChronological(userId, followTab, cursor, pageSize, topic);
    }
    // 快照过期/被负反馈主动失效时，重新生成并从首页返回（客户端按 id 去重）
    if (!hasSnapshot(subject(userId), parsed.snapId)) {
      String snapId = followTab
          ? createFollowSnapshot(userId)
          : createRecommendSnapshot(userId, topic);
      if (snapId == null) {
        return fallbackChronological(userId, followTab, cursor, pageSize, topic);
      }
      return readSnapshot(userId, snapId, 0, pageSize, version);
    }
    return readSnapshot(userId, parsed.snapId, parsed.offset, pageSize, version);
  }

  @Override
  public void invalidateUserSnapshots(Long userId) {
    if (userId == null) return;
    String subject = subject(userId);
    String indexKey = SNAPSHOT_INDEX_PREFIX + subject;
    try {
      Set<String> snapIds = redisTemplate.opsForSet().members(indexKey);
      if (snapIds != null) {
        for (String snapId : snapIds) {
          redisTemplate.delete(SNAPSHOT_PREFIX + subject + ":" + snapId);
        }
      }
      redisTemplate.delete(indexKey);
    } catch (Exception e) {
      log.warn("[community-feed] invalidate snapshots failed: userId={}, reason={}",
          userId, e.getMessage());
    }
  }

  private boolean hasSnapshot(String subject, String snapId) {
    try {
      Long size = redisTemplate.opsForZSet().zCard(SNAPSHOT_PREFIX + subject + ":" + snapId);
      return size != null && size > 0;
    } catch (Exception e) {
      return false;
    }
  }

  // ==================== 推荐快照 ====================

  private String createRecommendSnapshot(Long userId, String topic) {
    Set<Long> hidden = loadHidden(userId);
    Set<Long> followed = loadFollowed(userId);
    Map<String, Double> topicWeights = interestService.weights(userId, "topic");
    Map<String, Double> authorWeights = interestService.weights(userId, "author");
    Set<String> seen = loadSeen(userId);
    Set<String> userIps = new HashSet<>();
    Set<String> userPetTypes = new HashSet<>();
    loadUserPetPreferences(userId, userIps, userPetTypes);

    Map<Long, CommunityPostEntity> candidates = new LinkedHashMap<>();
    LocalDateTime now = LocalDateTime.now();
    String normalizedTopic = normalizeTopic(topic);

    // 1) 关注作者：收件箱 + 关注关系兜底（历史数据没有写扩散）
    List<Long> inboxIds = inboxService.recentPostIds(userId, RECALL_LIMIT);
    if (!inboxIds.isEmpty()) {
      addPosts(candidates, postMapper.selectByIds(inboxIds), userId, hidden, normalizedTopic);
    }
    if (!followed.isEmpty()) {
      LambdaQueryWrapper<CommunityPostEntity> wrapper = baseWrapper(userId, hidden, normalizedTopic)
          .in(CommunityPostEntity::getUserId, followed.stream().limit(MAX_AUTHOR_IDS).collect(Collectors.toList()))
          .ge(CommunityPostEntity::getCreateTime, now.minusDays(FOLLOW_DAYS))
          .orderByDesc(CommunityPostEntity::getCreateTime)
          .last("LIMIT " + RECALL_LIMIT);
      addPosts(candidates, postMapper.selectList(wrapper), userId, hidden, normalizedTopic);
    }

    // 2) 兴趣话题召回
    List<String> positiveTopics = topicWeights.entrySet().stream()
        .filter(e -> e.getValue() != null && e.getValue() > 0)
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(USER_INTEREST_TOPICS)
        .map(Map.Entry::getKey)
        .filter(k -> k != null && !k.isBlank())
        .collect(Collectors.toList());
    if (!positiveTopics.isEmpty() && normalizedTopic == null) {
      LambdaQueryWrapper<CommunityPostEntity> wrapper = baseWrapper(userId, hidden, null)
          .in(CommunityPostEntity::getTopic, positiveTopics)
          .ge(CommunityPostEntity::getCreateTime, now.minusDays(INTEREST_DAYS))
          .orderByDesc(CommunityPostEntity::getLikes)
          .orderByDesc(CommunityPostEntity::getCreateTime)
          .last("LIMIT " + RECALL_LIMIT);
      addPosts(candidates, postMapper.selectList(wrapper), userId, hidden, normalizedTopic);
    }

    // 3) 热门召回：优先读 Redis 热度 ZSET，空则回落数据库
    addPosts(candidates, loadHotPosts(userId, hidden, normalizedTopic), userId, hidden, normalizedTopic);

    // 4) 新鲜召回
    LambdaQueryWrapper<CommunityPostEntity> freshWrapper = baseWrapper(userId, hidden, normalizedTopic)
        .ge(CommunityPostEntity::getCreateTime, now.minusHours(FRESH_HOURS))
        .orderByDesc(CommunityPostEntity::getCreateTime)
        .last("LIMIT " + RECALL_LIMIT);
    addPosts(candidates, postMapper.selectList(freshWrapper), userId, hidden, normalizedTopic);

    // 5) 探索召回：从近期帖子中抽样，避免 ORDER BY RAND() 全表随机扫描
    if (userId != null && normalizedTopic == null) {
      LambdaQueryWrapper<CommunityPostEntity> sampleWrapper = baseWrapper(userId, hidden, null)
          .ge(CommunityPostEntity::getCreateTime, now.minusHours(FRESH_HOURS))
          .orderByDesc(CommunityPostEntity::getCreateTime)
          .last("LIMIT 200");
      List<CommunityPostEntity> pool = postMapper.selectList(sampleWrapper);
      if (pool != null && !pool.isEmpty()) {
        Collections.shuffle(pool);
        addPosts(candidates, pool.subList(0, Math.min(20, pool.size())),
            userId, hidden, null);
      }
    }

    if (candidates.isEmpty()) return null;

    // 曝光去重：优先剔除已看过的帖子；剩余过少时保留，避免老用户刷不出内容
    if (!seen.isEmpty()) {
      Map<Long, CommunityPostEntity> unseen = new LinkedHashMap<>();
      for (Map.Entry<Long, CommunityPostEntity> entry : candidates.entrySet()) {
        if (!seen.contains(String.valueOf(entry.getKey()))) {
          unseen.put(entry.getKey(), entry.getValue());
        }
      }
      if (unseen.size() >= 60) {
        candidates = unseen;
      }
    }

    // 打分 + 多样性重排
    Map<Long, Double> scoreMap = new HashMap<>();
    for (CommunityPostEntity post : candidates.values()) {
      scoreMap.put(post.getId(), scorePost(post, userId, followed, topicWeights, authorWeights,
          seen, userIps, userPetTypes));
    }
    List<CommunityPostEntity> ranked = sortAndDiversify(candidates, scoreMap);

    String subject = subject(userId);
    String snapId = IdWorker.getIdStr();
    if (!saveSnapshot(subject, snapId, ranked, scoreMap)) return null;
    return snapId;
  }

  // ==================== 关注快照 ====================

  private String createFollowSnapshot(Long userId) {
    if (userId == null) return null;
    Set<Long> hidden = loadHidden(userId);
    Map<Long, CommunityPostEntity> candidates = new LinkedHashMap<>();

    List<Long> inboxIds = inboxService.recentPostIds(userId, SNAPSHOT_MAX);
    if (!inboxIds.isEmpty()) {
      addPosts(candidates, postMapper.selectByIds(inboxIds), userId, hidden, null);
    }

    Set<Long> followed = loadFollowed(userId);
    if (!followed.isEmpty()) {
      LambdaQueryWrapper<CommunityPostEntity> wrapper = baseWrapper(userId, hidden, null)
          .in(CommunityPostEntity::getUserId, followed.stream().limit(MAX_AUTHOR_IDS).collect(Collectors.toList()))
          .ge(CommunityPostEntity::getCreateTime, LocalDateTime.now().minusDays(FOLLOW_DAYS))
          .orderByDesc(CommunityPostEntity::getCreateTime)
          .last("LIMIT " + SNAPSHOT_MAX);
      addPosts(candidates, postMapper.selectList(wrapper), userId, hidden, null);
    }
    if (candidates.isEmpty()) return null;

    List<CommunityPostEntity> ordered = candidates.values().stream()
        .sorted((a, b) -> {
          LocalDateTime t1 = a.getCreateTime();
          LocalDateTime t2 = b.getCreateTime();
          if (t1 == null) return 1;
          if (t2 == null) return -1;
          return t2.compareTo(t1);
        })
        .limit(SNAPSHOT_MAX)
        .collect(Collectors.toList());

    Map<Long, Double> scoreMap = new HashMap<>();
    for (CommunityPostEntity post : ordered) {
      scoreMap.put(post.getId(), epochMillis(post.getCreateTime()));
    }
    String snapId = IdWorker.getIdStr();
    if (!saveSnapshot(subject(userId), snapId, ordered, scoreMap)) return null;
    return snapId;
  }

  // ==================== 快照读取 ====================

  private CommunityFeedPageVO readSnapshot(Long userId, String snapId, int offset,
                                           int pageSize, String version) {
    String key = SNAPSHOT_PREFIX + subject(userId) + ":" + snapId;
    try {
      Long total = redisTemplate.opsForZSet().zCard(key);
      if (total == null || total == 0) {
        return emptyPage(version, snapId);
      }
      int safeOffset = Math.max(offset, 0);
      Set<String> members = redisTemplate.opsForZSet()
          .reverseRange(key, safeOffset, safeOffset + pageSize - 1L);
      List<Long> postIds = parseIds(members);
      List<CommunityPostEntity> ordered = loadOrdered(postIds);
      List<CommunityPostVO> records = toVOList(ordered);
      int nextOffset = safeOffset + postIds.size();
      boolean hasMore = nextOffset < total;
      markSeen(userId, ordered);
      return page(records, hasMore ? snapId + "|" + nextOffset : null, hasMore, version, snapId);
    } catch (Exception e) {
      log.warn("[community-feed] read snapshot failed: userId={}, snapId={}, reason={}",
          userId, snapId, e.getMessage());
      return emptyPage(version, snapId);
    }
  }

  // ==================== 降级分页 ====================

  private CommunityFeedPageVO fallbackChronological(Long userId, boolean followTab,
                                                    String cursor, int pageSize, String topic) {
    int offset = 0;
    Cursor parsed = parseCursor(cursor);
    if (parsed.snapId != null && FALLBACK_CURSOR.equals(parsed.snapId)) {
      offset = parsed.offset;
    }
    String version = feedVersion(userId);
    LocalDateTime now = LocalDateTime.now();
    LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
        .eq(CommunityPostEntity::getStatus, 1);
    if (followTab) {
      Set<Long> followed = loadFollowed(userId);
      if (followed.isEmpty()) return emptyPage(version, null);
      wrapper.in(CommunityPostEntity::getUserId,
          followed.stream().limit(MAX_AUTHOR_IDS).collect(Collectors.toList()));
    } else if (userId != null) {
      wrapper.ne(CommunityPostEntity::getUserId, userId);
      wrapper.notInSql(CommunityPostEntity::getId,
          "SELECT post_id FROM mo_community_not_interested WHERE user_id = " + userId);
    }
    String normalizedTopic = normalizeTopic(topic);
    if (normalizedTopic != null && !followTab) {
      wrapper.eq(CommunityPostEntity::getTopic, normalizedTopic);
    }
    wrapper.orderByDesc(CommunityPostEntity::getCreateTime)
        .last("LIMIT " + offset + "," + (pageSize + 1));

    List<CommunityPostEntity> posts = postMapper.selectList(wrapper);
    if (posts == null) posts = Collections.emptyList();
    boolean hasMore = posts.size() > pageSize;
    if (hasMore) {
      posts = new ArrayList<>(posts.subList(0, pageSize));
    }
    int nextOffset = offset + posts.size();
    return page(toVOList(posts), hasMore ? FALLBACK_CURSOR + "|" + nextOffset : null,
        hasMore, version, FALLBACK_CURSOR);
  }

  // ==================== 召回辅助 ====================

  private LambdaQueryWrapper<CommunityPostEntity> baseWrapper(Long userId, Set<Long> hidden,
                                                              String topic) {
    LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
        .eq(CommunityPostEntity::getStatus, 1);
    if (userId != null) {
      wrapper.ne(CommunityPostEntity::getUserId, userId);
      if (!hidden.isEmpty()) {
        // 用子查询代替 NOT IN 大列表，避免负反馈累积后 SQL 膨胀
        wrapper.notInSql(CommunityPostEntity::getId,
            "SELECT post_id FROM mo_community_not_interested WHERE user_id = " + userId);
      }
    }
    if (topic != null) {
      wrapper.eq(CommunityPostEntity::getTopic, topic);
    }
    return wrapper;
  }

  private List<CommunityPostEntity> loadHotPosts(Long userId, Set<Long> hidden, String topic) {
    try {
      Set<String> members = redisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, RECALL_LIMIT - 1L);
      List<Long> ids = parseIds(members);
      if (!ids.isEmpty()) {
        Map<Long, CommunityPostEntity> map = new HashMap<>();
        List<CommunityPostEntity> hotPosts = postMapper.selectByIds(ids);
        if (hotPosts != null) {
          for (CommunityPostEntity post : hotPosts) {
            map.put(post.getId(), post);
          }
          List<CommunityPostEntity> ordered = new ArrayList<>();
          for (Long id : ids) {
            CommunityPostEntity post = map.get(id);
            if (accept(post, userId, hidden, topic)) ordered.add(post);
          }
          if (!ordered.isEmpty()) return ordered;
        }
      }
    } catch (Exception e) {
      log.debug("[community-feed] hot recall from redis failed: {}", e.getMessage());
    }
    return postMapper.selectList(new LambdaQueryWrapper<CommunityPostEntity>()
        .eq(CommunityPostEntity::getStatus, 1)
        .ge(CommunityPostEntity::getCreateTime, LocalDateTime.now().minusDays(FOLLOW_DAYS))
        .orderByDesc(CommunityPostEntity::getHotScore)
        .orderByDesc(CommunityPostEntity::getLikes)
        .last("LIMIT " + RECALL_LIMIT));
  }

  private void addPosts(Map<Long, CommunityPostEntity> target, List<CommunityPostEntity> posts,
                        Long userId, Set<Long> hidden, String topic) {
    if (posts == null) return;
    for (CommunityPostEntity post : posts) {
      if (!accept(post, userId, hidden, topic)) continue;
      target.putIfAbsent(post.getId(), post);
    }
  }

  private boolean accept(CommunityPostEntity post, Long userId, Set<Long> hidden, String topic) {
    if (post == null || !Objects.equals(post.getStatus(), 1)) return false;
    if (userId != null && Objects.equals(post.getUserId(), userId)) return false;
    if (post.getId() != null && hidden.contains(post.getId())) return false;
    if (topic != null && !topic.equals(post.getTopic())) return false;
    return true;
  }

  private Set<Long> loadHidden(Long userId) {
    if (userId == null) return Collections.emptySet();
    List<CommunityNotInterestedEntity> rows = notInterestedMapper.selectList(
        new LambdaQueryWrapper<CommunityNotInterestedEntity>()
            .eq(CommunityNotInterestedEntity::getUserId, userId)
            .last("LIMIT 1000"));
    if (rows == null) return Collections.emptySet();
    Set<Long> ids = new HashSet<>();
    for (CommunityNotInterestedEntity row : rows) {
      if (row.getPostId() != null) ids.add(row.getPostId());
    }
    return ids;
  }

  private Set<Long> loadFollowed(Long userId) {
    if (userId == null) return Collections.emptySet();
    List<FollowEntity> rows = followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, userId)
            .eq(FollowEntity::getStatus, "FOLLOWING"));
    if (rows == null) return Collections.emptySet();
    Set<Long> ids = new LinkedHashSet<>();
    for (FollowEntity row : rows) {
      if (row.getTargetId() != null) ids.add(row.getTargetId());
    }
    return ids;
  }

  private void loadUserPetPreferences(Long userId, Set<String> ips, Set<String> petTypes) {
    if (userId == null) return;
    try {
      List<PetEntity> pets = petMapper.selectList(new LambdaQueryWrapper<PetEntity>()
          .eq(PetEntity::getUserId, userId));
      if (pets == null) return;
      for (PetEntity pet : pets) {
        if (pet.getPreferredIpCode() != null && !pet.getPreferredIpCode().isBlank()) {
          ips.add(pet.getPreferredIpCode().toUpperCase());
        }
        if (pet.getType() != null && !pet.getType().isBlank()) {
          petTypes.add(pet.getType().toUpperCase());
        }
      }
    } catch (Exception e) {
      log.debug("[community-feed] load pet preference failed: userId={}, reason={}",
          userId, e.getMessage());
    }
  }

  private Set<String> loadSeen(Long userId) {
    if (userId == null) return Collections.emptySet();
    String key = SEEN_PREFIX + userId;
    try {
      Long size = redisTemplate.opsForSet().size(key);
      if (size == null || size == 0 || size > 2000) return Collections.emptySet();
      Set<String> members = redisTemplate.opsForSet().members(key);
      return members == null ? Collections.emptySet() : members;
    } catch (Exception e) {
      return Collections.emptySet();
    }
  }

  private void markSeen(Long userId, List<CommunityPostEntity> posts) {
    if (userId == null || posts.isEmpty()) return;
    String key = SEEN_PREFIX + userId;
    try {
      Long size = redisTemplate.opsForSet().size(key);
      if (size != null && size > 5000) {
        redisTemplate.delete(key);
      }
      String[] members = posts.stream().map(p -> String.valueOf(p.getId())).toArray(String[]::new);
      redisTemplate.opsForSet().add(key, members);
      redisTemplate.expire(key, SEEN_TTL);
    } catch (Exception e) {
      log.debug("[community-feed] mark seen failed: userId={}, reason={}", userId, e.getMessage());
    }
  }

  // ==================== 打分与重排 ====================

  private double scorePost(CommunityPostEntity post, Long userId, Set<Long> followed,
                           Map<String, Double> topicWeights, Map<String, Double> authorWeights,
                           Set<String> seen, Set<String> userIps, Set<String> userPetTypes) {
    double score = 0;
    if (userId != null && followed.contains(post.getUserId())) {
      score += 1.5;
    }
    score += 1.2 * normalizedWeight(topicWeights, post.getTopic());
    score += 0.5 * normalizedWeight(authorWeights,
        post.getUserId() == null ? null : String.valueOf(post.getUserId()));
    if (userId != null) {
      if (post.getIpCode() != null && userIps.contains(post.getIpCode().toUpperCase())) {
        score += 0.8;
      }
      if (post.getPetType() != null && userPetTypes.contains(post.getPetType().toUpperCase())) {
        score += 0.6;
      }
    }
    score += 0.8 * hotNormalized(post);
    score += 0.6 * freshness(post.getCreateTime());
    if (userId != null && seen.contains(String.valueOf(post.getId()))) {
      score -= 0.3;
    }
    return score;
  }

  private double normalizedWeight(Map<String, Double> weights, String key) {
    if (weights == null || weights.isEmpty() || key == null) return 0;
    double maxAbs = 0;
    for (Double value : weights.values()) {
      if (value != null) maxAbs = Math.max(maxAbs, Math.abs(value));
    }
    if (maxAbs <= 0) return 0;
    Double value = weights.get(key);
    if (value == null) return 0;
    return Math.max(-1, Math.min(1, value / maxAbs));
  }

  private double hotNormalized(CommunityPostEntity post) {
    double raw = 3.0 * safeInt(post.getLikes()) + 5.0 * safeInt(post.getComments());
    if (post.getHotScore() != null) {
      raw = Math.max(raw, post.getHotScore().doubleValue());
    }
    return Math.min(2.0, Math.log1p(raw) / 5.0);
  }

  private double freshness(LocalDateTime createTime) {
    if (createTime == null) return 0;
    double hours = Duration.between(createTime, LocalDateTime.now()).toMinutes() / 60.0;
    if (hours < 0) hours = 0;
    return Math.exp(-hours / 24.0);
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : Math.max(value, 0);
  }

  private List<CommunityPostEntity> sortAndDiversify(Map<Long, CommunityPostEntity> candidates,
                                                    Map<Long, Double> scoreMap) {
    List<Long> sorted = scoreMap.entrySet().stream()
        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());

    List<CommunityPostEntity> primary = new ArrayList<>();
    List<CommunityPostEntity> reserve = new ArrayList<>();
    Map<Long, Integer> authorCount = new HashMap<>();
    Map<String, Integer> topicCount = new HashMap<>();

    for (Long id : sorted) {
      CommunityPostEntity post = candidates.get(id);
      if (post == null) continue;
      long author = post.getUserId() == null ? 0 : post.getUserId();
      String topic = post.getTopic() == null ? "" : post.getTopic();
      int authorUsed = authorCount.getOrDefault(author, 0);
      int topicUsed = topicCount.getOrDefault(topic, 0);
      if (authorUsed >= MAX_PER_AUTHOR || (!topic.isEmpty() && topicUsed >= MAX_PER_TOPIC)) {
        reserve.add(post);
        continue;
      }
      primary.add(post);
      authorCount.put(author, authorUsed + 1);
      if (!topic.isEmpty()) topicCount.put(topic, topicUsed + 1);
      if (primary.size() >= SNAPSHOT_MAX) break;
    }
    for (CommunityPostEntity post : reserve) {
      if (primary.size() >= SNAPSHOT_MAX) break;
      primary.add(post);
    }
    return primary;
  }

  // ==================== 快照与 VO ====================

  private boolean saveSnapshot(String subject, String snapId, List<CommunityPostEntity> ordered,
                               Map<Long, Double> scoreMap) {
    String key = SNAPSHOT_PREFIX + subject + ":" + snapId;
    try {
      Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
      for (CommunityPostEntity post : ordered) {
        double score = scoreMap.getOrDefault(post.getId(), 0.0);
        tuples.add(new DefaultTypedTuple<>(String.valueOf(post.getId()), score));
      }
      if (tuples.isEmpty()) return false;
      redisTemplate.opsForZSet().add(key, tuples);
      redisTemplate.expire(key, SNAPSHOT_TTL);
      // 记录用户快照索引，便于负反馈时批量失效
      String indexKey = SNAPSHOT_INDEX_PREFIX + subject;
      redisTemplate.opsForSet().add(indexKey, snapId);
      redisTemplate.expire(indexKey, Duration.ofMinutes(60));
      return true;
    } catch (Exception e) {
      log.warn("[community-feed] save snapshot failed: key={}, reason={}", key, e.getMessage());
      return false;
    }
  }

  private List<CommunityPostEntity> loadOrdered(List<Long> ids) {
    if (ids.isEmpty()) return Collections.emptyList();
    List<CommunityPostEntity> posts = postMapper.selectByIds(ids);
    if (posts == null) return Collections.emptyList();
    Map<Long, CommunityPostEntity> map = new HashMap<>();
    for (CommunityPostEntity post : posts) {
      if (Objects.equals(post.getStatus(), 1)) map.put(post.getId(), post);
    }
    List<CommunityPostEntity> ordered = new ArrayList<>();
    for (Long id : ids) {
      CommunityPostEntity post = map.get(id);
      if (post != null) ordered.add(post);
    }
    return ordered;
  }

  private List<CommunityPostVO> toVOList(List<CommunityPostEntity> posts) {
    if (posts.isEmpty()) return Collections.emptyList();
    Set<Long> authorIds = posts.stream()
        .map(CommunityPostEntity::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, UserEntity> userMap = new HashMap<>();
    if (!authorIds.isEmpty()) {
      List<UserEntity> users = userMapper.selectByIds(authorIds);
      if (users != null) {
        for (UserEntity user : users) {
          userMap.put(user.getId(), user);
        }
      }
    }
    List<CommunityPostVO> result = new ArrayList<>(posts.size());
    for (CommunityPostEntity post : posts) {
      CommunityPostVO vo = new CommunityPostVO();
      vo.setId(post.getId());
      vo.setUserId(post.getUserId());
      vo.setPetId(post.getPetId());
      vo.setContent(post.getContent());
      vo.setImages(JsonUtils.parseStringArray(post.getImages()));
      vo.setVideo(post.getVideo());
      vo.setCover(post.getCover());
      vo.setScheduledAt(post.getScheduledAt());
      vo.setTopic(post.getTopic());
      vo.setLikes(post.getLikes());
      vo.setComments(post.getComments());
      vo.setStatus(post.getStatus());
      vo.setCreateTime(post.getCreateTime());
      UserEntity author = userMap.get(post.getUserId());
      if (author != null) {
        vo.setUsername(author.getNickname());
        vo.setAvatar(author.getAvatar());
      }
      result.add(vo);
    }
    return result;
  }

  // ==================== 通用辅助 ====================

  private CommunityFeedPageVO page(List<CommunityPostVO> records, String nextCursor,
                                   boolean hasMore, String version, String snapshotId) {
    CommunityFeedPageVO page = new CommunityFeedPageVO();
    page.setRecords(records);
    page.setNextCursor(nextCursor);
    page.setHasMore(hasMore);
    page.setFeedVersion(version);
    page.setSnapshotId(snapshotId);
    return page;
  }

  private CommunityFeedPageVO emptyPage(String version, String snapshotId) {
    return page(Collections.emptyList(), null, false, version, snapshotId);
  }

  private String feedVersion(Long userId) {
    return userId == null ? "anon" : String.valueOf(interestService.feedVersion(userId));
  }

  private String subject(Long userId) {
    return userId == null ? "anon" : String.valueOf(userId);
  }

  private String normalizeTopic(String topic) {
    if (topic == null || topic.isBlank()) return null;
    return topic.trim();
  }

  private List<Long> parseIds(Set<String> members) {
    if (members == null || members.isEmpty()) return Collections.emptyList();
    List<Long> ids = new ArrayList<>(members.size());
    for (String member : members) {
      try {
        ids.add(Long.parseLong(member));
      } catch (NumberFormatException ignored) {
        // 脏数据跳过
      }
    }
    return ids;
  }

  private double epochMillis(LocalDateTime time) {
    LocalDateTime value = time == null ? LocalDateTime.now() : time;
    return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  private Cursor parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) return new Cursor(null, 0);
    int idx = cursor.lastIndexOf('|');
    if (idx <= 0) return new Cursor(null, 0);
    String snapId = cursor.substring(0, idx);
    int offset;
    try {
      offset = Integer.parseInt(cursor.substring(idx + 1));
    } catch (NumberFormatException e) {
      offset = 0;
    }
    if (snapId.isBlank()) return new Cursor(null, 0);
    return new Cursor(snapId, Math.max(offset, 0));
  }

  private static class Cursor {
    private final String snapId;
    private final int offset;

    private Cursor(String snapId, int offset) {
      this.snapId = snapId;
      this.offset = offset;
    }
  }
}
