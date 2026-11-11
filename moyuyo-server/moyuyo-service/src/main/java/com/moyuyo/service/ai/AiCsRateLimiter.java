package com.moyuyo.service.ai;

import com.moyuyo.service.config.AiCsConfig;
import com.moyuyo.service.config.AiCsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * AI 客服按用户限流
 * <p>
 * 目标：防止单个账号高频提问把第三方 token 配额刷穿（一次对话往往上千 token）。
 * <p>
 * 实现：Redis 固定窗口计数，key 为 {@code aic:rate:{userId}:{epochMinute}}，
 * INCR 后首次设置 70s 过期，窗口随分钟自然切换，无需清理任务。
 * 相比滑动窗口（ZSet）少两次往返，成本保护场景对边界精度要求不高。
 * <p>
 * 故障策略：Redis 不可用时 fail-open（放行），避免限流组件故障导致客服整体不可用，
 * 与 {@code IpRateLimitFilter} 的处理一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCsRateLimiter {

  private static final String KEY_PREFIX = "aic:rate:";

  /** 过期时间略大于窗口，避免临界分钟 key 提前消失导致计数从 0 重新开始 */
  private static final Duration KEY_TTL = Duration.ofSeconds(70);

  private final StringRedisTemplate redisTemplate;
  private final AiCsConfigService aiCsConfigService;

  /**
   * 尝试占用一次配额
   *
   * @param userId 用户 ID
   * @return true 放行；false 超出本分钟额度
   */
  public boolean tryAcquire(Long userId) {
    AiCsConfig config = aiCsConfigService.current();
    if (!config.rateLimitEnabled()) return true;
    if (userId == null) return true;

    long minute = Instant.now().getEpochSecond() / 60;
    String key = KEY_PREFIX + userId + ":" + minute;
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count != null) {
        try {
          // 每次续期（而不是只在首次设置）：某次 expire 失败时，后续请求能自动补上 TTL
          redisTemplate.expire(key, KEY_TTL);
        } catch (Exception e) {
          // 续期失败就把计数删掉，避免留下没有 TTL、永久驻留的 key；本次按 fail-open 放行
          redisTemplate.delete(key);
        }
      }
      return count == null || count.longValue() <= Math.max(1, config.rateLimitPerMinute());
    } catch (Exception e) {
      log.warn("AI 客服限流 fail-open（Redis 不可用），userId={}", userId, e);
      return true;
    }
  }
}
