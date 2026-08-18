package com.moyuyo.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.mq.TopicConstant;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单超时检查消费者
 * <p>
 * 监听订单创建消息，延迟30分钟后检查订单是否已支付，未支付则自动取消。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
    topic = TopicConstant.ORDER_CREATED,
    consumerGroup = TopicConstant.CONSUMER_GROUP_ORDER,
    messageModel = MessageModel.CLUSTERING
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        log.info("收到订单超时检查消息: {}", message);
        try {
            // 解析消息获取订单ID
            Long orderId = null;
            try {
                @SuppressWarnings("unchecked")
                var msgMap = objectMapper.readValue(message, java.util.Map.class);
                orderId = Long.valueOf(msgMap.get("orderId").toString());
            } catch (Exception e) {
                log.warn("无法解析消息中的订单ID，尝试直接解析为数字: {}", message);
                orderId = Long.valueOf(message.trim());
            }

            // 查询订单详情
            OrderEntity order = orderService.getOrderDetail(orderId, null);
            if (order == null) {
                log.warn("订单不存在: {}", orderId);
                return;
            }

            // 检查订单状态，仅自动取消未支付的订单
            // P1 修复：原实现硬编码 "PENDING"/"UNPAID" 字符串，但 OrderStatusEnum 实际状态名为 PENDING_PAY，
            // 导致超时订单永远不会被自动取消。改用枚举 name() 避免字符串漂移
            if (OrderStatusEnum.PENDING_PAY.name().equals(order.getStatus())) {
                log.info("订单 {} 超时未支付，自动取消", orderId);
                orderService.cancelOrder(orderId, null, "订单超时自动取消");
            } else {
                log.info("订单 {} 当前状态为 {}，无需自动取消", orderId, order.getStatus());
            }
        } catch (Exception e) {
            log.error("处理订单超时检查消息失败: {}", e.getMessage(), e);
        }
    }
}
