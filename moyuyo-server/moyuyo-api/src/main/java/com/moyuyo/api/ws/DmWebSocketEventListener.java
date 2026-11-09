package com.moyuyo.api.ws;

import com.moyuyo.service.event.DmConversationChangedEvent;
import com.moyuyo.service.event.DmGroupMessageRecalledEvent;
import com.moyuyo.service.event.DmGroupMessageSentEvent;
import com.moyuyo.service.event.DmMessageReadEvent;
import com.moyuyo.service.event.DmMessageRecalledEvent;
import com.moyuyo.service.event.DmMessageSentEvent;
import com.moyuyo.service.event.UserTokenInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 私信消息事件 → WebSocket 推送
 *
 * 使用 AFTER_COMMIT：只有消息真正落库后才推送给接收方，避免事务回滚时推送假消息。
 */
@Component
@RequiredArgsConstructor
public class DmWebSocketEventListener {

    private final DmSessionRegistry sessionRegistry;
    /** token 失效跨实例广播（Redis Pub/Sub），多实例部署时关闭其他节点上的连接 */
    private final TokenInvalidationBroadcaster tokenInvalidationBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(DmMessageSentEvent event) {
        if (event == null || event.receiverId() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("message", event.message());
        data.put("senderId", event.message().getSenderId());
        data.put("senderNickname", event.senderNickname());
        data.put("senderAvatar", event.senderAvatar());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_message");
        payload.put("data", data);

        sessionRegistry.sendToUser(event.receiverId(), payload);
    }

    /** 撤回事件：同时推给接收方与发送方，保证双方（含多端）都能原地更新 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageRecalled(DmMessageRecalledEvent event) {
        if (event == null || event.conversationId() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("messageId", event.messageId());
        data.put("senderId", event.senderId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_recall");
        payload.put("data", data);

        sessionRegistry.sendToUser(event.receiverId(), payload);
        sessionRegistry.sendToUser(event.senderId(), payload);
    }

    /** 已读回执：只推给原发送方（含多端），用于把当前会话中自己发的消息标记为已读 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageRead(DmMessageReadEvent event) {
        if (event == null || event.conversationId() == null || event.senderIds() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("readerId", event.readerId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_read");
        payload.put("data", data);

        event.senderIds().forEach(senderId -> sessionRegistry.sendToUser(senderId, payload));
    }

    /** 群聊消息：广播给全部群成员（发送者跳过，由本地消息回显） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupMessageSent(DmGroupMessageSentEvent event) {
        if (event == null || event.memberIds() == null || event.conversationId() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("message", event.message());
        data.put("senderId", event.senderId());
        data.put("senderNickname", event.senderNickname());
        data.put("senderAvatar", event.senderAvatar());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_message");
        payload.put("data", data);

        event.memberIds().stream()
                .filter(memberId -> memberId != null && !memberId.equals(event.senderId()))
                .forEach(memberId -> sessionRegistry.sendToUser(memberId, payload));
    }

    /** 群聊消息撤回：广播给全部群成员 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupMessageRecalled(DmGroupMessageRecalledEvent event) {
        if (event == null || event.memberIds() == null || event.conversationId() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("messageId", event.messageId());
        data.put("senderId", event.senderId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_recall");
        payload.put("data", data);

        event.memberIds().stream()
                .filter(memberId -> memberId != null)
                .forEach(memberId -> sessionRegistry.sendToUser(memberId, payload));
    }

    /**
     * token 失效（改密 / 重置密码）→ 强制关闭该用户全部在线连接。
     * <p>
     * 使用 AFTER_COMMIT：只有密码真正落库后才断开，避免事务回滚时误踢用户；
     * fallbackExecution=true 兼容非事务调用场景。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTokenInvalidated(UserTokenInvalidatedEvent event) {
        if (event == null || event.userId() == null) return;
        // 先关闭本实例上的连接，再广播给其他实例（忽略本实例自己发出的广播）
        sessionRegistry.closeUserSessions(event.userId(), event.reason());
        tokenInvalidationBroadcaster.publish(event.userId(), event.reason());
    }

    /**
     * 会话状态变更（多端同步）：只推给操作者本人，
     * 同账号其他在线端据此刷新列表/未读数或移除已删除消息。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationChanged(DmConversationChangedEvent event) {
        if (event == null || event.userId() == null || event.conversationId() == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", event.conversationId());
        data.put("action", event.action());
        // 仅消息删除需要携带具体ID，其余动作前端按 action 刷新
        if (event.messageIds() != null) {
            data.put("messageIds", event.messageIds());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dm_conversation_changed");
        payload.put("data", data);

        sessionRegistry.sendToUser(event.userId(), payload);
    }
}
