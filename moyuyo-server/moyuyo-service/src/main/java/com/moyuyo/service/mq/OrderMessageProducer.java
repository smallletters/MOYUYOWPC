package com.moyuyo.service.mq;

import com.moyuyo.common.mq.TopicConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者
 */
@Slf4j
@Component
public class OrderMessageProducer {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public void sendOrderCreated(Long orderId) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQ 未配置，跳过消息发送: orderId={}", orderId);
            return;
        }
        rocketMQTemplate.convertAndSend(TopicConstant.ORDER_CREATED, orderId);
        log.info("订单创建消息已发送: orderId={}", orderId);
    }

    public void sendOrderPaid(Long orderId) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQ 未配置，跳过消息发送: orderId={}", orderId);
            return;
        }
        rocketMQTemplate.convertAndSend(TopicConstant.ORDER_PAID, orderId);
        log.info("订单支付消息已发送: orderId={}", orderId);
    }

    public void sendOrderCancelled(Long orderId, String reason) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQ 未配置，跳过消息发送: orderId={}", orderId);
            return;
        }
        rocketMQTemplate.convertAndSend(TopicConstant.ORDER_CANCELLED, orderId + ":" + reason);
        log.info("订单取消消息已发送: orderId={}, reason={}", orderId, reason);
    }
}
