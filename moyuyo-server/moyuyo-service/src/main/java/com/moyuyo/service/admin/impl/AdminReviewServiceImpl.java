package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.enums.ReviewStatusEnum;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.service.admin.AdminReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台评价管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

  private final ProductReviewMapper productReviewMapper;
  private final OrderItemMapper orderItemMapper;
  private final OrderMapper orderMapper;

  @Override
  public Page<ProductReviewEntity> listAll(String status, int page, int size) {
    LambdaQueryWrapper<ProductReviewEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(ProductReviewEntity::getStatus, status);
    }
    wrapper.orderByDesc(ProductReviewEntity::getCreateTime);
    return productReviewMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 查询所有评价
    Long total = productReviewMapper.selectCount(new LambdaQueryWrapper<>());
    // 好评（评分 >= 4）
    Long positive = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().ge(ProductReviewEntity::getRating, 4));
    // 中评（评分 = 3）
    Long neutral = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getRating, 3));
    // 差评（评分 <= 2）
    Long negative = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().le(ProductReviewEntity::getRating, 2));
    // 待审核
    Long pending = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getStatus,
            ReviewStatusEnum.PENDING.name()));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("positive", positive);
    result.put("neutral", neutral);
    result.put("negative", negative);
    result.put("pending", pending);
    // 好评率 = 好评数 / 总数 * 100
    result.put("positiveRate", total > 0 ? (double) positive / total * 100 : 0);
    return result;
  }

  @Override
  public void reply(Long id, String content) {
    // 没有独立 replyContent 字段：在 content 末尾追加客服回复标记。
    // 不改变 status（迁移后已统一为 APPROVED/PENDING/REJECTED），避免评价从 C 端列表消失。
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null && content != null && !content.isBlank()) {
      String originalContent = entity.getContent() != null ? entity.getContent() : "";
      entity.setContent(originalContent + "\n[客服回复]: " + content);
      productReviewMapper.updateById(entity);
    }
  }

  @Override
  public void delete(Long id) {
    productReviewMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void approve(Long id) {
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity == null) {
      return;
    }
    entity.setStatus(ReviewStatusEnum.APPROVED.name());
    productReviewMapper.updateById(entity);

    // 审批通过后：如果订单所有 item 都已被覆盖评价，则订单流转到 COMPLETED（和主流电商一致）
    Long orderId = entity.getOrderId();
    if (orderId == null) {
      return;
    }
    try {
      tryCompleteOrderByReview(orderId);
    } catch (Exception e) {
      // 评价流转仅影响"已完成"归档，失败不能回滚审批本身
      log.warn("[review-complete] 审批后尝试完结订单失败 orderId={}, reason={}",
        orderId, e.getMessage());
    }
  }

  /**
   * 审批通过后检查订单：仅当订单所有 item 都有“审核通过(APPROVED)”的评价（含系统默认好评）时，
   * 才把订单推进 COMPLETED。待审核/已驳回不触发完结，避免在评价未过审时误完结。
   * 采用条件更新（WHERE status = RECEIVED）保证幂等。
   */
  public void tryCompleteOrderByReview(Long orderId) {
    // 1. 所有 item 总数
    List<OrderItemEntity> items = orderItemMapper.selectList(
      new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, orderId));
    if (items == null || items.isEmpty()) {
      return;
    }
    // 2. 审核通过(APPROVED)的评价所覆盖的 itemId 集合
    List<ProductReviewEntity> reviews = productReviewMapper.selectList(
      new LambdaQueryWrapper<ProductReviewEntity>()
        .eq(ProductReviewEntity::getOrderId, orderId)
        .eq(ProductReviewEntity::getStatus, ReviewStatusEnum.APPROVED.name()));
    Set<Long> reviewedItemIds = new HashSet<>();
    for (ProductReviewEntity r : reviews) {
      if (r.getOrderItemId() != null) {
        reviewedItemIds.add(r.getOrderItemId());
      }
    }
    // 兼容：若某条旧评价 orderItemId 为空但 productId 能匹配到单 item，视为覆盖
    for (ProductReviewEntity r : reviews) {
      if (r.getOrderItemId() == null && r.getProductId() != null) {
        for (OrderItemEntity item : items) {
          if (r.getProductId().equals(item.getProductId())) {
            reviewedItemIds.add(item.getId());
          }
        }
      }
    }

    boolean allReviewed = true;
    for (OrderItemEntity item : items) {
      if (!reviewedItemIds.contains(item.getId())) {
        allReviewed = false;
        break;
      }
    }
    if (!allReviewed) {
      return;
    }

    // 3. 条件更新：仅当订单仍处于 RECEIVED（或罕见的 PAID->COMPLETED 历史路径）才推进
    int updated = orderMapper.update(null,
      new LambdaUpdateWrapper<OrderEntity>()
        .eq(OrderEntity::getId, orderId)
        .in(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
        .set(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.name()));
    if (updated > 0) {
      log.info("[review-complete] 订单所有 item 已评价，已自动流转到 COMPLETED: orderId={}", orderId);
    }
  }

  @Override
  public void reject(Long id) {
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(ReviewStatusEnum.REJECTED.name());
      productReviewMapper.updateById(entity);
    }
  }
}
