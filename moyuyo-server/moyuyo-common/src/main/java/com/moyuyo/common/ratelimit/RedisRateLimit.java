package com.moyuyo.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式限流注解（Redis 滑动窗口，全集群共享计数）。
 * <p>
 * 与 Resilience4j 的 {@code @RateLimiter} 互补：
 * - Resilience4j 是进程内 Semaphore，多副本部署时实际阈值为 N × limit；
 * - 本注解基于 Redis，所有副本共享同一计数窗口，阈值严格生效。
 * <p>
 * 使用方式：标注在 Controller 方法上即可，超限抛 {@link RateLimitExceededException}，
 * 由 GlobalExceptionHandler 统一转成 429。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisRateLimit {

  /** 限流器名称，作为 Redis key 的一部分；为空时取「类名#方法名」 */
  String name() default "";

  /** 窗口内允许的最大请求数 */
  int limit() default 10;

  /** 窗口长度（秒） */
  int windowSeconds() default 60;

  /** 限流维度：true=按登录用户（未登录回退 IP）；false=始终按客户端 IP */
  boolean keyByUser() default true;

  /** 触发限流时返回给客户端的提示文案 */
  String message() default "请求过于频繁，请稍后再试";
}
