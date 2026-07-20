package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminRiskAlertService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 风险告警服务实现（模拟数据）
 */
@Service
public class AdminRiskAlertServiceImpl implements AdminRiskAlertService {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public List<Map<String, Object>> listConfigs() {
    List<Map<String, Object>> list = new ArrayList<>();

    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("id", 1L);
    r1.put("name", "登录异常检测");
    r1.put("type", "LOGIN");
    r1.put("threshold", 5);
    r1.put("enabled", true);
    r1.put("action", "VERIFY");
    r1.put("createTime", LocalDateTime.now().minusDays(7).format(FMT));
    list.add(r1);

    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("id", 2L);
    r2.put("name", "大额订单风控");
    r2.put("type", "ORDER");
    r2.put("threshold", 10000);
    r2.put("enabled", true);
    r2.put("action", "REVIEW");
    r2.put("createTime", LocalDateTime.now().minusDays(3).format(FMT));
    list.add(r2);

    Map<String, Object> r3 = new LinkedHashMap<>();
    r3.put("id", 3L);
    r3.put("name", "支付失败监控");
    r3.put("type", "PAYMENT");
    r3.put("threshold", 3);
    r3.put("enabled", false);
    r3.put("action", "LOG");
    r3.put("createTime", LocalDateTime.now().minusDays(1).format(FMT));
    list.add(r3);

    return list;
  }

  @Override
  public void createConfig(Map<String, Object> data) {
    // 模拟创建成功
  }

  @Override
  public void updateConfig(Map<String, Object> data) {
    // 模拟更新成功
  }

  @Override
  public void deleteConfig(Long id) {
    // 模拟删除成功
  }

  @Override
  public Map<String, Object> listHistory(int page, int size) {
    List<Map<String, Object>> list = new ArrayList<>();

    Map<String, Object> e1 = new LinkedHashMap<>();
    e1.put("id", 101L);
    e1.put("ruleId", 1L);
    e1.put("type", "LOGIN");
    e1.put("level", "HIGH");
    e1.put("message", "用户异常登录，连续失败5次");
    e1.put("handled", false);
    e1.put("createTime", LocalDateTime.now().minusHours(2).format(FMT));
    list.add(e1);

    Map<String, Object> e2 = new LinkedHashMap<>();
    e2.put("id", 102L);
    e2.put("ruleId", 2L);
    e2.put("type", "ORDER");
    e2.put("level", "MEDIUM");
    e2.put("message", "单笔订单金额超过¥10,000");
    e2.put("handled", true);
    e2.put("createTime", LocalDateTime.now().minusHours(5).format(FMT));
    list.add(e2);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", list.size());
    result.put("page", page);
    result.put("size", size);
    return result;
  }
}
