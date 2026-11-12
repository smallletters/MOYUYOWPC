package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.moyuyo.dao.entity.CommunityPostEntity;
import com.moyuyo.dao.entity.CommunityUserInterestEntity;
import com.moyuyo.dao.mapper.CommunityPostMapper;
import com.moyuyo.dao.mapper.CommunityUserInterestMapper;
import com.moyuyo.service.CommunityInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区兴趣画像：行为信号累加到 mo_user_interest。
 *
 * 设计取舍：
 * - 同步写入（一次 upsert），失败只打日志，不影响点赞/评论等主流程
 * - 权重允许为负：负反馈会抵消同话题/同作者的正权重
 * - Redis 只存兴趣版本号，用于客户端缓存失效；画像本体落库，重启不丢
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityInterestServiceImpl implements CommunityInterestService {

  private static final String DIM_TOPIC = "topic";
  private static final String DIM_AUTHOR = "author";
  private static final String VERSION_PREFIX = "feed:ver:";
  private static final Duration VERSION_TTL = Duration.ofDays(30);

  private final CommunityUserInterestMapper interestMapper;
  private final CommunityPostMapper postMapper;
  private final StringRedisTemplate redisTemplate;

  @Override
  public void onLike(Long userId, Long postId, boolean increase) {
    CommunityPostEntity post = loadPost(postId);
    if (post == null) return;
    double sign = increase ? 1 : -1;
    addWeight(userId, DIM_TOPIC, post.getTopic(), 1.0 * sign);
    addWeight(userId, DIM_AUTHOR, authorKey(post.getUserId()), 0.5 * sign);
  }

  @Override
  public void onComment(Long userId, Long postId) {
    CommunityPostEntity post = loadPost(postId);
    if (post == null) return;
    addWeight(userId, DIM_TOPIC, post.getTopic(), 1.2);
    addWeight(userId, DIM_AUTHOR, authorKey(post.getUserId()), 0.6);
  }

  @Override
  public void onCollect(Long userId, Long postId, boolean increase) {
    CommunityPostEntity post = loadPost(postId);
    if (post == null) return;
    double sign = increase ? 1 : -1;
    addWeight(userId, DIM_TOPIC, post.getTopic(), 1.5 * sign);
    addWeight(userId, DIM_AUTHOR, authorKey(post.getUserId()), 0.8 * sign);
  }

  @Override
  public void onFollow(Long userId, Long targetId, boolean increase) {
    if (userId == null || targetId == null) return;
    addWeight(userId, DIM_AUTHOR, String.valueOf(targetId), increase ? 2.0 : -2.0);
  }

  @Override
  public void onNotInterested(Long userId, Long postId) {
    CommunityPostEntity post = loadPost(postId);
    if (post == null) return;
    addWeight(userId, DIM_TOPIC, post.getTopic(), -3.0);
    addWeight(userId, DIM_AUTHOR, authorKey(post.getUserId()), -2.0);
  }

  @Override
  public Map<String, Double> weights(Long userId, String dimension) {
    if (userId == null || dimension == null) return Collections.emptyMap();
    try {
      List<CommunityUserInterestEntity> rows = interestMapper.selectList(
          new LambdaQueryWrapper<CommunityUserInterestEntity>()
              .eq(CommunityUserInterestEntity::getUserId, userId)
              .eq(CommunityUserInterestEntity::getDimension, dimension)
              .orderByDesc(CommunityUserInterestEntity::getWeight)
              .last("LIMIT 30"));
      Map<String, Double> result = new LinkedHashMap<>();
      for (CommunityUserInterestEntity row : rows) {
        if (row.getInterestKey() == null || row.getWeight() == null) continue;
        result.put(row.getInterestKey(), row.getWeight().doubleValue());
      }
      return result;
    } catch (Exception e) {
      log.warn("[community-interest] load weights failed: userId={}, dim={}, reason={}",
          userId, dimension, e.getMessage());
      return Collections.emptyMap();
    }
  }

  @Override
  public long feedVersion(Long userId) {
    if (userId == null) return 0L;
    try {
      String value = redisTemplate.opsForValue().get(VERSION_PREFIX + userId);
      return value == null ? 0L : Long.parseLong(value);
    } catch (Exception e) {
      return 0L;
    }
  }

  private CommunityPostEntity loadPost(Long postId) {
    if (postId == null) return null;
    try {
      return postMapper.selectById(postId);
    } catch (Exception e) {
      log.warn("[community-interest] load post failed: postId={}, reason={}", postId, e.getMessage());
      return null;
    }
  }

  private String authorKey(Long authorId) {
    return authorId == null ? null : String.valueOf(authorId);
  }

  /**
   * 权重累加 + 版本号自增。任何异常都被吞掉，避免影响点赞/评论主流程。
   */
  private void addWeight(Long userId, String dimension, String key, double delta) {
    if (userId == null || dimension == null || key == null || key.isBlank() || delta == 0) return;
    try {
      interestMapper.upsert(IdWorker.getId(), userId, dimension, key.trim(),
          BigDecimal.valueOf(delta));
      bumpVersion(userId);
    } catch (Exception e) {
      log.warn("[community-interest] upsert failed: userId={}, dim={}, key={}, reason={}",
          userId, dimension, key, e.getMessage());
    }
  }

  private void bumpVersion(Long userId) {
    try {
      String key = VERSION_PREFIX + userId;
      Long version = redisTemplate.opsForValue().increment(key);
      if (version != null && version == 1L) {
        redisTemplate.expire(key, VERSION_TTL);
      }
    } catch (Exception e) {
      log.debug("[community-interest] bump version failed: userId={}, reason={}",
          userId, e.getMessage());
    }
  }
}
