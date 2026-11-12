package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 社区关注流收件箱（写扩散）。
 *
 * - 发帖成功后把 postId 推送到粉丝的 Redis ZSET：feed:inbox:{userId}
 * - 读取时取最近 N 条，作为关注流的候选来源
 * - 粉丝数超过阈值（大 V）不做写扩散，退回读时拉取，避免一次发帖扇出过多
 * - Redis 不可用时静默降级：调用方会回退到数据库按关注关系查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityInboxService {

  private static final String INBOX_PREFIX = "feed:inbox:";
  private static final Duration INBOX_TTL = Duration.ofDays(7);
  /** 粉丝数超过该值的作者不做写扩散（读时拉取） */
  private static final int FANOUT_MAX_FOLLOWERS = 5000;

  private final FollowMapper followMapper;
  private final StringRedisTemplate redisTemplate;

  /**
   * 帖子发布后写入粉丝收件箱。失败只记录日志，不影响发帖主流程。
   */
  public void fanoutPost(Long authorId, Long postId, LocalDateTime createTime) {
    if (authorId == null || postId == null) return;
    try {
      List<FollowEntity> followers = followMapper.selectList(
          new LambdaQueryWrapper<FollowEntity>()
              .eq(FollowEntity::getTargetId, authorId)
              .eq(FollowEntity::getStatus, "FOLLOWING")
              // 多取一条用于判断是否超过写扩散阈值，避免把大 V 的全部粉丝加载进内存
              .last("LIMIT " + (FANOUT_MAX_FOLLOWERS + 1)));
      if (followers == null || followers.isEmpty()) return;
      if (followers.size() > FANOUT_MAX_FOLLOWERS) {
        log.info("[community-inbox] skip fanout for celebrity author: authorId={}, followers={}",
            authorId, followers.size());
        return;
      }
      double score = toEpochMillis(createTime);
      String member = String.valueOf(postId);
      for (FollowEntity follower : followers) {
        if (follower.getUserId() == null) continue;
        String key = INBOX_PREFIX + follower.getUserId();
        try {
          redisTemplate.opsForZSet().add(key, member, score);
          redisTemplate.expire(key, INBOX_TTL);
        } catch (Exception e) {
          // 单个粉丝写入失败不影响其他人
          log.debug("[community-inbox] add failed: userId={}, reason={}",
              follower.getUserId(), e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[community-inbox] fanout failed: authorId={}, postId={}, reason={}",
          authorId, postId, e.getMessage());
    }
  }

  /**
   * 读取当前用户收件箱中最近的帖子 ID（按发布时间倒序）。
   */
  public List<Long> recentPostIds(Long userId, int limit) {
    if (userId == null || limit <= 0) return Collections.emptyList();
    try {
      Set<String> members = redisTemplate.opsForZSet()
          .reverseRange(INBOX_PREFIX + userId, 0, limit - 1L);
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
    } catch (Exception e) {
      log.debug("[community-inbox] read failed: userId={}, reason={}", userId, e.getMessage());
      return Collections.emptyList();
    }
  }

  private double toEpochMillis(LocalDateTime time) {
    LocalDateTime value = time == null ? LocalDateTime.now() : time;
    return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
