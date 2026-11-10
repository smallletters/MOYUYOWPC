package com.moyuyo.api.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 私信 WebSocket Handler
 *
 * 仅负责连接生命周期管理与心跳响应；业务推送统一走 DmSessionRegistry#sendToUser。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmWebSocketHandler extends TextWebSocketHandler {

    /** 心跳响应：客户端每 25s 发 {"type":"ping"}，服务端回 {"type":"pong"} */
    private static final String PONG_PAYLOAD = "{\"type\":\"pong\"}";

    private final DmSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(DmWebSocketAuthInterceptor.USER_ID_ATTR);
        if (userId == null) {
            // 理论上不会发生（握手拦截器已校验），兜底关闭
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
            return;
        }
        sessionRegistry.register(userId, session);
        log.debug("[dm-ws] connected userId={}, session={}, online={}",
                userId, session.getId(), sessionRegistry.onlineCount());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 只处理心跳；业务消息一律走 REST，避免 WebSocket 出现第二套写入路径
        if (payload != null && payload.contains("\"ping\"")) {
            Long userId = (Long) session.getAttributes().get(DmWebSocketAuthInterceptor.USER_ID_ATTR);
            // 走注册表异步回包：不在容器读线程上做网络写，且与业务推送复用同一个装饰 session
            sessionRegistry.replyPong(userId, session, PONG_PAYLOAD);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(DmWebSocketAuthInterceptor.USER_ID_ATTR);
        sessionRegistry.unregister(userId, session);
        log.debug("[dm-ws] closed userId={}, session={}, status={}",
                userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get(DmWebSocketAuthInterceptor.USER_ID_ATTR);
        sessionRegistry.unregister(userId, session);
        log.warn("[dm-ws] transport error userId={}, session={}", userId, session.getId(), exception);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 关闭失败无需处理
        }
    }
}
