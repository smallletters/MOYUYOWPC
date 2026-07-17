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
   * 流量统计
   */
  Map<String, Object> trafficStats();
}
