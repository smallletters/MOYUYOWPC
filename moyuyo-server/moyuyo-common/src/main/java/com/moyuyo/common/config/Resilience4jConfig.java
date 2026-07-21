package com.moyuyo.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 限流熔断重试配置
 * <p>
 * 提供默认配置实例，具体的接口级限流规则在 application.yml 中通过
 * resilience4j.ratelimiter.instances / resilience4j.circuitbreaker.instances 配置
 */
@Configuration
public class Resilience4jConfig {

    /**
     * 全局 RateLimiter 默认配置
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(600)
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(defaultConfig);
    }

    /**
     * 全局 CircuitBreaker 默认配置
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    /**
     * 全局 Retry 默认配置
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build();

        return RetryRegistry.of(defaultConfig);
    }
}
