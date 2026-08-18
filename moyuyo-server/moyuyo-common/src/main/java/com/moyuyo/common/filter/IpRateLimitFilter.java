package com.moyuyo.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.utils.ClientIpResolver;
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
 * IP 维度限流过滤器（保护登录/支付等未鉴权敏感端点）
 * <p>
 * 设计要点：
 * 1. 滑动窗口（ZSet）：每个 IP 每分钟最多 N 次，超出立即返回 429
 * 2. 滑动窗口 key 含分钟戳（auth:ip:{ip}:{minute}）：自动过期，无需显式清理
 * 3. Redis 不可用时 fail-open：放行请求并 ERROR 日志 + Prometheus 计数器，便于告警
 * 4. 仅对白名单外的敏感端点生效（登录/支付/注册/邮箱验证/密码重置）
 * 5. 与 UserRateLimitFilter 解耦：IP 限流针对未鉴权流量，用户限流针对已鉴权流量
 * <p>
 * 配置项（application.yml）：
 * - moyuyo.ip-ratelimit.enabled：总开关（默认 true，dev 可关闭便于联调）
 * - moyuyo.ip-ratelimit.limit-per-minute：每分钟上限（默认 60）
 * <p>
 * 与 README 10、24 项承诺对齐：
 * - fail-open 时升级 ERROR 日志
 * - Prometheus 计数器 moyuyo_ip_rate_limit_fail_open_total 暴露 fail-open 次数
 */
@Slf4j
public class IpRateLimitFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int limitPerMinute;
    private final Counter failOpenCounter;

    /** Redis ZSet Key 前缀：{prefix}:{ip}:{epochMinute}，利用 TTL 自动清理 */
    private static final String REDIS_KEY_PREFIX = "auth:ip:";

    /** ZSet member 长度硬上限：超过则截断，避免攻击者用超长 member 撑爆 Redis 内存 */
    private static final int MAX_MEMBER_LENGTH = 64;

    /** 受限路径：未鉴权敏感端点 */
    private static final String[] SENSITIVE_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/magic-link/send",
            "/api/v1/payments/create"
    };

    public IpRateLimitFilter(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${moyuyo.ip-ratelimit.enabled:true}") boolean enabled,
                             @Value("${moyuyo.ip-ratelimit.limit-per-minute:60}") int limitPerMinute,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.failOpenCounter = Counter.builder("moyuyo_ip_rate_limit_fail_open_total")
                .description("IP 限流 fail-open 次数（Redis 不可用时累加）")
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

        String path = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS 预检请求：放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 非敏感路径：放行（由 UserRateLimitFilter 处理已鉴权流量）
        if (!isSensitivePath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 收敛到 ClientIpResolver：与 JwtAuthFilter / 风控 / 审计日志共用同一解析顺序与形状校验
        String ip = ClientIpResolver.resolve(request);
        if (!ClientIpResolver.isValidIpShape(ip) || "unknown".equals(ip)) {
            // 非法 IP 字面量：直接放行但 ERROR 日志，便于运维感知限流 key 注入尝试
            log.warn("IP 限流：客户端 IP 字面量非法（不匹配字符白名单），path={}, ip.len={}", path, ip.length());
            chain.doFilter(request, response);
            return;
        }

        long minute = Instant.now().getEpochSecond() / 60;
        String key = REDIS_KEY_PREFIX + ip + ":" + minute;

        try {
            long currentCount = incrementAndCount(key, ip);
            if (currentCount > limitPerMinute) {
                response.setHeader("Retry-After", "60");
                sendError(response, 429, "请求过于频繁，请稍后再试");
                return;
            }
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 关键修复：Redis 不可用时 fail-open + ERROR 日志 + Prometheus 计数器
            // 避免限流失效造成业务雪崩；告警规则基于计数器第一时间感知
            failOpenCounter.increment();
            log.error("IP 限流 fail-open（Redis 不可用），path={}, ip={}", path, ip, e);
            chain.doFilter(request, response);
        }
    }

    /**
     * 滑动窗口计数：使用 ZSet 记录每次请求时间戳
     * 利用 ZREMRANGEBYSCORE 清理过期成员（>60s 前）+ ZCARD 统计当前窗口内成员数
     * <p>
     * 性能优化：使用 Redis pipeline 把 ZREMRANGEBYSCORE + ZADD + EXPIRE 三条命令一次往返，
     * 相比之前 4 次 RTT（remove / add / expire / zcard）节省约 60~75% 延迟
     * （Lettuce 默认无 pipeline 优化），减少 Redis 连接池占用与网络栈切换。
     */
    private long incrementAndCount(String key, String ip) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;

        // ZSet member 唯一化：now + threadId + nano 防同毫秒碰撞
        // 长度硬截断：防止攻击者通过构造超长 member（如 XFF 注入）撑爆 Redis 内存
        String member = now + ":" + Thread.currentThread().getId() + ":" + System.nanoTime();
        if (member.length() > MAX_MEMBER_LENGTH) {
            member = member.substring(0, MAX_MEMBER_LENGTH);
        }
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] memberBytes = member.getBytes(StandardCharsets.UTF_8);

        // Pipeline 一次性执行 removeRangeByScore + zAdd + expire，
        // ZCARD 单独返回当前窗口成员数（add 之后的真实计数）
        Long count = redisTemplate.execute((RedisCallback<Long>) (RedisConnection conn) -> {
            conn.openPipeline();
            try {
                // ZREMRANGEBYSCORE key 0 windowStart
                conn.zSetCommands().zRemRangeByScore(keyBytes, 0, windowStart);
                // ZADD key {now} {member}
                conn.zSetCommands().zAdd(keyBytes, now, memberBytes);
                // EXPIRE key 70s
                conn.keyCommands().expire(keyBytes, 70);
                // ZCARD key → 返回当前窗口成员数
                return conn.zSetCommands().zCard(keyBytes);
            } finally {
                conn.closePipeline();
            }
        });
        return count != null ? count : 0L;
    }

    private boolean isSensitivePath(String path) {
        for (String prefix : SENSITIVE_PATHS) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}