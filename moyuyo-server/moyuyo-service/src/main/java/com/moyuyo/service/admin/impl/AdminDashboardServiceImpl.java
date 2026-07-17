package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台仪表盘服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

  @Override
  public Map<String, Object> getDashboardStats() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("todayGmv", 12580);
    data.put("todayOrders", 156);
    data.put("activeUsers", 2341);
    data.put("conversionRate", 3.8);
    data.put("gmvTrend", 12.5);
    data.put("ordersTrend", 8.3);
    data.put("usersTrend", -2.1);
    data.put("rateTrend", 0.5);
    data.put("pendingShip", 5);
    data.put("pendingReview", 3);
    data.put("pendingRefund", 1);
    return data;
  }

  @Override
  public List<Map<String, Object>> getRecentOrders() {
    List<Map<String, Object>> list = new ArrayList<>();
    String[][] data = {
        {"MOY20260708012", "宠物洗护套装", "129.00", "PENDING_SHIP"},
        {"MOY20260708011", "舒适胸背带-M", "49.00", "SHIPPED"},
        {"MOY20260708010", "益智玩具球", "35.00", "COMPLETED"},
        {"MOY20260708009", "宠物航空箱-L", "299.00", "PENDING_SHIP"},
        {"MOY20260708008", "鹿角磨牙棒", "22.00", "COMPLETED"},
    };
    for (String[] row : data) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("orderNo", row[0]);
      item.put("productName", row[1]);
      item.put("amount", new BigDecimal(row[2]));
      item.put("status", row[3]);
      list.add(item);
    }
    return list;
  }

  @Override
  public List<Map<String, Object>> getSalesTrend() {
    List<Map<String, Object>> list = new ArrayList<>();
    String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    double[] values = {9800, 11200, 10500, 12300, 13600, 11800, 10800};
    for (int i = 0; i < days.length; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("day", days[i]);
      item.put("value", values[i]);
      list.add(item);
    }
    return list;
  }
}
