package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.RiskRuleEntity;

import java.util.List;
import java.util.Map;

/**
 * 管理后台风控服务
 */
public interface AdminRiskService {

  /**
   * 规则列表（分页）
   */
  Page<Map<String, Object>> listRules(int page, int size);

  /**
   * 创建规则
   */
  void createRule(RiskRuleEntity entity);

  /**
   * 更新规则
   */
  void updateRule(RiskRuleEntity entity);

  /**
   * 删除规则
   */
  void deleteRule(Long id);

  /**
   * 启用/禁用规则
   */
  void toggleRule(Long id, Boolean enabled);

  /**
   * 风控事件列表（分页、筛选）
   */
  Page<Map<String, Object>> listEvents(String level, String status, int page, int size);

  /**
   * 风控事件统计
   */
  Map<String, Object> eventStats();
}
