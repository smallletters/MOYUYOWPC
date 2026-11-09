package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * token 失效事件跨实例广播（Redis Pub/Sub）。
 * <p>
 * 单机部署时由 Spring 事件直接关闭本机连接；多实例部署时，发布方在本机关闭连接后
 * 再把事件广播到 Redis，其他实例订阅后关闭各自持有的连接。
 * <p>
 * 注意：广播内容只包含 userId / reason / source，不包含任何凭证；
 * source 为实例唯一标识，用于避免发布方重复处理自己发出的消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInvalidationBroadcaster {

    /** Redis Pub/Sub 频道：所有实例订阅，收到后关闭本机该用户的 WebSocket 会话 */
    public static final String CHANNEL = "auth:token-invalidated";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DmSessionRegistry sessionRegistry;

    /** 当前实例唯一标识：避免发布实例重复处理自己发出的广播 */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * 广播 token 失效事件（由 DmWebSocketEventListener 在事务提交后调用）。
     * <p>
     * 广播失败只记录日志：本实例的连接已由 Spring 事件关闭；其他实例可能漏关，
     * 但其上的 HTTP 请求也会因 token 版本号不一致被拒绝，用户重新登录后连接自然重建。
     */
    public void publish(Long userId, String reason) {
        if (userId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            payload.put("reason", reason == null ? "" : reason);
            payload.put("source", instanceId);
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("[dm-ws] token 失效广播失败（仅本实例断开连接），userId={}", userId, e);
        }
    }

    /**
     * 处理 Redis 广播消息：关闭本实例上该用户的全部连接。
     * <p>
     * 忽略自己发出的消息（source == 本实例），避免与 Spring 事件重复关闭。
     */
    public void handleMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message);
            long userId = node.path("userId").asLong(-1L);
            if (userId <= 0) {
                return;
            }
            String source = node.path("source").asText("");
            if (instanceId.equals(source)) {
                return;
            }
            String reason = node.path("reason").asText("");
            sessionRegistry.closeUserSessions(userId, reason);
        } catch (Exception e) {
            // 非法/脏消息直接忽略，不能让它导致监听线程退出
            log.warn("[dm-ws] token 失效广播解析失败，忽略该消息: {}", e.getMessage());
        }
    }

    /** 仅供测试/排查：当前实例标识 */
    String getInstanceId() {
        return instanceId;
    }
}
