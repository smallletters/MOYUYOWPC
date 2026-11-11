package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.ProductPresaleEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductPresaleMapper;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预售生命周期定时任务
 * <p>
 * 1) 尾款期结束仍未支付的定金单：按配置退还定金（不可退则没收）并关闭订单，释放预售占用量；
 * 2) 按时间/库存刷新预售状态，避免后台列表长期停留在保存时的旧状态。
 * <p>
 * 两个任务分开调度：退定金涉及第三方渠道调用，频率过高会放大渠道压力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresaleLifecycleJob {

  private final ProductPresaleMapper productPresaleMapper;
  private final OrderMapper orderMapper;
  private final OrderService orderService;
  private final ProductService productService;

  /** 退定金总开关，默认开启；渠道未配置时可关闭，避免反复失败刷日志 */
  @Value("${moyuyo.presale.deposit-refund.enabled:true}")
  private boolean depositRefundEnabled;

  /** 单次处理上限：退款是外部调用，批量过大会拉长单次任务耗时 */
  @Value("${moyuyo.presale.deposit-refund-batch-size:100}")
  private int depositRefundBatchSize;

  /**
   * 尾款逾期退定金：默认 10 分钟一次。
   * <p>
   * fixedDelay 保证上一次执行完再等待，避免积压。
   */
  @Scheduled(fixedDelayString = "${moyuyo.presale.deposit-refund-fixed-delay-ms:600000}",
      initialDelay = 60000)
  public void refundExpiredDeposits() {
    if (!depositRefundEnabled) {
      return;
    }
    try {
      LocalDateTime now = LocalDateTime.now();
      // 只按"尾款期已结束"筛选，不再限定 presale_type=DEPOSIT：
      // 后台允许修改预售类型，若改为 FULL 后仍按类型过滤，历史定金单将永远得不到处理
      List<ProductPresaleEntity> presales = productPresaleMapper.selectList(
          new LambdaQueryWrapper<ProductPresaleEntity>()
              .isNotNull(ProductPresaleEntity::getBalanceEnd)
              .lt(ProductPresaleEntity::getBalanceEnd, now)
              .last("LIMIT 200"));
      if (presales.isEmpty()) {
        return;
      }
      List<Long> presaleIds = presales.stream().map(ProductPresaleEntity::getId).toList();
      // 只捞"定金已支付"的订单：REFUNDED / CANCELLED 由状态过滤排除，重复执行天然幂等
      List<OrderEntity> deposits = orderMapper.selectList(
          new LambdaQueryWrapper<OrderEntity>()
              .in(OrderEntity::getPresaleId, presaleIds)
              .eq(OrderEntity::getOrderType, "PRESALE")
              .eq(OrderEntity::getPresaleStage, "DEPOSIT")
              .eq(OrderEntity::getStatus, "PAID")
              .eq(OrderEntity::getDeleteStatus, 0)
              .orderByAsc(OrderEntity::getId)
              .last("LIMIT " + Math.max(1, depositRefundBatchSize)));
      if (deposits.isEmpty()) {
        return;
      }
      int success = 0;
      for (OrderEntity deposit : deposits) {
        try {
          if (orderService.handleExpiredDepositOrder(deposit.getId(), "尾款期结束仍未支付尾款")) {
            success++;
          }
        } catch (Exception e) {
          // 单笔失败不影响其他订单；渠道退款失败时事务回滚，订单保持已支付，下次任务重试
          log.error("[presale] 尾款逾期退定金失败 orderNo={}, reason={}",
              deposit.getOrderNo(), e.getMessage());
        }
      }
      if (success > 0) {
        log.info("[presale] 尾款逾期处理完成：成功 {}/{} 笔", success, deposits.size());
      }
    } catch (Exception e) {
      log.error("[presale] 尾款逾期退定金任务异常", e);
    }
  }

  /** 预售状态刷新：默认 5 分钟一次 */
  @Scheduled(fixedDelayString = "${moyuyo.presale.status-refresh-fixed-delay-ms:300000}",
      initialDelay = 45000)
  public void refreshPresaleStatus() {
    try {
      int changed = productService.refreshPresaleStatuses();
      if (changed > 0) {
        log.info("[presale] 预售状态刷新 {} 条", changed);
      }
    } catch (Exception e) {
      log.error("[presale] 预售状态刷新任务异常", e);
    }
  }
}
