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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台评价管理服务实现
 */
// 抑制 JDT null-analysis 对 MyBatis-Plus SFunction / Stream 方法引用的误报
// （底层 SFunction 的 @Nonnull 类型参数 vs Function.apply 形参推断冲突，mvn 编译无影响）
@SuppressWarnings("null")
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

  /**
   * 今日评价审核统计：基于 mo_product_review.create_time 落在"今日/昨日"区间，
   * 按状态分组计数。今日待审核取的是"今日产生的评价中当前仍为 PENDING 的数量"，
   * 今日已通过 / 已驳回取的是"今日产生的评价中当前状态为 APPROVED / REJECTED 的数量"，
   * 与卡片语义（今日工作量 + 通过率）一致。
   *
   * 实现说明：每日一次 GROUP BY 查询返回 PENDING/APPROVED/REJECTED 三个状态的数量，
   * 避免按状态拆 3 次 selectCount（原来 6 次 SQL 现合并为 2 次）。
   */
  @Override
  public Map<String, Object> todayStats() {
    LocalDate today = LocalDate.now();
    LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.of(today.plusDays(1), LocalTime.MIN);
    LocalDateTime yesterdayStart = LocalDateTime.of(today.minusDays(1), LocalTime.MIN);

    // 一次 SQL 按状态分组计数：SELECT status, COUNT(*) ... WHERE create_time BETWEEN ? AND ? GROUP BY status
    Map<String, Long> todayCounts = countByStatusGrouped(todayStart, todayEnd);
    Map<String, Long> yesterdayCounts = countByStatusGrouped(yesterdayStart, todayStart);

    long todayPending = todayCounts.getOrDefault(ReviewStatusEnum.PENDING.name(), 0L);
    long todayApproved = todayCounts.getOrDefault(ReviewStatusEnum.APPROVED.name(), 0L);
    long todayRejected = todayCounts.getOrDefault(ReviewStatusEnum.REJECTED.name(), 0L);
    long yesterdayPending = yesterdayCounts.getOrDefault(ReviewStatusEnum.PENDING.name(), 0L);
    long yesterdayApproved = yesterdayCounts.getOrDefault(ReviewStatusEnum.APPROVED.name(), 0L);
    long yesterdayRejected = yesterdayCounts.getOrDefault(ReviewStatusEnum.REJECTED.name(), 0L);

    // 今日通过率 = 已通过 / (已通过 + 已驳回)，无审结则按 0%
    long approvedPlusRejected = todayApproved + todayRejected;
    double passRate = approvedPlusRejected > 0
        ? (double) todayApproved / approvedPlusRejected * 100
        : 0d;
    long yesterdayApprovedPlusRejected = yesterdayApproved + yesterdayRejected;
    double yesterdayPassRate = yesterdayApprovedPlusRejected > 0
        ? (double) yesterdayApproved / yesterdayApprovedPlusRejected * 100
        : 0d;

    Map<String, Object> result = new HashMap<>();
    // 今日数据
    result.put("pending", todayPending);
    result.put("approved", todayApproved);
    result.put("rejected", todayRejected);
    result.put("passRate", passRate);
    // 昨日数据（用于趋势对比）
    result.put("yesterdayPending", yesterdayPending);
    result.put("yesterdayApproved", yesterdayApproved);
    result.put("yesterdayRejected", yesterdayRejected);
    result.put("yesterdayPassRate", yesterdayPassRate);
    return result;
  }

  /**
   * 按状态分组统计指定时间区间内的评价数量。
   * 返回 Map<status, count>，缺失的状态不会作为 key 出现，调用方需用 getOrDefault 兜底。
   */
  private Map<String, Long> countByStatusGrouped(LocalDateTime start, LocalDateTime end) {
    // 自定义 SQL：SELECT status, COUNT(*) ... GROUP BY status，单次查询返回各状态数量
    List<Map<String, Object>> rows = productReviewMapper.countByStatusInTimeRange(start, end);
    Map<String, Long> result = new HashMap<>();
    for (Map<String, Object> row : rows) {
      Object statusVal = row.get("status");
      Object cntVal = row.get("cnt");
      if (statusVal == null) {
        continue;
      }
      long cnt = cntVal instanceof Number ? ((Number) cntVal).longValue() : 0L;
      result.put(statusVal.toString(), cnt);
    }
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

  @Override
  @Transactional
  public int batchApprove(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    // 单条 SQL 批量更新：仅命中 PENDING 的评价，避免重复处理已审结的评价
    int updated = productReviewMapper.update(null,
        new LambdaUpdateWrapper<ProductReviewEntity>()
            .in(ProductReviewEntity::getId, ids)
            .eq(ProductReviewEntity::getStatus, ReviewStatusEnum.PENDING.name())
            .set(ProductReviewEntity::getStatus, ReviewStatusEnum.APPROVED.name()));
    if (updated == 0) {
      return 0;
    }

    // 找出本次涉及到的唯一 orderId,对每个订单尝试推进完结（与单条 approve 行为一致）。
    // 用 Set 去重,避免同一订单被 tryCompleteOrderByReview 调用多次（每次内部都有一次 SELECT COUNT）。
    List<ProductReviewEntity> approvedReviews = productReviewMapper.selectList(
        new LambdaQueryWrapper<ProductReviewEntity>()
            .in(ProductReviewEntity::getId, ids)
            .eq(ProductReviewEntity::getStatus, ReviewStatusEnum.APPROVED.name()));
    Set<Long> orderIds = new HashSet<>();
    for (ProductReviewEntity r : approvedReviews) {
      if (r.getOrderId() != null) {
        orderIds.add(r.getOrderId());
      }
    }
    for (Long orderId : orderIds) {
      try {
        tryCompleteOrderByReview(orderId);
      } catch (Exception e) {
        // 单个订单完结失败不应阻断其他订单,也不应回滚本次批量审核
        log.warn("[review-batch-approve] 批量通过后尝试完结订单失败 orderId={}, reason={}",
          orderId, e.getMessage());
      }
    }
    return updated;
  }
}
