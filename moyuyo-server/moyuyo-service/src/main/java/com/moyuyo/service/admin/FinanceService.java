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
}
