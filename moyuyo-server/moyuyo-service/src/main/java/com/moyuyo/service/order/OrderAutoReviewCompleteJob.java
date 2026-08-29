package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动好评 + 完结订单定时任务
 * <p>
 * 触发条件：订单处于 RECEIVED 状态，且收到时间（receivedTime）距今超过系统设置的"默认好评天数"。
 * <p>
 * 行为与主流电商（淘宝/京东）一致：
 * <ol>
 *   <li>遍历 RECEIVED 且 receivedTime 早于阈值的订单</li>
 *   <li>对尚未被用户评价的 orderItem，插入一条 5 星默认好评（status=已审核，避免额外审批）</li>
 *   <li>将订单状态升级到 COMPLETED（条件更新幂等）</li>
 * </ol>
 * <p>
 * 天数来源优先级：
 * <ol>
 *   <li>mo_system_config.config_key='auto_review_days'</li>
 *   <li>application.yml moyuyo.order.auto-review-days（默认 7 天）</li>
 * </ol>
 * 首次运行会 INSERT IGNORE 一条默认 seed（默认好评 7 天），避免用户去系统设置 UI 手动补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoReviewCompleteJob {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final ProductReviewMapper productReviewMapper;
  private final SystemConfigMapper systemConfigMapper;

  /** 视为"已评价"的状态集合（和 AdminReviewServiceImpl 保持一致） */
  private static final Set<String> REVIEWED_STATUSES =
    new HashSet<>(Arrays.asList("待审核", "已审核", "REPLIED"));

  private static final String CONFIG_KEY_AUTO_REVIEW_DAYS = "auto_review_days";
  private static final int DEFAULT_AUTO_REVIEW_DAYS = 7;
  private static final String DEFAULT_REVIEW_CONTENT =
    "用户超期未评价，系统自动给出好评。商品整体不错，物流也能接受，推荐给其他买家~";

  @Value("${moyuyo.order.auto-review-enabled:true}")
  private boolean enabled;

  @Value("${moyuyo.order.auto-review-days:" + DEFAULT_AUTO_REVIEW_DAYS + "}")
  private int defaultAutoReviewDays;

  @Value("${moyuyo.order.auto-review-batch-size:100}")
  private int batchSize;

  /** 每天 02:30 执行，放在 auto-confirm（02:00）之后，避免同一分钟争抢 DB 资源 */
  @Scheduled(cron = "${moyuyo.order.auto-review-cron:0 30 2 * * ?}")
  public void autoReviewAndComplete() {
    if (!enabled) {
      return;
    }
    try {
      ensureSeed();
      int days = resolveAutoReviewDays();
      LocalDateTime threshold = LocalDateTime.now().minusDays(days);

      List<OrderEntity> expired = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
          .eq(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
          .eq(OrderEntity::getDeleteStatus, 0)
          .isNotNull(OrderEntity::getReceivedTime)
          .lt(OrderEntity::getReceivedTime, threshold)
          .last("LIMIT " + Math.max(1, batchSize)));
      if (expired.isEmpty()) {
        return;
      }
      log.info("[auto-review] 扫描到 {} 笔已收货超过 {} 天仍未评价的订单，开始默认好评 + 完结",
        expired.size(), days);
      int success = 0;
      for (OrderEntity order : expired) {
        try {
          success += processOne(order) ? 1 : 0;
        } catch (Exception e) {
          log.warn("[auto-review] 处理订单失败 orderId={}, reason={}",
            order.getId(), e.getMessage());
        }
      }
      log.info("[auto-review] 默认好评 + 完结完成：成功 {} / 总 {} 笔", success, expired.size());
    } catch (Exception e) {
      log.error("[auto-review] 定时任务异常", e);
    }
  }

  /**
   * 处理单个订单：为缺评价的 item 插入默认好评，再把订单状态升级为 COMPLETED。
   * 用 @Transactional 保证单订单"评价+改状态"要么一起成功要么一起失败。
   */
  @Transactional
  public boolean processOne(OrderEntity order) {
    Long orderId = order.getId();
    Long userId = order.getUserId();

    List<OrderItemEntity> items = orderItemMapper.selectList(
      new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, orderId));
    if (items == null || items.isEmpty()) {
      // 没有 item 的异常订单，直接尝试把状态从 RECEIVED 升到 COMPLETED
      return upgradeToCompleted(orderId) > 0;
    }

    // 已评价的 orderItemId 集合
    List<ProductReviewEntity> reviews = productReviewMapper.selectList(
      new LambdaQueryWrapper<ProductReviewEntity>()
        .eq(ProductReviewEntity::getOrderId, orderId)
        .in(ProductReviewEntity::getStatus, REVIEWED_STATUSES));
    Set<Long> reviewedItemIds = new HashSet<>();
    for (ProductReviewEntity r : reviews) {
      if (r.getOrderItemId() != null) {
        reviewedItemIds.add(r.getOrderItemId());
      } else if (r.getProductId() != null) {
        for (OrderItemEntity item : items) {
          if (r.getProductId().equals(item.getProductId())) {
            reviewedItemIds.add(item.getId());
          }
        }
      }
    }

    LocalDateTime now = LocalDateTime.now();
    List<ProductReviewEntity> inserts = new ArrayList<>();
    for (OrderItemEntity item : items) {
      if (reviewedItemIds.contains(item.getId())) {
        continue;
      }
      ProductReviewEntity autoReview = new ProductReviewEntity();
      autoReview.setProductId(item.getProductId());
      autoReview.setUserId(userId);
      autoReview.setOrderId(orderId);
      autoReview.setOrderItemId(item.getId());
      autoReview.setRating(5); // 默认 5 星好评（主流电商约定）
      autoReview.setContent(DEFAULT_REVIEW_CONTENT);
      autoReview.setTags("系统默认好评");
      autoReview.setStatus("已审核"); // 系统插入的直接审核通过，不走审批流
      // 注意：createTime 由 MyBatis FieldFill.INSERT 自动注入
      inserts.add(autoReview);
    }
    for (ProductReviewEntity entity : inserts) {
      productReviewMapper.insert(entity);
    }

    // 升级订单状态（条件更新：只对 RECEIVED 生效）
    int updated = upgradeToCompleted(orderId);
    if (!inserts.isEmpty()) {
      log.info("[auto-review] 订单 orderId={} 生成 {} 条默认好评，是否同时升级到 COMPLETED: {}",
        orderId, inserts.size(), updated > 0);
    }
    return true;
  }

  private int upgradeToCompleted(Long orderId) {
    return orderMapper.update(null,
      new LambdaUpdateWrapper<OrderEntity>()
        .eq(OrderEntity::getId, orderId)
        .eq(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
        .set(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.name()));
  }

  /**
   * 首次运行兜底：确保 mo_system_config 有 auto_review_days 配置项，便于后续 UI 能直接编辑。
   * 使用 selectOne + insert 而非 upsert，避免跨库差异；INSERT 冲突的概率极低（key 唯一）。
   */
  private void ensureSeed() {
    try {
      SystemConfigEntity exists = systemConfigMapper.selectOne(
        new LambdaQueryWrapper<SystemConfigEntity>()
          .eq(SystemConfigEntity::getConfigKey, CONFIG_KEY_AUTO_REVIEW_DAYS));
      if (exists != null) {
        return;
      }
      SystemConfigEntity seed = new SystemConfigEntity();
      seed.setConfigKey(CONFIG_KEY_AUTO_REVIEW_DAYS);
      seed.setConfigValue(String.valueOf(DEFAULT_AUTO_REVIEW_DAYS));
      seed.setRemark("默认好评天数(天)：收货后超过设定天数未评价则系统自动给出5星好评并完结订单");
      systemConfigMapper.insert(seed);
      log.info("[auto-review] 自动写入系统配置 seed: {}={}", CONFIG_KEY_AUTO_REVIEW_DAYS,
        DEFAULT_AUTO_REVIEW_DAYS);
    } catch (Exception e) {
      log.warn("[auto-review] 写入默认好评 seed 失败（可能唯一键冲突或配置表不存在），继续使用默认天数 {}: {}",
        defaultAutoReviewDays, e.getMessage());
    }
  }

  private int resolveAutoReviewDays() {
    try {
      SystemConfigEntity cfg = systemConfigMapper.selectOne(
        new LambdaQueryWrapper<SystemConfigEntity>()
          .eq(SystemConfigEntity::getConfigKey, CONFIG_KEY_AUTO_REVIEW_DAYS));
      if (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
        int parsed = Integer.parseInt(cfg.getConfigValue().trim());
        if (parsed > 0) {
          return parsed;
        }
      }
    } catch (Exception e) {
      log.warn("[auto-review] 读取 {} 系统配置失败，使用默认值 {}: {}",
        CONFIG_KEY_AUTO_REVIEW_DAYS, defaultAutoReviewDays, e.getMessage());
    }
    return defaultAutoReviewDays;
  }
}
