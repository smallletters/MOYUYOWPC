package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DmSessionRegistry 强制下线逻辑测试。
 */
class DmSessionRegistryTest {

    private final DmSessionRegistry registry = new DmSessionRegistry(new ObjectMapper(), new DmPushExecutor());

    @Test
    void closeUserSessions_shouldSendInvalidatedMessageThenCloseWith4001() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        registry.register(1L, session);

        registry.closeUserSessions(1L, "PASSWORD_CHANGED");

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getPayload().contains("session_invalidated"),
                "关闭前必须先下发 session_invalidated 消息，客户端据此停止重连");
        verify(session).close(argThat(status -> status.getCode() == 4001));
        assertEquals(0, registry.onlineCount(), "强制下线后注册表必须清空该用户连接");
    }

    @Test
    void closeUserSessions_shouldSkipAlreadyClosedSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        when(session.getId()).thenReturn("s2");
        registry.register(2L, session);

        registry.closeUserSessions(2L, "PASSWORD_RESET");

        verify(session, never()).close(argThat(status -> status.getCode() == 4001));
        assertEquals(0, registry.onlineCount());
    }
}
