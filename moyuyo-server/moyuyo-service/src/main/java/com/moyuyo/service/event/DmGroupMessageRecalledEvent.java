package com.moyuyo.service.event;

import java.util.List;

/**
 * 群聊消息撤回事件：推送给全部群成员，用于实时替换为撤回占位。
 */
public record DmGroupMessageRecalledEvent(
        List<Long> memberIds,
        Long conversationId,
        Long messageId,
        Long senderId) {
}
