package com.moyuyo.service.event;

import com.moyuyo.dao.entity.DmMessageEntity;

import java.util.List;

/**
 * 群聊消息已发送事件
 *
 * 与单聊事件不同，群聊需要向全部成员广播（发送者本人由本地消息回显）。
 */
public record DmGroupMessageSentEvent(
        List<Long> memberIds,
        Long senderId,
        Long conversationId,
        DmMessageEntity message,
        String senderNickname,
        String senderAvatar) {
}
