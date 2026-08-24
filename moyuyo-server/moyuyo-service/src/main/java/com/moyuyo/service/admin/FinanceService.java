package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 财务管理服务
 */
public interface FinanceService {

  /**
   * 获取财务概览数据
   */
  Map<String, Object> getFinanceOverview();

  /**
   * 获取结算列表
   */
  List<Map<String, Object>> getSettlementList();

  /**
   * 获取退款原因分布（统计前 5 名），用于财务概览辅助分析
   */
  List<Map<String, Object>> getRefundReasonDistribution();

  /**
   * 获取最近 6 个月的 GMV / 退款额趋势（按月份倒序），用于趋势展示
   */
  List<Map<String, Object>> getMonthlyTrend();
}
