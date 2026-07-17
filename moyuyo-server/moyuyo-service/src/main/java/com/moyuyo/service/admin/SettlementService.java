package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 结算管理服务
 */
public interface SettlementService {

  /**
   * 结算列表
   */
  List<Map<String, Object>> listAll(String status);

  /**
   * 结算详情
   */
  Map<String, Object> getDetail(Long id);

  /**
   * 执行结算
   */
  void settle(Long id);

  /**
   * 确认结算
   */
  void confirm(Long id);
}
