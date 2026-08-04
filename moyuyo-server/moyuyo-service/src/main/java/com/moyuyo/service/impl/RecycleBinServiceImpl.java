package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.OrderInterceptEntity;
import com.moyuyo.dao.admin.entity.OrderPriceModifyEntity;
import com.moyuyo.dao.admin.entity.OrderPrintLogEntity;
import com.moyuyo.dao.admin.mapper.OrderInterceptMapper;
import com.moyuyo.dao.admin.mapper.OrderPriceModifyMapper;
import com.moyuyo.dao.admin.mapper.OrderPrintLogMapper;
import com.moyuyo.dao.admin.mapper.OrderTagMapper;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final LogisticsMapper logisticsMapper;
  private final PaymentMapper paymentMapper;
  private final OrderPriceModifyMapper orderPriceModifyMapper;
  private final OrderPrintLogMapper orderPrintLogMapper;
  private final OrderInterceptMapper orderInterceptMapper;
  private final OrderTagMapper orderTagMapper;

  @Override
  public IPage<OrderEntity> listDeletedOrders(Long userId, int page, int size) {
    return orderMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .eq(OrderEntity::getDeleteStatus, 1)
            .orderByDesc(OrderEntity::getUpdateTime));
  }

  @Override
  @Transactional
  public void restoreOrder(Long orderId, Long userId) {
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在");
    }
    if (!Objects.equals(order.getUserId(), userId)) {
      throw new IllegalArgumentException("无权操作该订单");
    }
    if (order.getDeleteStatus() != 1) {
      throw new IllegalStateException("订单不在回收站中");
    }
    order.setDeleteStatus(0);
    orderMapper.updateById(order);
    log.info("Order restored from recycle bin: orderId={}, userId={}", orderId, userId);
  }

  @Override
  @Transactional
  public void permanentlyDelete(Long orderId, Long userId) {
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在");
    }
    if (!Objects.equals(order.getUserId(), userId)) {
      throw new IllegalArgumentException("无权操作该订单");
    }
    if (order.getDeleteStatus() != 1) {
      throw new IllegalStateException("订单不在回收站中");
    }
    // 级联删除订单关联的所有子表数据
    deleteOrderChildren(orderId);
    orderMapper.deleteById(orderId);
    log.info("Order permanently deleted: orderId={}, userId={}", orderId, userId);
  }

  @Override
  @Transactional
  public void clearAll(Long userId) {
    // 先查询所有待删除的订单ID
    List<OrderEntity> orders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .eq(OrderEntity::getDeleteStatus, 1));
    for (OrderEntity order : orders) {
      deleteOrderChildren(order.getId());
    }
    orderMapper.delete(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .eq(OrderEntity::getDeleteStatus, 1));
    log.info("Recycle bin cleared: userId={}", userId);
  }

  /** 级联删除订单下的所有关联子表数据 */
  private void deleteOrderChildren(Long orderId) {
    orderItemMapper.delete(new LambdaQueryWrapper<OrderItemEntity>()
        .eq(OrderItemEntity::getOrderId, orderId));
    logisticsMapper.delete(new LambdaQueryWrapper<LogisticsEntity>()
        .eq(LogisticsEntity::getOrderId, orderId));
    paymentMapper.delete(new LambdaQueryWrapper<PaymentEntity>()
        .eq(PaymentEntity::getOrderId, orderId));
    orderPriceModifyMapper.delete(new LambdaQueryWrapper<OrderPriceModifyEntity>()
        .eq(OrderPriceModifyEntity::getOrderId, orderId));
    orderPrintLogMapper.delete(new LambdaQueryWrapper<OrderPrintLogEntity>()
        .eq(OrderPrintLogEntity::getOrderId, orderId));
    orderInterceptMapper.delete(new LambdaQueryWrapper<OrderInterceptEntity>()
        .eq(OrderInterceptEntity::getOrderId, orderId));
  }
}
