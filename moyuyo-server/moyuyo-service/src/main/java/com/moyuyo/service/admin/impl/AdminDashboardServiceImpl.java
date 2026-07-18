package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyuyo.dao.entity.*;
import com.moyuyo.dao.mapper.*;
import com.moyuyo.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 管理后台仪表盘服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

  private final OrderMapper orderMapper;
  private final UserMapper userMapper;
  private final OrderItemMapper orderItemMapper;
  private final RefundMapper refundMapper;
  private final ProductReviewMapper productReviewMapper;

  @Override
  public Map<String, Object> getDashboardStats() {
    // 获取当天开始时间（00:00:00）
    LocalDate today = LocalDate.now();

    // 1. 当天已支付订单的总金额
    List<Object> gmvResult = orderMapper.selectObjs(
        new QueryWrapper<OrderEntity>()
            .select("COALESCE(SUM(pay_amount), 0)")
            .ge("paid_at", today)
            .in("status", "COMPLETED", "PENDING_SHIP")
    );
    BigDecimal todayGmv = gmvResult == null || gmvResult.isEmpty() || gmvResult.get(0) == null
        ? BigDecimal.ZERO
        : new BigDecimal(gmvResult.get(0).toString());

    // 2. 当天订单总数（按创建时间）
    Long todayOrders = orderMapper.selectCount(
        new QueryWrapper<OrderEntity>()
            .ge("create_time", today)
    );

    // 3. 当天活跃用户数（最后登录时间在今天）
    Long activeUsers = userMapper.selectCount(
        new QueryWrapper<UserEntity>()
            .ge("last_login_time", today)
    );

    // 4. 转化率 = 当天订单数 / 活跃用户数 * 100
    double conversionRate = activeUsers > 0
        ? BigDecimal.valueOf(todayOrders)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(activeUsers), 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    // 5. 待发货订单
    Long pendingShip = orderMapper.selectCount(
        new QueryWrapper<OrderEntity>()
            .eq("status", "PENDING_SHIP")
    );

    // 6. 待审核评价
    Long pendingReview = productReviewMapper.selectCount(
        new QueryWrapper<ProductReviewEntity>()
            .eq("status", "PENDING")
    );

    // 7. 待处理退款
    Long pendingRefund = refundMapper.selectCount(
        new QueryWrapper<RefundEntity>()
            .in("status", "PENDING", "REFUNDING")
    );

    // 8. 计算趋势数据（对比昨天）
    LocalDate yesterday = today.minusDays(1);
    List<Object> yesterdayGmvResult = orderMapper.selectObjs(
        new QueryWrapper<OrderEntity>()
            .select("COALESCE(SUM(pay_amount), 0)")
            .ge("paid_at", yesterday)
            .lt("paid_at", today)
            .in("status", "COMPLETED", "PENDING_SHIP")
    );
    BigDecimal yesterdayGmv = yesterdayGmvResult == null || yesterdayGmvResult.isEmpty() || yesterdayGmvResult.get(0) == null
        ? BigDecimal.ZERO
        : new BigDecimal(yesterdayGmvResult.get(0).toString());

    Long yesterdayOrders = orderMapper.selectCount(
        new QueryWrapper<OrderEntity>()
            .ge("create_time", yesterday)
            .lt("create_time", today)
    );

    Long yesterdayUsers = userMapper.selectCount(
        new QueryWrapper<UserEntity>()
            .ge("last_login_time", yesterday)
            .lt("last_login_time", today)
    );

    double yesterdayRate = yesterdayUsers > 0
        ? BigDecimal.valueOf(yesterdayOrders)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(yesterdayUsers), 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    double gmvTrend = yesterdayGmv.compareTo(BigDecimal.ZERO) > 0
        ? BigDecimal.valueOf(todayGmv.subtract(yesterdayGmv).doubleValue())
            .multiply(BigDecimal.valueOf(100))
            .divide(yesterdayGmv, 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    double ordersTrend = yesterdayOrders > 0
        ? BigDecimal.valueOf(todayOrders - yesterdayOrders)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(yesterdayOrders), 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    double usersTrend = yesterdayUsers > 0
        ? BigDecimal.valueOf(activeUsers - yesterdayUsers)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(yesterdayUsers), 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    double rateTrend = yesterdayRate > 0
        ? BigDecimal.valueOf(conversionRate - yesterdayRate)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(yesterdayRate), 1, RoundingMode.HALF_UP)
            .doubleValue()
        : 0.0;

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("todayGmv", todayGmv);
    data.put("todayOrders", todayOrders);
    data.put("activeUsers", activeUsers);
    data.put("conversionRate", conversionRate);
    data.put("gmvTrend", gmvTrend);
    data.put("ordersTrend", ordersTrend);
    data.put("usersTrend", usersTrend);
    data.put("rateTrend", rateTrend);
    data.put("pendingShip", pendingShip);
    data.put("pendingReview", pendingReview);
    data.put("pendingRefund", pendingRefund);
    return data;
  }

  @Override
  public List<Map<String, Object>> getRecentOrders() {
    // 查询最近5条订单
    List<OrderEntity> orders = orderMapper.selectList(
        new QueryWrapper<OrderEntity>()
            .orderByDesc("create_time")
            .last("LIMIT 5")
    );

    List<Map<String, Object>> list = new ArrayList<>();
    for (OrderEntity order : orders) {
      // 获取订单的商品名称（取第一个商品，多于1件时加"等N件商品"）
      List<OrderItemEntity> items = orderItemMapper.selectByOrderId(order.getId());
      String productName;
      if (items == null || items.isEmpty()) {
        productName = "";
      } else {
        OrderItemEntity firstItem = items.get(0);
        productName = firstItem != null && firstItem.getProductName() != null
            ? firstItem.getProductName() : "";
        if (items.size() > 1) {
          productName += " 等" + items.size() + "件商品";
        }
      }

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("orderNo", order.getOrderNo());
      item.put("productName", productName);
      item.put("amount", order.getPayAmount());
      item.put("status", order.getStatus());
      list.add(item);
    }
    return list;
  }

  @Override
  public List<Map<String, Object>> getSalesTrend() {
    // 查询最近7天每日销售额
    List<Map<String, Object>> list = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int i = 6; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      LocalDate nextDay = date.plusDays(1);

      List<Object> result = orderMapper.selectObjs(
          new QueryWrapper<OrderEntity>()
              .select("COALESCE(SUM(pay_amount), 0)")
              .ge("paid_at", date)
              .lt("paid_at", nextDay)
              .in("status", "COMPLETED", "PENDING_SHIP")
      );
      BigDecimal value = result == null || result.isEmpty() || result.get(0) == null
          ? BigDecimal.ZERO
          : new BigDecimal(result.get(0).toString());

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("day", date.format(DateTimeFormatter.ofPattern("MM-dd")));
      item.put("value", value);
      list.add(item);
    }
    return list;
  }
}
