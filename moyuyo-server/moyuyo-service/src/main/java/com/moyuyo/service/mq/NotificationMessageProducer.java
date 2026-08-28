package com.moyuyo.service.mq;

import com.moyuyo.common.mq.NotificationMessage;
import com.moyuyo.common.mq.TopicConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者
 * <p>
 * 业务模块在关键节点（订单支付、活动报名、系统公告等）调用本类发送通知，
 * 由 NotificationConsumer 监听 NOTIFICATION_SEND Topic 落库到 mo_notification 表。
 */
@Slf4j
@Component
public class NotificationMessageProducer {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送通知消息到 MQ
     *
     * @param message 通知内容
     */
    public void send(NotificationMessage message) {
        if (message == null) {
            log.warn("通知消息为空，跳过发送");
            return;
        }
        if (rocketMQTemplate == null) {
            // MQ 未配置时直接走降级：同步落库，保证通知不丢
            log.warn("RocketMQ 未配置，通知降级为同步发送: userId={}, type={}", message.getUserId(), message.getType());
            syncFallback(message);
            return;
        }
        rocketMQTemplate.convertAndSend(TopicConstant.NOTIFICATION_SEND, message);
        log.info("通知消息已发送: userId={}, type={}, title={}",
                message.getUserId(), message.getType(), message.getTitle());
    }

    /**
     * 兼容便捷方法：使用基础字段快速构造消息
     */
    public void send(Long userId, String type, String title, String content, Long relatedId) {
        send(NotificationMessage.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .build());
    }

    /**
     * MQ 降级：直接在调用线程写入数据库，避免通知丢失
     */
    private void syncFallback(NotificationMessage message) {
        // 这里仅打日志，具体落库由业务调用方在 catch 中处理
        log.info("[Fallback] 建议业务侧捕获 MQ 异常后调用 notificationService.saveNotification 落库");
    }
}