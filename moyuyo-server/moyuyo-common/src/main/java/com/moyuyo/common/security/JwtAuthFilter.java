package com.moyuyo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
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
                    if (!jwtUtil.validate(token)) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    String blacklisted = redisTemplate.opsForValue().get(REDIS_KEY_BLACKLIST + token);
                    if (blacklisted != null) {
                        sendUnauthorized(response, "Token has been revoked");
                        return;
                    }
                    String adminRole = jwtUtil.getRole(token);
                    if (adminRole == null || adminRole.isBlank()) {
                        sendForbidden(response, "Admin role required");
                        return;
                    }
                    // admin 路径校验通过，缓存到 ThreadLocal，后续 admin permission filter 复用
                    UserContextHolder.setUserId(jwtUtil.getUserId(token));
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
            if (!jwtUtil.validate(token)) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }

            // 检查 Token 是否在黑名单中（已登出）
            String blacklisted = redisTemplate.opsForValue().get(REDIS_KEY_BLACKLIST + token);
            if (blacklisted != null) {
                sendUnauthorized(response, "Token has been revoked");
                return;
            }

            // C 端接口：仅设置 userId，不强制角色要求
            // 管理端接口已在兜底块中强制校验角色并放行，不会走到这里
            Long userId = jwtUtil.getUserId(token);
            UserContextHolder.setUserId(userId);
            UserContextHolder.setToken(token);
            // C 端 token 不携带 role；如携带（非管理员）则原样保留，由业务层判断
            String cRole = jwtUtil.getRole(token);
            UserContextHolder.setRole(cRole);

            chain.doFilter(request, response);

        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isWhiteListed(String path, String method) {
        for (WhiteListEntry entry : WHITE_LIST) {
            // 前缀型白名单仅放行 GET 请求，防止 POST/PUT/DELETE 写入绕过鉴权
            if (entry.prefix && !"GET".equalsIgnoreCase(method)) {
                continue;
            }
            if (path.equals(entry.path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 白名单条目：path + 是否前缀匹配
     */
    private record WhiteListEntry(String path, boolean prefix) {
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        Result<Void> result = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), result);
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        Result<Void> result = Result.error(403, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
