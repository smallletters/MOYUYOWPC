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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
      // 获取本月时间范围
      LocalDate now = LocalDate.now();
      LocalDateTime monthStart = LocalDateTime.of(now.withDayOfMonth(1), LocalTime.MIN);
      LocalDateTime monthEnd = LocalDateTime.of(now.withDayOfMonth(now.lengthOfMonth()), LocalTime.MAX);

      // 本月已支付订单的GMV（排除取消和退款完成的订单）
      List<String> paidStatuses = Arrays.asList("PAID", "PENDING_SHIP", "SHIPPED",
        "PENDING_RECEIVE", "RECEIVED", "REFUNDING", "REFUNDED", "COMPLETED");
      List<OrderEntity> monthOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
          .in(OrderEntity::getStatus, paidStatuses)
          .ge(OrderEntity::getCreateTime, monthStart)
          .le(OrderEntity::getCreateTime, monthEnd));

      // 计算本月GMV
      BigDecimal monthGmv = monthOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 本月退款总额
      List<RefundEntity> completedRefunds = refundMapper.selectList(
        new LambdaQueryWrapper<RefundEntity>()
          .eq(RefundEntity::getStatus, "COMPLETED")
          .ge(RefundEntity::getCreateTime, monthStart)
          .le(RefundEntity::getCreateTime, monthEnd));

      BigDecimal refundAmount = completedRefunds.stream()
        .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 实际收入 = GMV - 退款
      BigDecimal actualIncome = monthGmv.subtract(refundAmount);

      // 待结算金额 = 已支付但未完成收货的订单金额
      List<String> pendingStatuses = Arrays.asList("PAID", "PENDING_SHIP", "SHIPPED", "PENDING_RECEIVE");
      List<OrderEntity> pendingOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
          .in(OrderEntity::getStatus, pendingStatuses));
      BigDecimal pendingSettlement = pendingOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 支付渠道分布（从 mo_payment 表按渠道统计本月金额）
      List<PaymentEntity> monthPayments = paymentMapper.selectList(
        new LambdaQueryWrapper<PaymentEntity>()
          .eq(PaymentEntity::getStatus, "SUCCESS")
          .ge(PaymentEntity::getCreateTime, monthStart)
          .le(PaymentEntity::getCreateTime, monthEnd));

      // 按渠道分组汇总
      Map<String, BigDecimal> channelAmounts = monthPayments.stream()
        .filter(p -> p.getPayChannel() != null)
        .collect(Collectors.groupingBy(
          PaymentEntity::getPayChannel,
          Collectors.reducing(BigDecimal.ZERO,
            p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
            BigDecimal::add)));

      BigDecimal totalChannelAmount = channelAmounts.values().stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      List<Map<String, Object>> channelDist = new ArrayList<>();
      for (Map.Entry<String, BigDecimal> entry : channelAmounts.entrySet()) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("channel", entry.getKey());
        channel.put("amount", entry.getValue());

        // 计算占比（百分比，保留一位小数）
        double ratio = totalChannelAmount.compareTo(BigDecimal.ZERO) > 0
          ? entry.getValue().multiply(BigDecimal.valueOf(100))
            .divide(totalChannelAmount, 1, RoundingMode.HALF_UP)
            .doubleValue()
          : 0.0;
        channel.put("ratio", ratio);
        channelDist.add(channel);
      }

      // 待处理问题数（待处理的退款申请数）
      Long pendingIssues = refundMapper.selectCount(
        new LambdaQueryWrapper<RefundEntity>()
          .eq(RefundEntity::getStatus, "PENDING"));

      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("monthGmv", monthGmv);
      overview.put("actualIncome", actualIncome);
      overview.put("pendingSettlement", pendingSettlement);
      overview.put("refundAmount", refundAmount);
      overview.put("channelDistribution", channelDist);
      overview.put("pendingIssues", pendingIssues);
      return overview;
    } catch (Exception e) {
      // 异常时返回空概览
      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("monthGmv", BigDecimal.ZERO);
      overview.put("actualIncome", BigDecimal.ZERO);
      overview.put("pendingSettlement", BigDecimal.ZERO);
      overview.put("refundAmount", BigDecimal.ZERO);
      overview.put("channelDistribution", Collections.emptyList());
      overview.put("pendingIssues", 0);
      return overview;
    }
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
}
