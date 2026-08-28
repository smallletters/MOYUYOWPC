package com.moyuyo.service.mq;

import com.moyuyo.common.mq.NotificationMessage;
import com.moyuyo.common.mq.TopicConstant;
import com.moyuyo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通知消费者
 * <p>
 * 监听 NOTIFICATION_SEND Topic，将业务侧发送的通知落库到 mo_notification 表，
 * 供用户端 /pages/user/notifications 页面读取展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
    topic = TopicConstant.NOTIFICATION_SEND,
    consumerGroup = TopicConstant.CONSUMER_GROUP_NOTIFICATION,
    messageModel = MessageModel.CLUSTERING
)
public class NotificationConsumer implements RocketMQListener<NotificationMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(NotificationMessage message) {
        log.info("收到通知消息: userId={}, type={}, title={}",
                message.getUserId(), message.getType(), message.getTitle());
        try {
            // 字段校验：userId / type / title 缺失视为非法消息
            if (message.getUserId() == null
                    || message.getType() == null
                    || message.getTitle() == null) {
                log.warn("通知消息字段不完整，跳过: {}", message);
                return;
            }
            notificationService.saveNotification(
                    message.getUserId(),
                    message.getType(),
                    message.getTitle(),
                    message.getContent(),
                    message.getRelatedId());
        } catch (Exception e) {
            // 异常仅记录日志，不抛出避免消息无限重试；如需重试可改为抛出并配置 RocketMQ 重试次数
            log.error("处理通知消息失败: {}", e.getMessage(), e);
        }
    }
}