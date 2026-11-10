package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.RiskRuleEntity;
import com.moyuyo.service.admin.AdminRiskService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 风控管理")
@Slf4j
@RestController
@RequestMapping("/api/admin/risk")
@RequiredArgsConstructor
public class AdminRiskController {

  private final AdminRiskService adminRiskService;
  private final JdbcTemplate jdbcTemplate;

  /** 事件类型 → 中文 */
  private static final Map<String, String> EVENT_TYPE_TEXT = Map.of(
      "LOGIN", "登录风险",
      "ORDER", "下单风险",
      "PAYMENT", "支付风险"
  );

  /** 风险等级 → 中文 */
  private static final Map<String, String> RISK_LEVEL_TEXT = Map.of(
      "LOW", "低风险",
      "MEDIUM", "中风险",
      "HIGH", "高风险",
      "CRITICAL", "严重风险"
  );

  @Operation(summary = "风控规则列表")
  @GetMapping("/rules")
  public Result<?> rules(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminRiskService.listRules(page, size));
  }

  @Operation(summary = "创建规则")
  @PostMapping("/rules")
  @RateLimiter(name = "riskRuleUpdate", fallbackMethod = "riskRuleRateLimitFallback")
  public Result<Map<String, Object>> createRule(@RequestBody RiskRuleEntity body) {
    adminRiskService.createRule(body);
    return Result.success(Map.of("id", body.getId(), "message", "规则创建成功"));
  }

  @Operation(summary = "更新规则")
  @PutMapping("/rules/{id}")
  @RateLimiter(name = "riskRuleUpdate", fallbackMethod = "riskRuleRateLimitFallbackUpdate")
  public Result<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody RiskRuleEntity body) {
    body.setId(id);
    adminRiskService.updateRule(body);
    return Result.success(Map.of("id", id, "message", "规则更新成功"));
  }

  @Operation(summary = "启用/禁用规则")
  @PutMapping("/rules/{id}/status")
  @RateLimiter(name = "riskRuleUpdate", fallbackMethod = "riskRuleRateLimitFallbackToggle")
  public Result<Map<String, Object>> toggleRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Boolean enabled = Boolean.valueOf(String.valueOf(body.getOrDefault("enabled", true)));
    adminRiskService.toggleRule(id, enabled);
    return Result.success(Map.of("id", id, "enabled", enabled, "message", "规则状态更新成功"));
  }

  @Operation(summary = "删除规则")
  @DeleteMapping("/rules/{id}")
  @RateLimiter(name = "riskRuleUpdate", fallbackMethod = "riskRuleRateLimitFallbackDelete")
  public Result<Map<String, Object>> deleteRule(@PathVariable Long id) {
    adminRiskService.deleteRule(id);
    return Result.success(Map.of("id", id, "message", "规则删除成功"));
  }

  /**
   * 风控规则修改限流降级：dev 30/分钟，prod 10/分钟（详见 application.yml）
   * 4 个 fallback 各自匹配对应方法签名（参数列表类型必须一致）
   */
  @SuppressWarnings("unused")
  private Result<Map<String, Object>> riskRuleRateLimitFallback(RiskRuleEntity body, RequestNotPermitted e) {
    log.warn("风控规则接口触发限流：{}", e.getMessage());
    return Result.error(429, "风控规则操作过于频繁，请稍后再试");
  }

  @SuppressWarnings("unused")
  private Result<Map<String, Object>> riskRuleRateLimitFallbackUpdate(Long id, RiskRuleEntity body, RequestNotPermitted e) {
    return riskRuleRateLimitFallback(body, e);
  }

  @SuppressWarnings("unused")
  private Result<Map<String, Object>> riskRuleRateLimitFallbackToggle(Long id, Map<String, Object> body, RequestNotPermitted e) {
    return Result.error(429, "风控规则操作过于频繁，请稍后再试");
  }

  @SuppressWarnings("unused")
  private Result<Map<String, Object>> riskRuleRateLimitFallbackDelete(Long id, RequestNotPermitted e) {
    return Result.error(429, "风控规则操作过于频繁，请稍后再试");
  }

  @Operation(summary = "风控事件列表")
  @GetMapping("/events")
  public Result<?> events(
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminRiskService.listEvents(level, status, page, size));
  }

  @Operation(summary = "风控事件统计")
  @GetMapping("/event-stats")
  public Result<Map<String, Object>> eventStats() {
    return Result.success(adminRiskService.eventStats());
  }

  @Operation(summary = "风控总览（KPI / 趋势 / 类型分布 / 最新事件）")
  @GetMapping("/overview")
  public Result<Map<String, Object>> overview() {
    Map<String, Object> result = new LinkedHashMap<>();

    // 今日触发与拦截
    Map<String, Object> today = jdbcTemplate.queryForMap(
        "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN action_taken = 'BLOCK' THEN 1 ELSE 0 END) AS blocked " +
            "FROM mo_risk_event WHERE DATE(create_time) = CURDATE()");
    long todayTotal = toLong(today.get("total"));
    long todayBlocked = toLong(today.get("blocked"));

    // 待处理事件
    Long pending = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mo_risk_event WHERE status = 'PENDING'", Long.class);

    // 规则总数与启用数
    Map<String, Object> ruleRow = jdbcTemplate.queryForMap(
        "SELECT COUNT(*) AS total, SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) AS enabled FROM mo_risk_rule");
    long ruleTotal = toLong(ruleRow.get("total"));
    long ruleEnabled = toLong(ruleRow.get("enabled"));

    // 拦截命中率 = 今日拦截 / 今日触发
    BigDecimal hitRate = todayTotal > 0
        ? BigDecimal.valueOf(todayBlocked * 100L).divide(BigDecimal.valueOf(todayTotal), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Map<String, Object> kpi = new LinkedHashMap<>();
    kpi.put("todayBlocked", todayBlocked);
    kpi.put("todayTotal", todayTotal);
    kpi.put("hitRate", hitRate);
    kpi.put("pendingCount", pending == null ? 0L : pending);
    kpi.put("ruleTotal", ruleTotal);
    kpi.put("ruleEnabled", ruleEnabled);
    result.put("kpi", kpi);

    // 近 7 天趋势：触发总数 / 拦截数
    List<Map<String, Object>> trendRows = jdbcTemplate.query(
        "SELECT DATE(create_time) AS d, COUNT(*) AS total, " +
            "SUM(CASE WHEN action_taken = 'BLOCK' THEN 1 ELSE 0 END) AS blocked " +
            "FROM mo_risk_event WHERE create_time >= ? GROUP BY DATE(create_time)",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          java.sql.Date d = rs.getDate("d");
          item.put("date", d == null ? "" : d.toLocalDate().toString());
          item.put("total", rs.getLong("total"));
          item.put("blocked", rs.getLong("blocked"));
          return item;
        },
        LocalDate.now().minusDays(6).atStartOfDay());

    Map<String, Map<String, Object>> trendByDate = new HashMap<>();
    for (Map<String, Object> row : trendRows) {
      trendByDate.put(String.valueOf(row.get("date")), row);
    }
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      LocalDate date = LocalDate.now().minusDays(6L - i);
      Map<String, Object> row = trendByDate.get(date.toString());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()));
      item.put("total", row == null ? 0L : toLong(row.get("total")));
      item.put("blocked", row == null ? 0L : toLong(row.get("blocked")));
      trend.add(item);
    }
    result.put("trend", trend);

    // 近 30 天事件类型分布
    result.put("typeDist", buildDist(
        "SELECT event_type AS k, COUNT(*) AS c FROM mo_risk_event " +
            "WHERE create_time >= ? GROUP BY event_type ORDER BY c DESC",
        EVENT_TYPE_TEXT, "其他风险"));

    // 近 30 天风险等级分布
    result.put("levelDist", buildDist(
        "SELECT risk_level AS k, COUNT(*) AS c FROM mo_risk_event " +
            "WHERE create_time >= ? GROUP BY risk_level ORDER BY c DESC",
        RISK_LEVEL_TEXT, "未分级"));

    // 最新 5 条风险事件
    List<Map<String, Object>> latest = jdbcTemplate.query(
        "SELECT id, event_type, risk_level, action_taken, status, user_id, business_id, create_time " +
            "FROM mo_risk_event ORDER BY create_time DESC LIMIT 5",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          String eventType = rs.getString("event_type");
          item.put("type", eventType == null ? "其他风险" : EVENT_TYPE_TEXT.getOrDefault(eventType, eventType));
          String level = rs.getString("risk_level");
          item.put("level", level == null ? "未分级" : RISK_LEVEL_TEXT.getOrDefault(level, level));
          item.put("user", rs.getObject("user_id") == null ? "-" : "user_" + rs.getObject("user_id"));
          item.put("businessId", rs.getString("business_id"));
          String action = rs.getString("action_taken");
          item.put("action", "BLOCK".equals(action) ? "已拦截"
              : "REVIEW".equals(action) ? "人工审核"
              : "VERIFY".equals(action) ? "二次验证" : (action == null ? "-" : action));
          item.put("actionType", action == null ? "pass" : action.toLowerCase());
          java.sql.Timestamp ts = rs.getTimestamp("create_time");
          item.put("time", ts == null ? "-" : ts.toLocalDateTime().toString().replace('T', ' ').substring(0, 16));
          return item;
        });
    result.put("latestEvents", latest);

    return Result.success(result);
  }

  /** 构造分布数据：[{key, name, count, percent}] */
  private List<Map<String, Object>> buildDist(String sql, Map<String, String> nameMap, String fallbackName) {
    List<Map<String, Object>> rows = jdbcTemplate.query(sql,
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("key", rs.getString("k"));
          item.put("count", rs.getLong("c"));
          return item;
        },
        LocalDate.now().minusDays(29).atStartOfDay());

    long total = rows.stream().mapToLong(r -> toLong(r.get("count"))).sum();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      long count = toLong(row.get("count"));
      String key = row.get("key") == null ? "" : String.valueOf(row.get("key"));
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("key", key);
      item.put("name", nameMap.getOrDefault(key, key.isBlank() ? fallbackName : key));
      item.put("count", count);
      item.put("percent", total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0d);
      result.add(item);
    }
    return result;
  }

  /** 安全转 long */
  private long toLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception e) {
      return 0L;
    }
  }
}
