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

  /**
   * 结算管理页面 - 按支付渠道聚合最近结算（Payout 汇总）
   * 每项 channel/count/amount/status/note，全部基于 mo_settlement 真实数据
   */
  List<Map<String, Object>> getPayoutChannels();

  /**
   * 结算管理页面 - 对账异常告警
   * 1) ABNORMAL 状态的 settlement；2) PENDING 状态的 refund
   */
  List<Map<String, Object>> getReconcileAlerts();

  /**
   * 结算管理页面 - 退款 KPI（总额 + 笔数）
   * 返回 totalAmount / totalCount / pendingCount / completedCount
   */
  Map<String, Object> getRefundKpi();
}
