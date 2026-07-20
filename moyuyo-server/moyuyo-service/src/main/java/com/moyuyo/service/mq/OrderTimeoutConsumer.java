package com.moyuyo.service.mq;

import com.moyuyo.common.mq.TopicConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时检查消费者
 * <p>
 * 监听订单创建消息，延迟30分钟后检查订单是否已支付，未支付则自动取消。
 * </p>
 */
@Slf4j
@RocketMQMessageListener(
    topic = TopicConstant.ORDER_CREATED,
    consumerGroup = TopicConstant.CONSUMER_GROUP_ORDER,
    messageModel = MessageModel.CLUSTERING
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("收到订单超时检查消息: {}", message);
        // 延迟30分钟后检查订单是否已支付，未支付则自动取消
        // TODO: 实际逻辑使用 RocketMQ 延迟消息等级
    }
}
