package com.moyuyo.api.ws;

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
 * 浏览器/APP 的 WebSocket 无法自定义 Authorization 头。为不把长期有效的 access token
 * 暴露在 URL（会被 Nginx/代理访问日志记录），客户端先通过 REST 换取一次性 ticket：
 * /ws/dm?ticket=xxx。ticket 60 秒有效、使用一次即从 Redis 删除，校验通过后把 userId
 * 写入 session attributes 供 Handler 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmWebSocketAuthInterceptor implements HandshakeInterceptor {

    /** session attributes 中保存当前用户 ID 的 key */
    public static final String USER_ID_ATTR = "dmUserId";

    /** 与 AuthServiceImpl 保持一致的 WS ticket key 前缀 */
    private static final String REDIS_KEY_WS_TICKET = "auth:ws-ticket:";
    /** 与 JwtAuthFilter 一致的用户封禁标记前缀 */
    private static final String REDIS_KEY_USER_BAN = "auth:user:ban:";

    /** ticket 形状：UUID 去掉横线后的 32 位小写十六进制 */
    private static final String TICKET_PATTERN = "[a-f0-9]{32}";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String ticket = extractTicket(request);
        if (ticket == null || !ticket.matches(TICKET_PATTERN)) {
            log.debug("[dm-ws] handshake rejected: missing or invalid ticket");
            return false;
        }
        try {
            // 原子读取并删除：ticket 只能使用一次，重放无效
            String stored = redisTemplate.opsForValue().getAndDelete(REDIS_KEY_WS_TICKET + ticket);
            if (stored == null || stored.isBlank()) {
                log.debug("[dm-ws] handshake rejected: ticket expired or already used");
                return false;
            }
            Long userId = Long.parseLong(stored.trim());
            // 封禁拦截与 JwtAuthFilter 保持一致
            if (redisTemplate.opsForValue().get(REDIS_KEY_USER_BAN + userId) != null) {
                log.debug("[dm-ws] handshake rejected: user banned, userId={}", userId);
                return false;
            }
            attributes.put(USER_ID_ATTR, userId);
            return true;
        } catch (Exception e) {
            // Redis 异常或数据非法：拒绝握手（fail-closed）
            log.warn("[dm-ws] handshake ticket check failed, reject handshake", e);
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

    /** 从 query 中读取 ticket（兼容 URL 编码） */
    private String extractTicket(ServerHttpRequest request) {
        String raw = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        if (raw == null || raw.isBlank()) return null;
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}
