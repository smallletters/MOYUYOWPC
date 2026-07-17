package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台仪表盘服务
 */
public interface AdminDashboardService {

  /**
   * 获取仪表盘统计数据
   */
  Map<String, Object> getDashboardStats();

  /**
   * 获取最近订单列表
   */
  List<Map<String, Object>> getRecentOrders();

  /**
   * 获取7天销售趋势
   */
  List<Map<String, Object>> getSalesTrend();
}
