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
        // 已支付（PAID）或待发货（PENDING_SHIP）状态均可发货
        String status = order.getStatus();
        if (!OrderStatusEnum.PAID.name().equals(status) && !OrderStatusEnum.PENDING_SHIP.name().equals(status)) {
            throw new IllegalStateException("订单未支付或不在待发货状态，不能发货");
        }

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
        LocalDateTime now = LocalDateTime.now();

        // 使用条件更新避免 TOCTOU 竞态条件：仅在未收货时才更新 receivedAt
        int logisticsUpdated = logisticsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId)
                        .isNull(LogisticsEntity::getReceivedAt)
                        .set(LogisticsEntity::getReceivedAt, now));

        // 使用条件更新：仅在已发货状态时才变更为已收货，防止状态错乱
        int orderUpdated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderEntity>()
                        .eq(OrderEntity::getId, orderId)
                        .eq(OrderEntity::getStatus, OrderStatusEnum.SHIPPED.name())
                        .set(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
                        .set(OrderEntity::getReceivedTime, now));

        // 只要任一表有更新就记录日志，重复调用不抛出异常（幂等）
        if (logisticsUpdated > 0 || orderUpdated > 0) {
            log.info("Delivery confirmed: orderId={}", orderId);
        } else {
            log.warn("Delivery confirm skipped (already received or not shipped): orderId={}", orderId);
        }
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
