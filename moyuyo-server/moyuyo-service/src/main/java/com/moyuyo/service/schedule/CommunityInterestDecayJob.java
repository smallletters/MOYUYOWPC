package com.moyuyo.service.schedule;

import com.moyuyo.dao.mapper.CommunityUserInterestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 社区兴趣画像每日衰减任务。
 *
 * 历史兴趣每天乘 0.95，避免早期偏好长期主导推荐；低于 0.02 的权重直接清理。
 * 与热度任务错开执行时间，避免同一时刻集中写库。
 * 多实例部署时用 Redis 日期键做分布式锁，保证一天只衰减一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityInterestDecayJob {

  private static final String LOCK_PREFIX = "job:community-interest-decay:";
  private static final Duration LOCK_TTL = Duration.ofHours(6);

  private final CommunityUserInterestMapper interestMapper;
  private final StringRedisTemplate redisTemplate;

  @Scheduled(cron = "${moyuyo.community.interest-decay-cron:0 30 4 * * *}")
  public void decay() {
    if (!acquireLock()) return;
    try {
      int updated = interestMapper.decayAll();
      int deleted = interestMapper.deleteNegligible();
      log.info("[community-interest] decay done: updated={}, deleted={}", updated, deleted);
    } catch (Exception e) {
      log.error("[community-interest] decay failed: {}", e.getMessage(), e);
    }
  }

  /**
   * 日期键 + SETNX：同一天只会有一个实例执行。
   * Redis 不可用时放行（单实例部署下仍能正常衰减）。
   */
  private boolean acquireLock() {
    try {
      // 用 UTC 日期，避免多实例时区不一致导致同一天生成两个锁 key
      String lockKey = LOCK_PREFIX + LocalDate.now(ZoneOffset.UTC);
      Boolean locked = redisTemplate.opsForValue()
          .setIfAbsent(lockKey, "1", LOCK_TTL);
      if (Boolean.TRUE.equals(locked)) return true;
      log.info("[community-interest] decay skipped, already executed today");
      return false;
    } catch (Exception e) {
      log.warn("[community-interest] acquire lock failed, continue: {}", e.getMessage());
      return true;
    }
  }
}
