package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.RiskEventEntity;
import com.moyuyo.dao.admin.entity.RiskRuleEntity;
import com.moyuyo.dao.admin.mapper.RiskEventMapper;
import com.moyuyo.dao.admin.mapper.RiskRuleMapper;
import com.moyuyo.service.admin.AdminRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台风控服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminRiskServiceImpl implements AdminRiskService {

  private final RiskRuleMapper riskRuleMapper;
  private final RiskEventMapper riskEventMapper;

  @Override
  public Page<Map<String, Object>> listRules(int page, int size) {
    LambdaQueryWrapper<RiskRuleEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(RiskRuleEntity::getPriority);

    Page<RiskRuleEntity> entityPage = riskRuleMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("ruleCode", e.getRuleCode());
      item.put("ruleName", e.getRuleName());
      item.put("ruleType", e.getRuleType());
      item.put("conditionJson", e.getConditionJson());
      item.put("action", e.getAction());
      item.put("priority", e.getPriority());
      item.put("enabled", e.getEnabled());
      item.put("description", e.getDescription());
      item.put("createTime", e.getCreateTime());
      item.put("updateTime", e.getUpdateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public void createRule(RiskRuleEntity entity) {
    riskRuleMapper.insert(entity);
  }

  @Override
  public void updateRule(RiskRuleEntity entity) {
    riskRuleMapper.updateById(entity);
  }

  @Override
  public void deleteRule(Long id) {
    riskRuleMapper.deleteById(id);
  }

  @Override
  public void toggleRule(Long id, Boolean enabled) {
    RiskRuleEntity entity = riskRuleMapper.selectById(id);
    if (entity != null) {
      entity.setEnabled(enabled);
      riskRuleMapper.updateById(entity);
    }
  }

  @Override
  public Page<Map<String, Object>> listEvents(String level, String status, int page, int size) {
    LambdaQueryWrapper<RiskEventEntity> wrapper = new LambdaQueryWrapper<>();
    if (level != null && !level.isEmpty()) {
      wrapper.eq(RiskEventEntity::getRiskLevel, level);
    }
    if (status != null && !status.isEmpty()) {
      wrapper.eq(RiskEventEntity::getStatus, status);
    }
    wrapper.orderByDesc(RiskEventEntity::getCreateTime);

    Page<RiskEventEntity> entityPage = riskEventMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("ruleId", e.getRuleId());
      item.put("ruleCode", e.getRuleCode());
      item.put("userId", e.getUserId());
      item.put("eventType", e.getEventType());
      item.put("businessId", e.getBusinessId());
      item.put("riskLevel", e.getRiskLevel());
      item.put("actionTaken", e.getActionTaken());
      item.put("detailJson", e.getDetailJson());
      item.put("status", e.getStatus());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Map<String, Object> eventStats() {
    List<RiskEventEntity> all = riskEventMapper.selectList(null);
    Map<String, Object> result = new LinkedHashMap<>();

    // 按风险等级统计
    Map<String, Long> levelStats = all.stream()
        .collect(Collectors.groupingBy(RiskEventEntity::getRiskLevel, Collectors.counting()));
    result.put("levelStats", levelStats);

    // 按状态统计
    Map<String, Long> statusStats = all.stream()
        .collect(Collectors.groupingBy(RiskEventEntity::getStatus, Collectors.counting()));
    result.put("statusStats", statusStats);

    result.put("totalCount", all.size());
    return result;
  }
}
