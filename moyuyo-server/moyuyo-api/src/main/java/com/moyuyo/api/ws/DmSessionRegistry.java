package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 自定义关闭码：token 被强制失效（4000-4999 为应用自定义区间）。
     * 客户端收到该关闭码后必须停止自动重连，引导用户重新登录。
     */
    public static final int SESSION_INVALIDATED_CLOSE_CODE = 4001;

    private static final CloseStatus SESSION_INVALIDATED_CLOSE_STATUS =
            new CloseStatus(SESSION_INVALIDATED_CLOSE_CODE, "token invalidated");

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

    /**
     * 强制关闭指定用户的全部在线连接（改密/重置密码后旧 token 失效）。
     * <p>
     * 先下发 {@code session_invalidated} 消息，再以自定义关闭码 4001 关闭；
     * 客户端据此停止自动重连，避免用已失效 token 反复握手。
     * <p>
     * 注意：当前为单机内存注册表，多实例部署时只能关闭当前实例上的连接；
     * 若后续改为多实例，需要配合 Redis Pub/Sub 广播该失效事件。
     */
    public void closeUserSessions(Long userId, String reason) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        // 先拷贝快照：关闭过程中 afterConnectionClosed 会并发 unregister 修改原集合
        List<WebSocketSession> snapshot = new ArrayList<>(set);

        String message = null;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", reason == null ? "" : reason);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "session_invalidated");
            payload.put("data", data);
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[dm-ws] 序列化失效通知失败，直接关闭连接，userId={}", userId, e);
        }

        int closed = 0;
        for (WebSocketSession session : snapshot) {
            if (session == null) {
                continue;
            }
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            try {
                synchronized (session) {
                    if (message != null) {
                        session.sendMessage(new TextMessage(message));
                    }
                    session.close(SESSION_INVALIDATED_CLOSE_STATUS);
                }
                closed++;
            } catch (Exception e) {
                log.warn("[dm-ws] 强制关闭连接失败, userId={}, session={}", userId, session.getId(), e);
            } finally {
                unregister(userId, session);
            }
        }
        log.info("[dm-ws] token 失效强制下线: userId={}, total={}, closed={}, reason={}",
                userId, snapshot.size(), closed, reason);
    }

    /** 当前在线连接数（仅用于日志/排查） */
    public int onlineCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }
}
