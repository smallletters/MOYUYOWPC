package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

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
 *
 * 并发约束：
 * 1. 注册表里存的是 {@link ConcurrentWebSocketSessionDecorator} 包装后的 session，
 *    保证同一连接被多线程写时串行化，并在超过发送时限时主动断开慢客户端；
 * 2. 推送一律投递到 {@link DmPushExecutor} 异步执行，调用方（HTTP 业务线程）不做网络写。
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

    /** 单条消息发送时限：超过即判定客户端不可写，断开该连接，避免长时间占用推送线程 */
    private static final int SEND_TIME_LIMIT_MS = 5000;
    /** 单连接发送缓冲上限：慢客户端积压超过 512KB 直接断开 */
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private static final CloseStatus SESSION_INVALIDATED_CLOSE_STATUS =
            new CloseStatus(SESSION_INVALIDATED_CLOSE_CODE, "token invalidated");

    private final ObjectMapper objectMapper;
    private final DmPushExecutor pushExecutor;

    /** userId → 该用户当前在线连接集合（元素为装饰后的 session） */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 新连接建立后注册。
     * 包装为并发安全的 session：{@code WebSocketSession} 本身不保证线程安全，
     * 推送线程与心跳回包线程可能同时写同一连接。
     */
    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(safeSession);
    }

    /** 连接关闭/异常后移除；集合为空时原子清理 key，避免 userId 无限增长 */
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        // 注册表存的是装饰后的 session，而关闭/异常回调拿到的是原始 session，
        // 对象不是同一个实例 → 必须按 session id 匹配删除，否则永远删不掉造成泄漏
        set.removeIf(s -> s == session || s.getId().equals(session.getId()));
        // computeIfPresent 原子判断：并发注册的新连接不会被误删
        sessions.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
    }

    /**
     * 向指定用户的全部在线连接推送 JSON 消息。
     * <p>
     * 序列化在调用线程完成（CPU 开销小），网络发送投递到有界线程池异步执行，
     * 避免慢客户端阻塞 HTTP 业务线程或事务提交回调。
     */
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
        pushExecutor.execute(() -> doSendToUser(userId, json));
    }

    /**
     * 心跳回包：与业务推送共用异步通道。
     * <p>
     * 必须使用注册表里装饰后的 session 发送，否则会和推送线程并发写同一底层连接。
     */
    public void replyPong(Long userId, WebSocketSession rawSession, String pongJson) {
        if (rawSession == null || pongJson == null) return;
        String sessionId = rawSession.getId();
        pushExecutor.execute(() -> {
            WebSocketSession target = findBySessionId(userId, sessionId);
            if (target == null || !target.isOpen()) return;
            try {
                target.sendMessage(new TextMessage(pongJson));
            } catch (Exception e) {
                log.warn("[dm-ws] pong failed, userId={}, session={}", userId, sessionId, e);
                unregister(userId, rawSession);
            }
        });
    }

    /** 实际发送（在推送线程池里执行） */
    private void doSendToUser(Long userId, String json) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return;
        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.warn("[dm-ws] send failed, userId={}, session={}", userId, session.getId(), e);
                unregister(userId, session);
            }
        }
    }

    /** 按 session id 找到注册表中装饰后的 session */
    private WebSocketSession findBySessionId(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return null;
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return null;
        for (WebSocketSession s : set) {
            if (sessionId.equals(s.getId())) return s;
        }
        return null;
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
                // 装饰后的 session 已保证串行发送 + 超时保护，无需额外加锁
                if (message != null) {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (Exception e) {
                        // 通知发送失败（如慢客户端触发发送时限）也必须继续关闭连接，
                        // 否则旧 token 的会话会一直挂着
                        log.warn("[dm-ws] 下发失效通知失败，仍强制关闭, userId={}, session={}",
                                userId, session.getId(), e);
                    }
                }
                session.close(SESSION_INVALIDATED_CLOSE_STATUS);
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
