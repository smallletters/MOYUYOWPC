package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台风险告警服务
 */
public interface AdminRiskAlertService {

  /**
   * 风险规则列表
   */
  List<Map<String, Object>> listConfigs();

  /**
   * 创建风险规则
   */
  void createConfig(Map<String, Object> data);

  /**
   * 更新风险规则
   */
  void updateConfig(Map<String, Object> data);

  /**
   * 删除风险规则
   */
  void deleteConfig(Long id);

  /**
   * 风险事件历史
   */
  Map<String, Object> listHistory(int page, int size);
}
