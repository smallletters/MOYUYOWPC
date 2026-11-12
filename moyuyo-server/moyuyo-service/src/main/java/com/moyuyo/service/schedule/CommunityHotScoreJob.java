package com.moyuyo.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.CommunityPostEntity;
import com.moyuyo.dao.mapper.CommunityPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 社区帖子热度分刷新任务。
 *
 * - 每 5 分钟刷新近 7 天帖子的 hot_score（点赞/评论加权 + 时间衰减）
 * - 把 Top 300 写入 Redis ZSET feed:hot，推荐召回直接读取，避免每次请求扫库
 * - Redis 不可用时只更新数据库，推荐召回会自动回落数据库排序
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityHotScoreJob {

  private static final String HOT_KEY = "feed:hot";
  private static final int HOT_LIMIT = 300;
  private static final Duration HOT_TTL = Duration.ofMinutes(15);

  private final CommunityPostMapper postMapper;
  private final StringRedisTemplate redisTemplate;

  @Scheduled(
      fixedDelayString = "${moyuyo.community.hot-score-fixed-delay-ms:300000}",
      initialDelay = 60000)
  public void refresh() {
    try {
      int updated = postMapper.refreshHotScores();
      log.info("[community-hot] refreshed {} posts", updated);
      cacheTopPosts();
    } catch (Exception e) {
      log.error("[community-hot] refresh failed: {}", e.getMessage(), e);
    }
  }

  private void cacheTopPosts() {
    try {
      List<CommunityPostEntity> top = postMapper.selectList(
          new LambdaQueryWrapper<CommunityPostEntity>()
              .eq(CommunityPostEntity::getStatus, 1)
              .ge(CommunityPostEntity::getCreateTime, LocalDateTime.now().minusDays(7))
              .orderByDesc(CommunityPostEntity::getHotScore)
              .orderByDesc(CommunityPostEntity::getLikes)
              .last("LIMIT " + HOT_LIMIT));
      redisTemplate.delete(HOT_KEY);
      if (top == null || top.isEmpty()) return;

      Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
      for (CommunityPostEntity post : top) {
        double score = post.getHotScore() == null
            ? fallbackScore(post)
            : post.getHotScore().doubleValue();
        tuples.add(new DefaultTypedTuple<>(String.valueOf(post.getId()), score));
      }
      redisTemplate.opsForZSet().add(HOT_KEY, tuples);
      redisTemplate.expire(HOT_KEY, HOT_TTL);
    } catch (Exception e) {
      log.warn("[community-hot] cache top posts failed: {}", e.getMessage());
    }
  }

  private double fallbackScore(CommunityPostEntity post) {
    int likes = post.getLikes() == null ? 0 : Math.max(post.getLikes(), 0);
    int comments = post.getComments() == null ? 0 : Math.max(post.getComments(), 0);
    return 3.0 * likes + 5.0 * comments;
  }
}
