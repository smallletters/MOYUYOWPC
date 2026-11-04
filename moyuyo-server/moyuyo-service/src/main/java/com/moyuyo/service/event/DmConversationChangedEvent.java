package com.moyuyo.service.event;

import java.util.List;

/**
 * 私信会话状态变更事件（多端同步）
 *
 * 用于把「删除消息 / 置顶 / 删除会话 / 标记已读」这类只影响操作者本人视图的变更，
 * 推送给同一账号的其他在线端；由 WebSocket 监听器在事务提交后推送。
 *
 * @param action     变更动作：messages_deleted / pin / conversation_deleted / read
 * @param messageIds 被删除的消息ID（仅 messages_deleted 有值）
 */
public record DmConversationChangedEvent(
        Long userId,
        Long conversationId,
        String action,
        List<Long> messageIds) {
}
