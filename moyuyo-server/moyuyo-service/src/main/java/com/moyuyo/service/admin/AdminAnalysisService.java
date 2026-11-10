package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台数据分析服务
 */
public interface AdminAnalysisService {

  /**
   * 漏斗分析（浏览→加购→下单→支付→完成）
   */
  List<Map<String, Object>> funnel();

  /**
   * RFM 分析
   */
  List<Map<String, Object>> rfm();

  /**
   * 搜索统计
   */
  Map<String, Object> searchStats();

  /**
   * 零结果搜索词 TOP N（result_count = 0 的关键词聚合）
   */
  List<Map<String, Object>> zeroResultKeywords(int limit);

  /**
   * 流量统计
   */
  Map<String, Object> trafficStats();

  /**
   * 流失分析（最近 days 天）
   * 返回 map：{ churnRate, churnStep, churnReasons:[{reason, orderCount, lostAmount}, ...] }
   */
  Map<String, Object> churn(int days);

  /**
   * 复购率分析（最近 days 天，复购定义：在窗口内订单数≥2）
   * 返回 map：{ repurchaseRate, trend:与上一周期对比的百分点变化 }
   */
  Map<String, Object> repurchase(int days);

  /**
   * 用户画像（新老用户占比 + 设备分布）
   */
  Map<String, Object> userProfile();

  /**
   * 渠道访问统计（近 30 天，按渠道聚合独立会话数）
   */
  List<Map<String, Object>> channelStats();
}
