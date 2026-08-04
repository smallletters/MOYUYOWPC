package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * 将逗号分隔字符串 / List / JSON 数组字符串统一转为合法 JSON 数组字符串
   * （notify_channels / notify_users 列为 MySQL JSON 类型，纯字符串写入会触发 409）
   */
  private String toJsonArray(Object value) {
    if (value == null) return null;
    try {
      String s = value.toString();
      if (s.trim().startsWith("[")) {
        MAPPER.readTree(s); // 校验已是合法 JSON，原样保留
        return s;
      }
      if (value instanceof List) {
        return MAPPER.writeValueAsString(value);
      }
      // 逗号分隔字符串 → 拆分为数组后序列化
      String[] parts = s.split("[,，]");
      List<String> items = new ArrayList<>();
      for (String p : parts) {
        String t = p.trim();
        if (!t.isEmpty()) items.add(t);
      }
      return MAPPER.writeValueAsString(items);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 将 JSON 数组字符串还原为逗号分隔字符串（前端展示用）
   */
  private String fromJsonArray(String json) {
    if (json == null || json.isEmpty()) return null;
    try {
      JsonNode node = MAPPER.readTree(json);
      if (node.isArray()) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : node) {
          if (sb.length() > 0) sb.append(",");
          sb.append(n.asText());
        }
        return sb.toString();
      }
      return json;
    } catch (Exception e) {
      return json;
    }
  }

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
      item.put("notifyChannels", fromJsonArray(config.getNotifyChannels()));
      item.put("notifyUsers", fromJsonArray(config.getNotifyUsers()));
      item.put("enabled", config.getStatus() != null && config.getStatus() == 1);
      item.put("createTime", config.getCreateTime());
      list.add(item);
    }
    return list;
  }

  @Override
  public void createConfig(Map<String, Object> data) {
    RiskAlertConfigEntity entity = new RiskAlertConfigEntity();
    // 兼容多种字段命名（前端可能用 name/type 或 alert_name/alert_type）
    String name = firstStr(data, "alertName", "alert_name", "name");
    String type = firstStr(data, "alertType", "alert_type", "type");
    if (name != null) entity.setAlertName(name);
    if (type != null) entity.setAlertType(type);
    if (data.get("threshold") != null) entity.setThreshold(Integer.valueOf(data.get("threshold").toString()));
    if (data.get("notifyChannels") != null) entity.setNotifyChannels(toJsonArray(data.get("notifyChannels")));
    if (data.get("notifyUsers") != null) entity.setNotifyUsers(toJsonArray(data.get("notifyUsers")));
    // 兜底：alert_name/alert_type/metric/condition/threshold 是 NOT NULL 必填字段
    if (entity.getAlertName() == null || entity.getAlertName().isEmpty()) {
      entity.setAlertName("未命名告警_" + System.currentTimeMillis());
    }
    if (entity.getAlertType() == null || entity.getAlertType().isEmpty()) {
      entity.setAlertType("THRESHOLD");
    }
    if (entity.getMetric() == null) entity.setMetric("DEFAULT_METRIC");
    if (entity.getCondition() == null) entity.setCondition("GREATER_THAN");
    if (entity.getThreshold() == null) entity.setThreshold(0);
    // 兜底：status 是 TINYINT(1)（V20260727 迁移），写入整数 1=启用
    entity.setStatus(1);
    riskAlertConfigMapper.insert(entity);
  }

  private String firstStr(Map<String, Object> data, String... keys) {
    for (String k : keys) {
      Object v = data.get(k);
      if (v != null) return v.toString();
    }
    return null;
  }

  @Override
  public void updateConfig(Map<String, Object> data) {
    if (data.get("id") == null) return;
    RiskAlertConfigEntity entity = riskAlertConfigMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setAlertName((String) data.get("name"));
    if (data.get("type") != null) entity.setAlertType((String) data.get("type"));
    if (data.get("threshold") != null) entity.setThreshold(Integer.valueOf(data.get("threshold").toString()));
    if (data.get("notifyChannels") != null) entity.setNotifyChannels(toJsonArray(data.get("notifyChannels")));
    if (data.get("notifyUsers") != null) entity.setNotifyUsers(toJsonArray(data.get("notifyUsers")));
    if (data.get("enabled") != null) {
      entity.setStatus(Boolean.TRUE.equals(data.get("enabled")) ? 1 : 0);
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
