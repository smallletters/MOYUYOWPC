package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信 WebSocket 在线会话注册表
 *
 * 当前为单机内存实现：userId → 多个 WebSocketSession（支持同一用户多端在线）。
 * 多实例部署时需替换为 Redis Pub/Sub 广播，否则只有连到同一实例的接收方能收到推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmSessionRegistry {

    private final ObjectMapper objectMapper;

    /** userId → 该用户当前在线连接集合 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** 新连接建立后注册 */
    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** 连接关闭/异常后移除；集合为空时原子清理 key，避免 userId 无限增长 */
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        set.remove(session);
        // computeIfPresent 原子判断：并发注册的新连接不会被误删
        sessions.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
    }

    /** 向指定用户的全部在线连接推送 JSON 消息 */
    public void sendToUser(Long userId, Object payload) {
        if (userId == null || payload == null) return;
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[dm-ws] serialize payload failed, userId={}", userId, e);
            return;
        }

        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            try {
                // WebSocketSession 不保证线程安全，同一连接串行发送
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.warn("[dm-ws] send failed, userId={}, session={}", userId, session.getId(), e);
                unregister(userId, session);
            }
        }
    }

    /** 当前在线连接数（仅用于日志/排查） */
    public int onlineCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }
}
