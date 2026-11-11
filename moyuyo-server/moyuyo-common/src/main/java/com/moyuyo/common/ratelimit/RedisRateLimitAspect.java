package com.moyuyo.common.ratelimit;

import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link RedisRateLimit} 注解切面：在方法执行前向 Redis 申请配额，超限直接拒绝。
 */
@Slf4j
@Aspect
@Component
public class RedisRateLimitAspect {

  private final RedisRateLimiter redisRateLimiter;

  public RedisRateLimitAspect(RedisRateLimiter redisRateLimiter) {
    this.redisRateLimiter = redisRateLimiter;
  }

  @Around("@annotation(redisRateLimit)")
  public Object around(ProceedingJoinPoint joinPoint, RedisRateLimit redisRateLimit) throws Throwable {
    String name = resolveName(joinPoint, redisRateLimit);
    String dimension = resolveDimension(redisRateLimit.keyByUser());
    if (!redisRateLimiter.tryAcquire(name, dimension, redisRateLimit.limit(), redisRateLimit.windowSeconds())) {
      log.warn("[ratelimit] 触发限流, name={}, dimension={}", name, dimension);
      throw new RateLimitExceededException(redisRateLimit.message(), redisRateLimit.windowSeconds());
    }
    return joinPoint.proceed();
  }

  /** 未显式指定名称时用「类名#方法名」，保证不同接口默认各自独立计数 */
  private String resolveName(ProceedingJoinPoint joinPoint, RedisRateLimit redisRateLimit) {
    if (!redisRateLimit.name().isBlank()) {
      return redisRateLimit.name();
    }
    return joinPoint.getSignature().getDeclaringType().getSimpleName()
        + "#" + joinPoint.getSignature().getName();
  }

  /** 已登录按 userId 计数，未登录回退到可信代理解析出的客户端 IP */
  private String resolveDimension(boolean keyByUser) {
    if (keyByUser) {
      Long userId = UserContextHolder.getUserId();
      if (userId != null) {
        return "u:" + userId;
      }
    }
    return "ip:" + resolveClientIp();
  }

  private String resolveClientIp() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletAttributes) {
      return ClientIpResolver.resolve(servletAttributes.getRequest());
    }
    return "unknown";
  }
}
