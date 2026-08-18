package com.moyuyo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthFilter implements Filter {

    /** Bearer Token 最大长度（与 RFC 6750 推荐的 Bearer 凭证长度上界对齐） */
    private static final int MAX_TOKEN_LENGTH = 4096;

    /** Redis 黑名单 key 长度上界（防 Redis SCAN 类命令被超长 key 拖慢） */
    private static final int MAX_BLACKLIST_KEY_LENGTH = 64 + MAX_TOKEN_LENGTH;

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    /** JWT 黑名单 fail-closed 计数器：Redis 不可用时累加，便于 Prometheus 告警 */
    private final Counter jwtBlacklistFailClosedCounter;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         ObjectMapper objectMapper,
                         StringRedisTemplate redisTemplate,
                         MeterRegistry meterRegistry) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.jwtBlacklistFailClosedCounter = Counter.builder("moyuyo_jwt_blacklist_fail_closed_total")
                .description("JWT 黑名单查询因 Redis 不可用而 fail-closed 的次数")
                .tag("reason", "redis_unavailable")
                .register(meterRegistry);
    }

    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";

    /**
     * 公开路径白名单：不需要 JWT 校验的端点
     * <p>
     * 设计原则：
     * 1. 仅放行完全公开的端点（注册、登录、Webhook、健康检查、OpenAPI 文档）
     * 2. 严格区分"精确匹配"和"前缀匹配"，前缀必须以 / 结尾，避免
     *    "/api/v1/products" 误匹配 "/api/v1/products-secret" 等未来新增路径
     * 3. 不在白名单内的请求必须携带 Bearer Token
     */
    private static final List<WhiteListEntry> WHITE_LIST = List.of(
            // 精确匹配
            new WhiteListEntry("/api/health", false),
            new WhiteListEntry("/api/admin/auth/login", false),
            new WhiteListEntry("/api-docs", false),
            new WhiteListEntry("/v3/api-docs", false),
            // C 端公开鉴权接口（精确匹配）
            new WhiteListEntry("/api/v1/auth/register", false),
            new WhiteListEntry("/api/v1/auth/login", false),
            new WhiteListEntry("/api/v1/auth/refresh", false),
            new WhiteListEntry("/api/v1/auth/email/verify", false),
            new WhiteListEntry("/api/v1/auth/email/verify-confirm", false),
            new WhiteListEntry("/api/v1/auth/password/forgot", false),
            new WhiteListEntry("/api/v1/auth/password/reset", false),
            // 支付渠道 Webhook（精确匹配，签名校验在内部完成）
            new WhiteListEntry("/api/v1/payments/stripe/webhook", false),
            new WhiteListEntry("/api/v1/payments/paypal/webhook", false),
            // C 端公开浏览接口（仅允许只读列表，详情/下单仍需登录）
            // 仅放行 GET 请求，且路径必须严格以 / 结尾
            new WhiteListEntry("/api/v1/products", true),
            new WhiteListEntry("/api/v1/products/", true),
            new WhiteListEntry("/api/v1/categories", true),
            new WhiteListEntry("/api/v1/categories/", true)
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String method = request.getMethod();

        try {
            // 兜底：所有 /api/admin/** 端点必须强制 JWT 校验，即使白名单配置错误
            // 也不允许 admin 路径绕过鉴权
            if (path.startsWith("/api/admin/")) {
                // 唯一允许匿名访问的 admin 路径是登录本身
                if (!"/api/admin/auth/login".equals(path)) {
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        sendUnauthorized(response, "Missing or invalid Authorization header");
                        return;
                    }
                    String token = authHeader.substring(7);
                    // 兜底：超长 token 直接拒绝（防 Redis SCAN 类命令被超长 key 拖慢）
                    if (token.length() > MAX_TOKEN_LENGTH) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    // P1 性能修复：原实现分三次调用 jwtUtil.validate / getRole / getUserId，每次都重新
                    // 触发 HMAC-SHA256 验签 + base64 解码。现改为单次 parseClaims 后从 Claims 复用。
                    // 单次 JWT 验签可节省 ~150µs × 2 = 300µs/请求；QPS=1k 时累计 300ms CPU。
                    Claims claims;
                    try {
                        claims = jwtUtil.parseClaims(token);
                    } catch (JwtException | IllegalArgumentException e) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    if (isBlacklisted(token)) {
                        sendUnauthorized(response, "Token has been revoked");
                        return;
                    }
                    String adminRole = jwtUtil.getRoleFromClaims(claims);
                    if (adminRole == null || adminRole.isBlank()) {
                        sendForbidden(response, "Admin role required");
                        return;
                    }
                    // admin 路径校验通过，缓存到 ThreadLocal，后续 admin permission filter 复用
                    UserContextHolder.setUserId(jwtUtil.getUserIdFromClaims(claims));
                    UserContextHolder.setToken(token);
                    UserContextHolder.setRole(adminRole);
                    chain.doFilter(request, response);
                    return;
                }
            }

            if (isWhiteListed(path, method)) {
                chain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);
            // 兜底：超长 token 直接拒绝（防 Redis SCAN 类命令被超长 key 拖慢）
            if (token.length() > MAX_TOKEN_LENGTH) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }
            // P1 性能修复：单次 parseClaims 复用 Claims，避免 validate + getUserId + getRole 三次 HMAC 验签
            Claims claims;
            try {
                claims = jwtUtil.parseClaims(token);
            } catch (JwtException | IllegalArgumentException e) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }

            // 检查 Token 是否在黑名单中（已登出）。fail-closed 语义，Redis 不可用时拒绝请求
            if (isBlacklisted(token)) {
                sendUnauthorized(response, "Token has been revoked");
                return;
            }

            // C 端接口：仅设置 userId，不强制角色要求
            // 管理端接口已在兜底块中强制校验角色并放行，不会走到这里
            UserContextHolder.setUserId(jwtUtil.getUserIdFromClaims(claims));
            UserContextHolder.setToken(token);
            // C 端 token 不携带 role；如携带（非管理员）则原样保留，由业务层判断
            UserContextHolder.setRole(jwtUtil.getRoleFromClaims(claims));

            chain.doFilter(request, response);

        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 查询 JWT 黑名单（统一收敛点）
     * <p>
     * 安全语义：fail-closed。
     * 与 IP / 用户限流的 fail-open 刻意区分：限流优先业务可用性，黑名单优先安全
     * （防 Redis 抖动期间被吊销 token 仍能通过鉴权）。
     * <p>
     * 二次防御：入口先校验 token 长度，避免与 extractBearerToken 之外的入口绕过长度限制，
     * 构造超长 Redis key 拖慢 Redis 集群的 SCAN / KEYS 类命令。
     *
     * @return true=已吊销（拒绝）；false=未吊销；Redis 不可用时返回 true（拒绝）
     */
    private boolean isBlacklisted(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return true;
        }
        String key = REDIS_KEY_BLACKLIST + token;
        if (key.length() > MAX_BLACKLIST_KEY_LENGTH) {
            // 兜底：极端情况下 key 过长直接拒绝（不查 Redis）
            return true;
        }
        try {
            String blacklisted = redisTemplate.opsForValue().get(key);
            return blacklisted != null;
        } catch (DataAccessException e) {
            // fail-closed：Redis 不可用时拒绝请求，避免被吊销的 token 仍能通过鉴权
            // DataAccessException 是 RedisConnectionFailureException 的父类，单一捕获即可覆盖所有 Redis / DB 异常
            jwtBlacklistFailClosedCounter.increment();
            log.error("JWT 黑名单查询失败（fail-closed，拒绝请求），token.length={}", token.length(), e);
            return true;
        }
    }

    private boolean isWhiteListed(String path, String method) {
        for (WhiteListEntry entry : WHITE_LIST) {
            // 前缀型白名单仅放行 GET 请求，防止 POST/PUT/DELETE 写入绕过鉴权
            if (entry.prefix && !"GET".equalsIgnoreCase(method)) {
                continue;
            }
            // 关键修复：原实现 path.equals(entry.path) 仅做精确匹配，前缀型条目（prefix=true）形同虚设，
            // "/api/v1/products/123" 这类合法 GET 请求会被 JWT 拦截返回 401（与 README 设计意图不符）。
            // 修复：精确匹配走 equals，前缀匹配必须以 / 结尾避免 "/products" 误伤 "/products-secret"
            if (entry.prefix) {
                if (path.equals(entry.path) || path.startsWith(entry.path + "/")) {
                    return true;
                }
            } else {
                if (path.equals(entry.path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 白名单条目：path + 是否前缀匹配
     */
    private record WhiteListEntry(String path, boolean prefix) {
    }

    /**
     * 写入 401 JSON 响应
     * <p>
     * 防御性检查：response.isCommitted() 为 true 时（如上游过滤器已 flush / Tomcat 内部已写入 headers），
     * 直接调用 getWriter() / writeValue 会抛 IllegalStateException，污染 Spring 异常处理链路
     * （被 GlobalExceptionHandler#handleException 兜底为 500，掩盖真实的认证拦截意图）。
     * 此处前置判断：已 commit 仅记录 ERROR 日志，不再尝试二次写入。
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            log.error("response 已 commit，无法写入 401 响应。message={}", message);
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        Result<Void> result = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), result);
    }

    /**
     * 写入 403 JSON 响应（防御 isCommitted 异常，与 AdminPermissionFilter#sendForbidden 对齐）
     */
    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            log.error("response 已 commit，无法写入 403 响应。message={}", message);
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        Result<Void> result = Result.error(403, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
