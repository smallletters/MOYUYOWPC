package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货 N 天后，未被用户主动确认收货的订单 → 系统自动确认收货
 * <p>
 * 设计要点：
 * <ul>
 *   <li>天数优先读取 mo_system_config.config_key='auto_confirm'，读失败再退回 moyuyo.order.auto-confirm-days（默认 7 天）</li>
 *   <li>筛选条件：status=SHIPPED 且 deliverTime 非空且早于阈值；排除逻辑删除的订单</li>
 *   <li>更新采用条件更新（WHERE status=SHIPPED）保证幂等，并发场景不会错误覆盖</li>
 *   <li>同步写入 LogisticsEntity.receivedAt，避免物流表与订单表脱节（和 LogisticsServiceImpl.confirmReceived 一致）</li>
 *   <li>扫描上限 batchSize（默认 200），超过会在下次扫描再处理</li>
 *   <li>总开关 moyuyo.order.auto-confirm-enabled，dev 环境可关</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoConfirmReceiveJob {

  private final OrderMapper orderMapper;
  private final LogisticsMapper logisticsMapper;
  private final SystemConfigMapper systemConfigMapper;

  @Value("${moyuyo.order.auto-confirm-enabled:true}")
  private boolean enabled;

  /** 默认天数：与 seed 中 auto_confirm=7 保持一致，双保险 */
  @Value("${moyuyo.order.auto-confirm-days:7}")
  private int defaultAutoConfirmDays;

  @Value("${moyuyo.order.auto-confirm-batch-size:200}")
  private int batchSize;

  /**
   * 每天 02:00 执行一次，尽量避开用户流量高峰。
   * cron 表达式：秒 分 时 日 月 周
   */
  @Scheduled(cron = "${moyuyo.order.auto-confirm-cron:0 0 2 * * ?}")
  public void autoConfirmReceived() {
    if (!enabled) {
      return;
    }
    try {
      int days = resolveAutoConfirmDays();
      LocalDateTime threshold = LocalDateTime.now().minusDays(days);

      // 仅扫 SHIPPED 且 deliverTime 早于阈值、未逻辑删除的订单
      List<OrderEntity> expired = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
          .eq(OrderEntity::getStatus, OrderStatusEnum.SHIPPED.name())
          .eq(OrderEntity::getDeleteStatus, 0)
          .isNotNull(OrderEntity::getDeliverTime)
          .lt(OrderEntity::getDeliverTime, threshold)
          .last("LIMIT " + Math.max(1, batchSize)));
      if (expired.isEmpty()) {
        return;
      }
      log.info("[auto-confirm] 扫描到 {} 笔发货超过 {} 天仍未确认收货的订单，开始自动确认",
        expired.size(), days);
      int success = 0;
      for (OrderEntity order : expired) {
        try {
          LocalDateTime now = LocalDateTime.now();
          // 条件更新物流 receivedAt（只在物流记录存在且 receivedAt 为空时才写）
          logisticsMapper.update(null,
            new LambdaUpdateWrapper<LogisticsEntity>()
              .eq(LogisticsEntity::getOrderId, order.getId())
              .isNull(LogisticsEntity::getReceivedAt)
              .set(LogisticsEntity::getReceivedAt, now));
          // 条件更新订单：只有仍停在 SHIPPED 的才真正升级到 RECEIVED
          int updated = orderMapper.update(null,
            new LambdaUpdateWrapper<OrderEntity>()
              .eq(OrderEntity::getId, order.getId())
              .eq(OrderEntity::getStatus, OrderStatusEnum.SHIPPED.name())
              .set(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
              .set(OrderEntity::getReceivedTime, now));
          if (updated > 0) {
            success++;
          }
        } catch (Exception e) {
          log.warn("[auto-confirm] 自动确认收货失败 orderId={}, reason={}",
            order.getId(), e.getMessage());
        }
      }
      log.info("[auto-confirm] 自动确认收货完成：成功 {} / 总 {} 笔", success, expired.size());
    } catch (Exception e) {
      log.error("[auto-confirm] 定时任务异常", e);
    }
  }

  /** 解析自动确认收货天数：DB(mo_system_config.auto_confirm) → application 配置 → 默认 7 */
  private int resolveAutoConfirmDays() {
    try {
      SystemConfigEntity cfg = systemConfigMapper.selectOne(
        new LambdaQueryWrapper<SystemConfigEntity>()
          .eq(SystemConfigEntity::getConfigKey, "auto_confirm"));
      if (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
        // SystemConfig.vue 里把 autoConfirm 当做 boolean 渲染过（历史 UI 遗留），会写 'true'/'false'
        // 对布尔字面量回退到默认天数；合法数字才采纳
        String raw = cfg.getConfigValue().trim();
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
          return defaultAutoConfirmDays;
        }
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
          return defaultAutoConfirmDays;
        }
        return parsed;
      }
    } catch (Exception e) {
      log.warn("[auto-confirm] 读取 auto_confirm 系统配置失败，使用默认值 {}: {}",
        defaultAutoConfirmDays, e.getMessage());
    }
    return defaultAutoConfirmDays;
  }
}
