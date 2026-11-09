package com.moyuyo.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TokenInvalidationBroadcaster 单元测试：验证发布格式、远端处理与本实例去重。
 */
class TokenInvalidationBroadcasterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StringRedisTemplate redisTemplate;
    private DmSessionRegistry sessionRegistry;
    private TokenInvalidationBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        sessionRegistry = mock(DmSessionRegistry.class);
        broadcaster = new TokenInvalidationBroadcaster(redisTemplate, objectMapper, sessionRegistry);
    }

    @Test
    @DisplayName("publish：发送到固定频道，payload 带 userId/reason/source")
    void publish_shouldSendPayloadToChannel() throws Exception {
        broadcaster.publish(1L, "PASSWORD_CHANGED");

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        assertEquals(TokenInvalidationBroadcaster.CHANNEL, channelCaptor.getValue());
        JsonNode node = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(1L, node.path("userId").asLong());
        assertEquals("PASSWORD_CHANGED", node.path("reason").asText());
        assertEquals(broadcaster.getInstanceId(), node.path("source").asText());
    }

    @Test
    @DisplayName("handleMessage：非本实例消息 → 关闭本机对应连接")
    void handleMessage_shouldCloseRemoteSessions() {
        broadcaster.handleMessage("{\"userId\":9,\"reason\":\"PASSWORD_RESET\",\"source\":\"other-instance\"}");
        verify(sessionRegistry).closeUserSessions(9L, "PASSWORD_RESET");
    }

    @Test
    @DisplayName("handleMessage：自己发出的消息 → 忽略，避免重复关闭")
    void handleMessage_shouldIgnoreSelfMessage() {
        String selfMessage = "{\"userId\":9,\"reason\":\"PASSWORD_RESET\",\"source\":\""
                + broadcaster.getInstanceId() + "\"}";
        broadcaster.handleMessage(selfMessage);
        verify(sessionRegistry, never()).closeUserSessions(anyLong(), anyString());
    }

    @Test
    @DisplayName("handleMessage：非法消息 → 忽略且不抛异常")
    void handleMessage_shouldIgnoreMalformedMessage() {
        broadcaster.handleMessage("{bad json");
        broadcaster.handleMessage("{\"userId\":0}");
        assertTrue(true, "非法消息不应导致监听线程退出");
        verify(sessionRegistry, never()).closeUserSessions(anyLong(), anyString());
    }
}
