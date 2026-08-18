package com.moyuyo.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 用户维度限流过滤器（保护已鉴权接口）
 * <p>
 * 设计要点：
 * 1. 滑动窗口（ZSet）：每个 userId 每分钟最多 N 次（默认 200），超出立即返回 429
 * 2. 与 IpRateLimitFilter 解耦：用户限流针对已鉴权流量，IP 限流针对未鉴权流量
 * 3. 仅对已登录请求生效：未鉴权（UserContextHolder.getUserId() == null）由 IpRateLimitFilter 处理
 * 4. Redis 不可用时 fail-open + ERROR 日志 + Prometheus 计数器
 * <p>
 * 配置项（application.yml）：
 * - moyuyo.user-ratelimit.enabled：总开关（默认 true，dev 可关闭）
 * - moyuyo.user-ratelimit.limit-per-minute：每分钟上限（默认 200）
 * <p>
 * 与 README 25、225 项承诺对齐：
 * - 用户维度限流独立开关：与 IP 限流开关解耦
 * - 限流异常归一化：fail-open 时升级日志为 ERROR，便于告警
 */
@Slf4j
public class UserRateLimitFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int limitPerMinute;
    private final Counter failOpenCounter;

    /** Redis ZSet Key 前缀：{prefix}:{userId}:{epochMinute} */
    private static final String REDIS_KEY_PREFIX = "auth:user:";

    public UserRateLimitFilter(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${moyuyo.user-ratelimit.enabled:true}") boolean enabled,
                               @Value("${moyuyo.user-ratelimit.limit-per-minute:200}") int limitPerMinute,
                               MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.failOpenCounter = Counter.builder("moyuyo_user_rate_limit_fail_open_total")
                .description("用户限流 fail-open 次数（Redis 不可用时累加）")
                .tag("reason", "redis_unavailable")
                .register(meterRegistry);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 跳过 OPTIONS 预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 未鉴权流量由 IpRateLimitFilter 处理
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        long minute = Instant.now().getEpochSecond() / 60;
        String key = REDIS_KEY_PREFIX + userId + ":" + minute;

        try {
            long currentCount = incrementAndCount(key);
            if (currentCount > limitPerMinute) {
                response.setHeader("Retry-After", "60");
                sendError(response, 429, "请求过于频繁，请稍后再试");
                return;
            }
            chain.doFilter(request, response);
        } catch (Exception e) {
            // Redis 不可用时 fail-open + ERROR 日志 + Prometheus 计数器
            failOpenCounter.increment();
            log.error("用户限流 fail-open（Redis 不可用）, userId={}, path={}", userId, request.getRequestURI(), e);
            chain.doFilter(request, response);
        }
    }

    /** 滑动窗口计数（与 IpRateLimitFilter pipeline 优化一致）：单次 RTT 完成清理/写入/过期/计数 */
    private long incrementAndCount(String key) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;

        String member = now + ":" + Thread.currentThread().getId() + ":" + System.nanoTime();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] memberBytes = member.getBytes(StandardCharsets.UTF_8);

        Long count = redisTemplate.execute((RedisCallback<Long>) (RedisConnection conn) -> {
            conn.openPipeline();
            try {
                conn.zSetCommands().zRemRangeByScore(keyBytes, 0, windowStart);
                conn.zSetCommands().zAdd(keyBytes, now, memberBytes);
                conn.keyCommands().expire(keyBytes, 70);
                return conn.zSetCommands().zCard(keyBytes);
            } finally {
                conn.closePipeline();
            }
        });
        return count != null ? count : 0L;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}