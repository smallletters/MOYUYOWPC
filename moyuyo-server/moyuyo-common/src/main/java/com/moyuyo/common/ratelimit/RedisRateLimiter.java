package com.moyuyo.common.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用 Redis 滑动窗口限流器。
 * <p>
 * 用 ZSet 存窗口内每次请求的时间戳，Lua 脚本原子完成「清理过期成员 + 计数 + 写入本次请求」，
 * 多副本共享同一计数，阈值严格生效（区别于 Resilience4j 的进程内 Semaphore）。
 * <p>
 * 先判断再写入：超限请求不写入 ZSet，窗口内成员数上限恒等于 limit，
 * 避免攻击者高频打接口把 ZSet 撑爆（每成员约 80 字节，10k QPS × 60s 可达数十 MB）。
 * <p>
 * 容错策略：Redis 不可用时 fail-open 放行，并累加 Prometheus 计数器 + ERROR 日志，
 * 避免缓存故障直接导致下单全链路不可用；网关层仍有 IP/用户维度兜底限流。
 */
@Slf4j
@Component
public class RedisRateLimiter {

  /**
   * 滑动窗口 Lua：
   * KEYS[1]=限流 key；ARGV[1]=窗口起始毫秒；ARGV[2]=当前毫秒；ARGV[3]=本次请求成员；
   * ARGV[4]=窗口内配额；ARGV[5]=key 过期毫秒。
   * 返回值 >=0 表示放行并返回窗口内当前请求数；-1 表示已超限。
   */
  private static final RedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
      local key = KEYS[1]
      redis.call('ZREMRANGEBYSCORE', key, 0, ARGV[1])
      local count = redis.call('ZCARD', key)
      if count >= tonumber(ARGV[4]) then
        return -1
      end
      redis.call('ZADD', key, ARGV[2], ARGV[3])
      redis.call('PEXPIRE', key, ARGV[5])
      return count + 1
      """, Long.class);

  /** Redis key 前缀，与 IP/用户维度限流过滤器区分开，避免互相污染 */
  private static final String KEY_PREFIX = "ratelimit:";

  private final StringRedisTemplate redisTemplate;
  private final Counter failOpenCounter;

  public RedisRateLimiter(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.failOpenCounter = Counter.builder("moyuyo_rate_limit_fail_open_total")
        .description("分布式限流 fail-open 次数（Redis 不可用时累加）")
        .tag("reason", "redis_unavailable")
        .register(meterRegistry);
  }

  /**
   * 尝试获取一次配额。
   *
   * @param name          限流器名称
   * @param dimension     限流维度值（如 u:123 / ip:1.2.3.4）
   * @param limit         窗口内允许的最大请求数
   * @param windowSeconds 窗口长度（秒）
   * @return true=放行，false=触发限流
   */
  public boolean tryAcquire(String name, String dimension, int limit, int windowSeconds) {
    if (limit <= 0 || windowSeconds <= 0) {
      return true;
    }
    String key = KEY_PREFIX + name + ":" + dimension;
    long now = System.currentTimeMillis();
    long windowStart = now - windowSeconds * 1000L;
    // 成员需唯一：同一毫秒内同线程并发请求的实际碰撞概率由 nanoTime 收敛到可忽略
    String member = now + ":" + Thread.currentThread().getId() + ":" + System.nanoTime();
    try {
      Long result = redisTemplate.execute(SLIDING_WINDOW_SCRIPT,
          List.of(key),
          String.valueOf(windowStart),
          String.valueOf(now),
          member,
          String.valueOf(limit),
          String.valueOf((windowSeconds + 10) * 1000L));
      if (result == null) {
        // 脚本结果为 null 说明连接/序列化异常，按失败处理
        failOpenCounter.increment();
        log.error("[ratelimit] Redis 返回空结果，本次请求放行, key={}", key);
        return true;
      }
      return result >= 0;
    } catch (Exception e) {
      // Redis 不可用：fail-open，避免限流组件把下单链路整体拖垮
      failOpenCounter.increment();
      log.error("[ratelimit] Redis 计数失败，本次请求放行, key={}", key, e);
      return true;
    }
  }
}
