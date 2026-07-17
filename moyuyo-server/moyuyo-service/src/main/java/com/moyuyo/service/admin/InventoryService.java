package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 库存管理服务
 */
public interface InventoryService {

  /**
   * 获取库存概览
   */
  Map<String, Object> getInventoryOverview();

  /**
   * 获取预警列表
   */
  List<Map<String, Object>> getAlertList();
}
