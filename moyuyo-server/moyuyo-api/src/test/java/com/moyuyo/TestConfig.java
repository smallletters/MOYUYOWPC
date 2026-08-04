package com.moyuyo;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@TestConfiguration
public class TestConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 测试环境限流器注册表：设置极高阈值，确保集成测试不会触发限流降级。
     * 限流降级方法返回 Result.error(429, ...) 时 HTTP 状态码仍为 200，
     * 会干扰测试中基于 HTTP 状态码的断言。
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1_000_000)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(60))
                .build();
        return RateLimiterRegistry.of(config);
    }
}
