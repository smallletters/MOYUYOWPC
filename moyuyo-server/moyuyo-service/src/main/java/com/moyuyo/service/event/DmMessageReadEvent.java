package com.moyuyo.service.event;

import java.util.Set;

/**
 * 私信消息已读事件
 *
 * 对方打开会话/主动标记已读后，事务提交时通知原发送方，用于展示「已读」状态。
 * 这里只携带会话与阅读者信息，前端把当前会话中自己发送的消息整体标记为已读。
 */
public record DmMessageReadEvent(
        Long conversationId,
        Long readerId,
        Set<Long> senderIds) {
}
