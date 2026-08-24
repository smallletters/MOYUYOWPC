package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.SettlementEntity;
import com.moyuyo.dao.admin.mapper.SettlementMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.service.admin.FinanceService;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import static com.moyuyo.common.enums.PaymentStatusEnum.SUCCESS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 财务管理服务实现
 */
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

  private final OrderMapper orderMapper;
  private final PaymentMapper paymentMapper;
  private final RefundMapper refundMapper;
  private final SettlementMapper settlementMapper;

  @Override
  public Map<String, Object> getFinanceOverview() {
    try {
      LocalDateTime[] monthRange = getMonthRange();
      LocalDateTime monthStart = monthRange[0];
      LocalDateTime monthEnd = monthRange[1];

      BigDecimal monthGmv = computeMonthGmv(monthStart, monthEnd);
      BigDecimal refundAmount = computeRefundAmount(monthStart, monthEnd);
      BigDecimal actualIncome = monthGmv.subtract(refundAmount);
      BigDecimal pendingSettlement = computePendingSettlement();
      List<Map<String, Object>> channelDist = computeChannelDistribution(monthStart, monthEnd);
      Long pendingIssues = computePendingIssues();

      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("monthGmv", monthGmv);
      overview.put("actualIncome", actualIncome);
      overview.put("pendingSettlement", pendingSettlement);
      overview.put("refundAmount", refundAmount);
      overview.put("channelDistribution", channelDist);
      overview.put("pendingIssues", pendingIssues);
      return overview;
    } catch (Exception e) {
      return emptyOverview();
    }
  }

  /** 计算本月时间范围 */
  private LocalDateTime[] getMonthRange() {
    LocalDate now = LocalDate.now();
    return new LocalDateTime[]{
      LocalDateTime.of(now.withDayOfMonth(1), LocalTime.MIN),
      LocalDateTime.of(now.withDayOfMonth(now.lengthOfMonth()), LocalTime.MAX)
    };
  }

  /** 计算本月GMV（已支付订单金额总和） */
  private BigDecimal computeMonthGmv(LocalDateTime monthStart, LocalDateTime monthEnd) {
    List<String> paidStatuses = Arrays.asList(PAID.name(), PENDING_SHIP.name(), SHIPPED.name(),
      "PENDING_RECEIVE", RECEIVED.name(), REFUNDING.name(), REFUNDED.name(), COMPLETED.name());
    List<OrderEntity> monthOrders = orderMapper.selectList(
      new LambdaQueryWrapper<OrderEntity>()
        .in(OrderEntity::getStatus, paidStatuses)
        .ge(OrderEntity::getCreateTime, monthStart)
        .le(OrderEntity::getCreateTime, monthEnd));
    return monthOrders.stream()
      .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 计算本月退款总额 */
  private BigDecimal computeRefundAmount(LocalDateTime monthStart, LocalDateTime monthEnd) {
    List<RefundEntity> completedRefunds = refundMapper.selectList(
      new LambdaQueryWrapper<RefundEntity>()
        .eq(RefundEntity::getStatus, "COMPLETED")
        .ge(RefundEntity::getCreateTime, monthStart)
        .le(RefundEntity::getCreateTime, monthEnd));
    return completedRefunds.stream()
      .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 计算待结算金额（已支付但未完成收货的订单金额） */
  private BigDecimal computePendingSettlement() {
    List<String> pendingStatuses = Arrays.asList(PAID.name(), PENDING_SHIP.name(), SHIPPED.name(), "PENDING_RECEIVE");
    List<OrderEntity> pendingOrders = orderMapper.selectList(
      new LambdaQueryWrapper<OrderEntity>().in(OrderEntity::getStatus, pendingStatuses));
    return pendingOrders.stream()
      .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 计算支付渠道分布 */
  private List<Map<String, Object>> computeChannelDistribution(LocalDateTime monthStart, LocalDateTime monthEnd) {
    List<PaymentEntity> monthPayments = paymentMapper.selectList(
      new LambdaQueryWrapper<PaymentEntity>()
        .eq(PaymentEntity::getStatus, SUCCESS.name())
        .ge(PaymentEntity::getCreateTime, monthStart)
        .le(PaymentEntity::getCreateTime, monthEnd));

    Map<String, BigDecimal> channelAmounts = monthPayments.stream()
      .filter(p -> p.getPayChannel() != null)
      .collect(Collectors.groupingBy(PaymentEntity::getPayChannel,
        Collectors.reducing(BigDecimal.ZERO,
          p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add)));

    BigDecimal totalChannelAmount = channelAmounts.values().stream()
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<Map<String, Object>> channelDist = new ArrayList<>();
    for (Map.Entry<String, BigDecimal> entry : channelAmounts.entrySet()) {
      Map<String, Object> channel = new LinkedHashMap<>();
      channel.put("channel", entry.getKey());
      channel.put("amount", entry.getValue());
      double ratio = totalChannelAmount.compareTo(BigDecimal.ZERO) > 0
        ? entry.getValue().multiply(BigDecimal.valueOf(100))
          .divide(totalChannelAmount, 1, RoundingMode.HALF_UP).doubleValue()
        : 0.0;
      channel.put("ratio", ratio);
      channelDist.add(channel);
    }
    return channelDist;
  }

  /** 计算待处理问题数（待处理的退款申请数） */
  private Long computePendingIssues() {
    return refundMapper.selectCount(
      new LambdaQueryWrapper<RefundEntity>().eq(RefundEntity::getStatus, "PENDING"));
  }

  /** 返回空财务概览 */
  private Map<String, Object> emptyOverview() {
    Map<String, Object> overview = new LinkedHashMap<>();
    overview.put("monthGmv", BigDecimal.ZERO);
    overview.put("actualIncome", BigDecimal.ZERO);
    overview.put("pendingSettlement", BigDecimal.ZERO);
    overview.put("refundAmount", BigDecimal.ZERO);
    overview.put("channelDistribution", Collections.emptyList());
    overview.put("pendingIssues", 0);
    return overview;
  }

  @Override
  public List<Map<String, Object>> getSettlementList() {
    try {
      // 从 mo_settlement 表查询结算列表
      List<SettlementEntity> settlements = settlementMapper.selectList(
        new LambdaQueryWrapper<SettlementEntity>()
          .orderByDesc(SettlementEntity::getCreateTime));

      List<Map<String, Object>> list = new ArrayList<>();
      for (SettlementEntity entity : settlements) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("settlementNo", entity.getSettlementNo());
        item.put("date", entity.getPeriod() != null ? entity.getPeriod()
          : (entity.getCreateTime() != null ? entity.getCreateTime().toLocalDate().toString() : ""));
        item.put("amount", entity.getAmount() != null
          ? BigDecimal.valueOf(entity.getAmount()) : BigDecimal.ZERO);
        item.put("status", entity.getStatus());
        list.add(item);
      }
      return list;
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 退款原因分布：按 reason 聚合统计数量，取前 5
   * reason 在 mo_refund 表中可能为 null（兼容历史数据），归类为 "未填写"
   */
  @Override
  public List<Map<String, Object>> getRefundReasonDistribution() {
    try {
      List<RefundEntity> refunds = refundMapper.selectList(
        new LambdaQueryWrapper<RefundEntity>()
          .orderByDesc(RefundEntity::getCreateTime));
      Map<String, Long> reasonCount = refunds.stream()
        .collect(Collectors.groupingBy(
          r -> (r.getReason() == null || r.getReason().isEmpty()) ? "未填写" : r.getReason(),
          LinkedHashMap::new,
          Collectors.counting()));
      List<Map<String, Object>> list = new ArrayList<>();
      reasonCount.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .limit(5)
        .forEach(e -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("reason", e.getKey());
          item.put("count", e.getValue());
          list.add(item);
        });
      return list;
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 最近 6 个月（包含本月）的 GMV 与退款额趋势。
   * 通过遍历近 6 个月的 1 号零点到下月 1 号零点的范围逐月累加。
   */
  @Override
  public List<Map<String, Object>> getMonthlyTrend() {
    try {
      List<Map<String, Object>> result = new ArrayList<>();
      LocalDate today = LocalDate.now();
      DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("yyyy-MM");

      for (int i = 5; i >= 0; i--) {
        LocalDate monthAnchor = today.minusMonths(i).withDayOfMonth(1);
        LocalDateTime start = LocalDateTime.of(monthAnchor, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(monthAnchor.plusMonths(1).minusDays(1), LocalTime.MAX);

        // 本月已支付订单 GMV
        List<String> paidStatuses = Arrays.asList(PAID.name(), PENDING_SHIP.name(), SHIPPED.name(),
          "PENDING_RECEIVE", RECEIVED.name(), REFUNDING.name(), REFUNDED.name(), COMPLETED.name());
        BigDecimal gmv = orderMapper.selectList(
          new LambdaQueryWrapper<OrderEntity>()
            .in(OrderEntity::getStatus, paidStatuses)
            .ge(OrderEntity::getCreateTime, start)
            .le(OrderEntity::getCreateTime, end)).stream()
          .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本月已完成退款总额
        BigDecimal refund = refundMapper.selectList(
          new LambdaQueryWrapper<RefundEntity>()
            .eq(RefundEntity::getStatus, "COMPLETED")
            .ge(RefundEntity::getCreateTime, start)
            .le(RefundEntity::getCreateTime, end)).stream()
          .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("month", monthAnchor.format(ymFmt));
        item.put("gmv", gmv);
        item.put("refund", refund);
        item.put("net", gmv.subtract(refund));
        result.add(item);
      }
      return result;
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
