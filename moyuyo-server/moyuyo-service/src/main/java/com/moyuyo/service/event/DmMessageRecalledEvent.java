package com.moyuyo.service.event;

/**
 * 私信消息已撤回事件
 *
 * 事务提交后推送给接收方与发送方（多端同步），前端按 messageId 原地更新为撤回占位。
 */
public record DmMessageRecalledEvent(
        Long receiverId,
        Long senderId,
        Long conversationId,
        Long messageId) {
}
