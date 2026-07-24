package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.AfterSalesEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.mapper.AfterSalesMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.AfterSalesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSalesServiceImpl implements AfterSalesService {

  private final AfterSalesMapper afterSalesMapper;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;

  @Override
  public IPage<AfterSalesEntity> listAfterSales(Long userId, int page, int size, String status) {
    LambdaQueryWrapper<AfterSalesEntity> wrapper = new LambdaQueryWrapper<AfterSalesEntity>()
        .eq(AfterSalesEntity::getUserId, userId);

    if (status != null && !status.isEmpty()) {
      wrapper.eq(AfterSalesEntity::getStatus, status);
    }

    wrapper.orderByDesc(AfterSalesEntity::getCreateTime);
    return afterSalesMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public AfterSalesEntity getAfterSalesDetail(Long id, Long userId) {
    AfterSalesEntity entity = afterSalesMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("售后申请不存在");
    }
    if (!Objects.equals(entity.getUserId(), userId)) {
      throw new IllegalArgumentException("无权访问该售后申请");
    }
    return entity;
  }

  @Override
  @Transactional
  public AfterSalesEntity createAfterSales(Long userId, Long orderId, Long orderItemId, String type,
                                           String reason, BigDecimal amount, String description, String images) {
    // 校验订单是否存在且属于该用户
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在");
    }
    if (!Objects.equals(order.getUserId(), userId)) {
      throw new IllegalArgumentException("订单不属于当前用户");
    }

    // 校验订单项是否属于该订单
    if (orderItemId != null) {
      OrderItemEntity orderItem = orderItemMapper.selectById(orderItemId);
      if (orderItem == null || !Objects.equals(orderItem.getOrderId(), orderId)) {
        throw new IllegalArgumentException("订单项不属于该订单");
      }
    }

    AfterSalesEntity entity = new AfterSalesEntity();
    entity.setUserId(userId);
    entity.setOrderId(orderId);
    entity.setOrderItemId(orderItemId);
    entity.setType(type);
    entity.setReason(reason);
    entity.setAmount(amount);
    entity.setDescription(description);
    entity.setImages(images);
    entity.setStatus("PENDING");
    afterSalesMapper.insert(entity);
    log.info("After-sales request created: orderId={}, type={}, userId={}", orderId, type, userId);
    return entity;
  }
}
