package com.moyuyo.service.event;

import com.moyuyo.dao.entity.DmMessageEntity;

/**
 * 私信消息已发送事件
 *
 * 由 DmService 在事务内发布，WebSocket 监听器在事务提交后（AFTER_COMMIT）推送，
 * 避免"消息未落库却已推送给接收方"的假通知。
 */
public record DmMessageSentEvent(
        Long receiverId,
        Long conversationId,
        DmMessageEntity message,
        String senderNickname,
        String senderAvatar) {
}
