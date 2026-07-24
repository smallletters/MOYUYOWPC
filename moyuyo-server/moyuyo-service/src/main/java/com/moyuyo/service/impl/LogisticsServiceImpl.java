package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsMapper logisticsMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public LogisticsEntity shipOrder(Long orderId, String carrier, String trackingNumber) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!OrderStatusEnum.PAID.name().equals(order.getStatus())) throw new IllegalStateException("订单未支付，不能发货");

        LogisticsEntity existing = logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId));
        if (existing != null) throw new IllegalStateException("该订单已发货");

        LogisticsEntity logistics = new LogisticsEntity();
        logistics.setOrderId(orderId);
        logistics.setCarrier(carrier);
        logistics.setTrackingNumber(trackingNumber);
        logistics.setShippedAt(LocalDateTime.now());
        logistics.setTraces(toTracesJson("Shipped", carrier, trackingNumber));
        logisticsMapper.insert(logistics);

        order.setStatus(OrderStatusEnum.SHIPPED.name());
        order.setShippingCarrier(carrier);
        order.setTrackingNumber(trackingNumber);
        order.setDeliverTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order shipped: orderId={}, carrier={}, tracking={}", orderId, carrier, trackingNumber);
        return logistics;
    }

    @Override
    public LogisticsEntity getLogisticsByOrderId(Long orderId) {
        return logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId));
    }

    @Override
    @Transactional
    public LogisticsEntity updateTracking(Long logisticsId, String traces) {
        LogisticsEntity entity = logisticsMapper.selectById(logisticsId);
        if (entity == null) throw new IllegalArgumentException("物流记录不存在");
        entity.setTraces(traces);
        logisticsMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void confirmReceived(Long orderId) {
        LogisticsEntity logistics = logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId));
        if (logistics != null) {
            logistics.setReceivedAt(LocalDateTime.now());
            logisticsMapper.updateById(logistics);
        }

        OrderEntity order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setStatus(OrderStatusEnum.RECEIVED.name());
            order.setReceivedTime(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        log.info("Delivery confirmed: orderId={}", orderId);
    }

    private String toTracesJson(String event, String carrier, String tracking) {
        try {
            return objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("time", LocalDateTime.now().toString(), "location", "",
                            "desc", "Package shipped via " + carrier + ", tracking: " + tracking,
                            "status", "shipped")));
        } catch (JsonProcessingException e) {
            log.error("JSON serialization error", e);
            return "[]";
        }
    }
}
