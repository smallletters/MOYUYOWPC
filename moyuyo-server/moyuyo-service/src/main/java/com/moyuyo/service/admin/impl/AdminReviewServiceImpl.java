package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.enums.OrderStatusEnum;
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

  /** 视为"已评价"的状态集合：PENDING（用户刚提交待审核也算已占坑）、已审核、REPLIED */
  private static final Set<String> REVIEWED_STATUSES =
    new HashSet<>(Arrays.asList("待审核", "已审核", "REPLIED"));

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
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getStatus, "待审核"));

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
    // 直接更新状态为已回复，并在评价内容末尾追加回复标记
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("REPLIED");
      // 没有 replyContent 字段，在 content 字段末尾追加回复内容
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
    entity.setStatus("已审核");
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
   * 检查订单是否所有 item 都已评价（覆盖已审核/待审核/已回复），是则把订单状态推进到 COMPLETED。
   * 采用条件更新（WHERE status IN (RECEIVED)）保证幂等。
   */
  public void tryCompleteOrderByReview(Long orderId) {
    // 1. 所有 item 总数
    List<OrderItemEntity> items = orderItemMapper.selectList(
      new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, orderId));
    if (items == null || items.isEmpty()) {
      return;
    }
    // 2. 已被评价覆盖的 itemId 集合（包含待审核/已审核/已回复，驳回的不算）
    List<ProductReviewEntity> reviews = productReviewMapper.selectList(
      new LambdaQueryWrapper<ProductReviewEntity>()
        .eq(ProductReviewEntity::getOrderId, orderId)
        .in(ProductReviewEntity::getStatus, REVIEWED_STATUSES));
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
      entity.setStatus("已驳回");
      productReviewMapper.updateById(entity);
    }
  }
}
