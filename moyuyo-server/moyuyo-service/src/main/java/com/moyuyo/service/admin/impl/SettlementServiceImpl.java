package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结算管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

  private final OrderMapper orderMapper;

  /** 已支付状态的订单列表，用于结算聚合 */
  private static final List<String> PAID_STATUSES = List.of("PAID", "SHIPPED", "COMPLETED", "RECEIVED");

  @Override
  public List<Map<String, Object>> listAll(String status) {
    // 查询所有已支付订单，可按订单状态筛选
    QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
    wrapper.in("status", PAID_STATUSES);
    if (status != null && !status.isEmpty()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("create_time");

    List<OrderEntity> orders = orderMapper.selectList(wrapper);

    // 按日期分组聚合：计算每天订单总额和订单数
    Map<LocalDate, List<OrderEntity>> grouped = orders.stream()
        .collect(Collectors.groupingBy(
            o -> o.getCreateTime().toLocalDate(),
            LinkedHashMap::new,
            Collectors.toList()
        ));

    List<Map<String, Object>> list = new ArrayList<>();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter noFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    for (Map.Entry<LocalDate, List<OrderEntity>> entry : grouped.entrySet()) {
      LocalDate date = entry.getKey();
      List<OrderEntity> dayOrders = entry.getValue();

      // 计算该日订单总金额
      BigDecimal totalAmount = dayOrders.stream()
          .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("settlementNo", "SET-" + date.format(noFormatter));
      item.put("date", date.format(dateFormatter));
      item.put("amount", totalAmount);
      item.put("orderCount", dayOrders.size());
      item.put("status", "PENDING");
      list.add(item);
    }

    return list;
  }

  @Override
  public Map<String, Object> getDetail(Long id) {
    // 查询所有已支付订单进行统计
    QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
    wrapper.in("status", PAID_STATUSES);
    List<OrderEntity> orders = orderMapper.selectList(wrapper);

    // 计算总金额、佣金（5%）和实际结算金额
    BigDecimal totalAmount = orders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal commission = totalAmount.multiply(new BigDecimal("0.05"))
        .setScale(2, RoundingMode.HALF_UP);
    BigDecimal actualAmount = totalAmount.subtract(commission);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("settlementNo", "SET-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    detail.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    detail.put("totalAmount", totalAmount);
    detail.put("commission", commission);
    detail.put("actualAmount", actualAmount);
    detail.put("orderCount", orders.size());
    detail.put("status", "PENDING");
    detail.put("completedAt", LocalDateTime.now());
    return detail;
  }

  @Override
  public void settle(Long id) {
    // 查询所有已支付的订单，更新状态为已结算
    UpdateWrapper<OrderEntity> wrapper = new UpdateWrapper<>();
    wrapper.in("status", PAID_STATUSES);
    wrapper.set("status", "SETTLED");
    orderMapper.update(null, wrapper);
    log.info("结算完成，更新订单状态为 SETTLED");
  }

  @Override
  public void confirm(Long id) {
    // 确认结算状态
    log.info("结算已确认，id={}", id);
  }
}
