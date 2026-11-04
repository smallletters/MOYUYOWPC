package com.moyuyo.api.ws;

import com.moyuyo.common.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 私信 WebSocket 握手鉴权
 *
 * 浏览器/APP 的 WebSocket 无法自定义 Authorization 头，因此 token 通过
 * 查询参数传递：/ws/dm?token=xxx。校验 JWT 签名、过期时间与 Redis 黑名单，
 * 校验通过后把 userId 写入 session attributes 供 Handler 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmWebSocketAuthInterceptor implements HandshakeInterceptor {

    /** session attributes 中保存当前用户 ID 的 key */
    public static final String USER_ID_ATTR = "dmUserId";

    /** 与 JwtAuthFilter 保持一致的 token 黑名单前缀 */
    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.debug("[dm-ws] handshake rejected: missing token");
            return false;
        }
        try {
            Claims claims = jwtUtil.parseClaims(token);
            // 登出/踢下线后的 token 不允许建立连接；Redis 不可用时 fail-closed
            if (isBlacklisted(token)) {
                log.debug("[dm-ws] handshake rejected: token revoked");
                return false;
            }
            Long userId = jwtUtil.getUserIdFromClaims(claims);
            attributes.put(USER_ID_ATTR, userId);
            return true;
        } catch (Exception e) {
            log.debug("[dm-ws] handshake rejected: invalid token, {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 无需额外处理
    }

    /** 从 query 中读取 token（兼容 URL 编码） */
    private String extractToken(ServerHttpRequest request) {
        String raw = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (raw == null || raw.isBlank()) return null;
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    /** 查询 Redis 黑名单；Redis 异常时拒绝连接（与 JwtAuthFilter 的 fail-closed 语义一致） */
    private boolean isBlacklisted(String token) {
        try {
            return redisTemplate.opsForValue().get(REDIS_KEY_BLACKLIST + token) != null;
        } catch (Exception e) {
            log.warn("[dm-ws] redis blacklist check failed, reject handshake", e);
            return true;
        }
    }
}
