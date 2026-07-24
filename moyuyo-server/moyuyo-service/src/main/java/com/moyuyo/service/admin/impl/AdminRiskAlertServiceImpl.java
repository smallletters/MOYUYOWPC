package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.RiskAlertConfigEntity;
import com.moyuyo.dao.admin.entity.RiskEventEntity;
import com.moyuyo.dao.admin.mapper.RiskAlertConfigMapper;
import com.moyuyo.dao.admin.mapper.RiskEventMapper;
import com.moyuyo.service.admin.AdminRiskAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险告警服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminRiskAlertServiceImpl implements AdminRiskAlertService {

  private final RiskAlertConfigMapper riskAlertConfigMapper;
  private final RiskEventMapper riskEventMapper;

  @Override
  public List<Map<String, Object>> listConfigs() {
    List<RiskAlertConfigEntity> configs = riskAlertConfigMapper.selectList(
        new LambdaQueryWrapper<RiskAlertConfigEntity>()
            .orderByDesc(RiskAlertConfigEntity::getCreateTime)
    );
    List<Map<String, Object>> list = new ArrayList<>();
    for (RiskAlertConfigEntity config : configs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", config.getId());
      item.put("name", config.getAlertName());
      item.put("type", config.getAlertType());
      item.put("threshold", config.getThreshold());
      item.put("notifyChannels", config.getNotifyChannels());
      item.put("notifyUsers", config.getNotifyUsers());
      item.put("enabled", "ENABLED".equals(config.getStatus()));
      item.put("createTime", config.getCreateTime());
      list.add(item);
    }
    return list;
  }

  @Override
  public void createConfig(Map<String, Object> data) {
    RiskAlertConfigEntity entity = new RiskAlertConfigEntity();
    if (data.get("name") != null) entity.setAlertName((String) data.get("name"));
    if (data.get("type") != null) entity.setAlertType((String) data.get("type"));
    if (data.get("threshold") != null) entity.setThreshold(Integer.valueOf(data.get("threshold").toString()));
    if (data.get("notifyChannels") != null) entity.setNotifyChannels((String) data.get("notifyChannels"));
    if (data.get("notifyUsers") != null) entity.setNotifyUsers((String) data.get("notifyUsers"));
    entity.setStatus("ENABLED");
    riskAlertConfigMapper.insert(entity);
  }

  @Override
  public void updateConfig(Map<String, Object> data) {
    if (data.get("id") == null) return;
    RiskAlertConfigEntity entity = riskAlertConfigMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setAlertName((String) data.get("name"));
    if (data.get("type") != null) entity.setAlertType((String) data.get("type"));
    if (data.get("threshold") != null) entity.setThreshold(Integer.valueOf(data.get("threshold").toString()));
    if (data.get("notifyChannels") != null) entity.setNotifyChannels((String) data.get("notifyChannels"));
    if (data.get("notifyUsers") != null) entity.setNotifyUsers((String) data.get("notifyUsers"));
    if (data.get("enabled") != null) {
      entity.setStatus(Boolean.TRUE.equals(data.get("enabled")) ? "ENABLED" : "DISABLED");
    }
    riskAlertConfigMapper.updateById(entity);
  }

  @Override
  public void deleteConfig(Long id) {
    riskAlertConfigMapper.deleteById(id);
  }

  @Override
  public Map<String, Object> listHistory(int page, int size) {
    LambdaQueryWrapper<RiskEventEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(RiskEventEntity::getCreateTime);

    Page<RiskEventEntity> pageObj = riskEventMapper.selectPage(new Page<>(page, size), wrapper);

    List<Map<String, Object>> list = new ArrayList<>();
    for (RiskEventEntity event : pageObj.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", event.getId());
      item.put("ruleId", event.getRuleId());
      item.put("type", event.getEventType());
      item.put("level", event.getRiskLevel());
      item.put("message", event.getDetailJson());
      item.put("handled", "RESOLVED".equals(event.getStatus()));
      item.put("createTime", event.getCreateTime());
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }
}
