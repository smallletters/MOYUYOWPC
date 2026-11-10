package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.SatisfactionSurveyEntity;
import com.moyuyo.dao.admin.mapper.SatisfactionSurveyMapper;
import com.moyuyo.service.admin.AdminSatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 满意度管理")
@RestController
@RequestMapping("/api/admin/satisfaction")
@RequiredArgsConstructor
public class AdminSatisfactionController {

  private final AdminSatisfactionService adminSatisfactionService;
  private final SatisfactionSurveyMapper satisfactionSurveyMapper;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  /** 模板状态 → 中文 */
  private static final Map<String, String> TEMPLATE_STATUS_TEXT = Map.of(
      "active", "使用中",
      "disabled", "已停用",
      "draft", "草稿"
  );

  @Operation(summary = "满意度统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminSatisfactionService.stats());
  }

  @Operation(summary = "评价列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) Integer score,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminSatisfactionService.listAll(score, category, page, size));
  }

  @Operation(summary = "新建满意度调查")
  @PostMapping("/surveys")
  public Result<Map<String, Object>> createSurvey(@RequestBody Map<String, Object> body) {
    return Result.success(adminSatisfactionService.createSurvey(body));
  }

  @Operation(summary = "回复评价")
  @PutMapping("/{id}/reply")
  public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    adminSatisfactionService.replySurvey(id, (String) body.getOrDefault("reply", ""));
    return Result.success(Map.of("id", id, "message", "回复成功"));
  }

  // ==================== 问卷模板 ====================

  @Operation(summary = "问卷模板列表")
  @GetMapping("/templates")
  public Result<List<Map<String, Object>>> templates() {
    String sql = "SELECT id, name, description, questions, minutes, status, create_time, update_time " +
        "FROM mo_satisfaction_template ORDER BY id ASC";
    List<Map<String, Object>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", rs.getLong("id"));
      item.put("name", rs.getString("name"));
      item.put("desc", rs.getString("description"));
      item.put("questions", rs.getInt("questions"));
      item.put("minutes", rs.getBigDecimal("minutes"));
      String status = rs.getString("status");
      item.put("status", status);
      item.put("statusText", status == null ? "草稿" : TEMPLATE_STATUS_TEXT.getOrDefault(status, status));
      Timestamp createTime = rs.getTimestamp("create_time");
      item.put("createTime", createTime == null ? null : createTime.toLocalDateTime().toString());
      return item;
    });
    return Result.success(list);
  }

  @Operation(summary = "新建问卷模板")
  @PostMapping("/templates/create")
  public Result<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    if (name == null || name.isBlank()) {
      return Result.error(400, "参数错误：模板名称不能为空");
    }
    String sql = "INSERT INTO mo_satisfaction_template (name, description, questions, minutes, status) " +
        "VALUES (?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql,
        name,
        body.get("desc") != null ? body.get("desc") : body.get("description"),
        toInt(body.get("questions"), 0),
        toDecimal(body.get("minutes"), new BigDecimal("1.0")),
        normalizeTemplateStatus((String) body.get("status")));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("message", "模板已创建");
    return Result.success(result);
  }

  @Operation(summary = "更新问卷模板")
  @PutMapping("/templates/{id}")
  public Result<Map<String, Object>> updateTemplate(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id FROM mo_satisfaction_template WHERE id = ?", id);
    if (rows.isEmpty()) {
      return Result.error(404, "模板不存在");
    }
    StringBuilder sql = new StringBuilder("UPDATE mo_satisfaction_template SET ");
    List<Object> args = new ArrayList<>();
    if (body.get("name") != null) {
      sql.append("name = ?, ");
      args.add(body.get("name"));
    }
    if (body.get("desc") != null || body.get("description") != null) {
      sql.append("description = ?, ");
      args.add(body.get("desc") != null ? body.get("desc") : body.get("description"));
    }
    if (body.get("questions") != null) {
      sql.append("questions = ?, ");
      args.add(toInt(body.get("questions"), 0));
    }
    if (body.get("minutes") != null) {
      sql.append("minutes = ?, ");
      args.add(toDecimal(body.get("minutes"), new BigDecimal("1.0")));
    }
    if (body.get("status") != null) {
      sql.append("status = ?, ");
      args.add(normalizeTemplateStatus(String.valueOf(body.get("status"))));
    }
    // 没有可更新字段时直接返回成功
    if (args.isEmpty()) {
      return Result.success(Map.of("id", id, "message", "无需更新"));
    }
    // 去掉末尾逗号并补 WHERE
    sql.setLength(sql.length() - 2);
    sql.append(" WHERE id = ?");
    args.add(id);
    jdbcTemplate.update(sql.toString(), args.toArray());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "模板已更新");
    return Result.success(result);
  }

  @Operation(summary = "删除问卷模板")
  @DeleteMapping("/templates/{id}")
  public Result<Map<String, Object>> deleteTemplate(@PathVariable Long id) {
    jdbcTemplate.update("DELETE FROM mo_satisfaction_template WHERE id = ?", id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "模板已删除");
    return Result.success(result);
  }

  // ==================== 调研触发规则 ====================

  @Operation(summary = "调研触发规则与发送设置")
  @GetMapping("/rules")
  public Result<Map<String, Object>> rules() {
    List<Map<String, Object>> rules = jdbcTemplate.query(
        "SELECT id, rule_key, label, description, enabled FROM mo_satisfaction_rule ORDER BY sort_no ASC",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("ruleKey", rs.getString("rule_key"));
          item.put("label", rs.getString("label"));
          item.put("desc", rs.getString("description"));
          item.put("enabled", rs.getInt("enabled") == 1);
          return item;
        });

    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("delayHours", readIntConfig("satisfaction.delay_hours", 24));
    settings.put("channels", readJsonListConfig("satisfaction.channels", List.of("App 内", "邮件")));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("rules", rules);
    result.put("settings", settings);
    return Result.success(result);
  }

  @Operation(summary = "启用/禁用调研触发规则")
  @PutMapping("/rules/{id}")
  public Result<Map<String, Object>> toggleRule(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
    Boolean enabled = body.get("enabled") == null ? null : Boolean.valueOf(String.valueOf(body.get("enabled")));
    if (enabled == null) {
      // 未传时取反
      List<Integer> current = jdbcTemplate.query(
          "SELECT enabled FROM mo_satisfaction_rule WHERE id = ?",
          (rs, rowNum) -> rs.getInt("enabled"), id);
      if (current.isEmpty()) {
        return Result.error(404, "规则不存在");
      }
      enabled = current.get(0) != 1;
    }
    jdbcTemplate.update("UPDATE mo_satisfaction_rule SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("enabled", enabled);
    result.put("message", "规则已更新");
    return Result.success(result);
  }

  @Operation(summary = "保存调研发送设置（延迟 / 渠道）")
  @PutMapping("/rules/settings/save")
  public Result<Map<String, Object>> saveRuleSettings(@RequestBody Map<String, Object> body) {
    if (body.get("delayHours") != null) {
      upsertConfig("satisfaction.delay_hours", String.valueOf(toInt(body.get("delayHours"), 24)),
          "满意度调研触发延迟（小时）");
    }
    if (body.get("channels") != null) {
      try {
        upsertConfig("satisfaction.channels",
            objectMapper.writeValueAsString(body.get("channels")),
            "满意度调研发送渠道（JSON 数组）");
      } catch (Exception e) {
        return Result.error(400, "渠道格式错误：" + e.getMessage());
      }
    }
    return Result.success(Map.of("message", "发送设置已保存"));
  }

  // ==================== 趋势 / 低分反馈 / 概览 ====================

  @Operation(summary = "近 N 天满意度趋势")
  @GetMapping("/trend")
  public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
    int safeDays = days <= 0 ? 30 : Math.min(days, 90);
    LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

    List<Map<String, Object>> rows = satisfactionSurveyMapper.selectMaps(
        new QueryWrapper<SatisfactionSurveyEntity>()
            .select("DATE(create_time) AS date, ROUND(AVG(score), 1) AS score, COUNT(*) AS count")
            .ge("create_time", since)
            .groupBy("DATE(create_time)")
            .orderByAsc("DATE(create_time)"));

    // 日期 → 聚合结果
    Map<String, Map<String, Object>> byDate = new HashMap<>();
    for (Map<String, Object> row : rows) {
      Object dateObj = row.get("date");
      if (dateObj == null) continue;
      byDate.put(String.valueOf(dateObj), row);
    }

    // 补齐缺失日期，保证图表连续
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < safeDays; i++) {
      LocalDate date = LocalDate.now().minusDays(safeDays - 1L - i);
      String key = date.toString();
      Map<String, Object> row = byDate.get(key);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", (date.getMonthValue()) + "/" + date.getDayOfMonth());
      item.put("fullDate", key);
      item.put("score", row == null || row.get("score") == null ? 0 : row.get("score"));
      item.put("count", row == null || row.get("count") == null ? 0 : row.get("count"));
      result.add(item);
    }
    return Result.success(result);
  }

  @Operation(summary = "最新低分反馈")
  @GetMapping("/low-score")
  public Result<List<Map<String, Object>>> lowScore(@RequestParam(defaultValue = "10") int limit) {
    int safeLimit = limit <= 0 ? 10 : Math.min(limit, 100);
    String sql = "SELECT s.id, s.user_id, COALESCE(u.nickname, CONCAT('用户', s.user_id), '匿名用户') AS user_name, " +
        "s.score, s.comment, s.order_id, s.ticket_id, s.create_time " +
        "FROM mo_satisfaction_survey s LEFT JOIN mo_user u ON u.id = s.user_id " +
        "WHERE s.score <= 3 ORDER BY s.create_time DESC LIMIT ?";
    List<Map<String, Object>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", rs.getLong("id"));
      item.put("userId", rs.getObject("user_id"));
      item.put("user", rs.getString("user_name"));
      item.put("score", rs.getInt("score"));
      item.put("content", rs.getString("comment"));
      long orderId = rs.getLong("order_id");
      long ticketId = rs.getLong("ticket_id");
      item.put("source", orderId > 0 ? "订单 #" + orderId : (ticketId > 0 ? "客服会话 #" + ticketId : "其他"));
      Timestamp ts = rs.getTimestamp("create_time");
      item.put("time", ts == null ? null : ts.toLocalDateTime().toString().replace('T', ' '));
      // 评价内容含客服回复则视为已跟进
      String comment = rs.getString("comment");
      boolean replied = comment != null && comment.contains("【客服回复】");
      item.put("status", replied ? "active" : "pending");
      item.put("statusText", replied ? "已跟进" : "待跟进");
      return item;
    }, safeLimit);
    return Result.success(list);
  }

  @Operation(summary = "调研概览（回收率 / 低分预警）")
  @GetMapping("/overview")
  public Result<Map<String, Object>> overview() {
    LocalDateTime since = LocalDate.now().minusDays(29).atStartOfDay();

    // 近 30 天满意度评价数
    Long surveyCount = satisfactionSurveyMapper.selectCount(
        new QueryWrapper<SatisfactionSurveyEntity>().ge("create_time", since));
    // 近 30 天低分评价数（<=3）
    Long lowScoreCount = satisfactionSurveyMapper.selectCount(
        new QueryWrapper<SatisfactionSurveyEntity>().ge("create_time", since).le("score", 3));
    // 近 30 天完成订单数（作为问卷应回收基数）
    Long completedOrders = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mo_order WHERE status = 'COMPLETED' AND delete_status = 0 AND create_time >= ?",
        Long.class, since);

    long total = completedOrders == null ? 0 : completedOrders;
    long surveys = surveyCount == null ? 0 : surveyCount;
    BigDecimal recycleRate = total > 0
        ? BigDecimal.valueOf(surveys * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("recycleRate", recycleRate);
    result.put("lowScore", lowScoreCount == null ? 0 : lowScoreCount);
    result.put("surveyCount", surveys);
    result.put("completedOrders", total);
    return Result.success(result);
  }

  // ==================== 私有辅助方法 ====================

  private int toInt(Object value, int defaultValue) {
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private BigDecimal toDecimal(Object value, BigDecimal defaultValue) {
    if (value == null) return defaultValue;
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /** 模板状态归一化：支持中文与编码 */
  private String normalizeTemplateStatus(String status) {
    if (status == null || status.isBlank()) return "draft";
    for (Map.Entry<String, String> entry : TEMPLATE_STATUS_TEXT.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(status) || entry.getValue().equals(status)) {
        return entry.getKey();
      }
    }
    return "draft";
  }

  /** 读取整型系统配置 */
  private int readIntConfig(String key, int defaultValue) {
    List<String> values = jdbcTemplate.query(
        "SELECT config_value FROM mo_system_config WHERE config_key = ?",
        (rs, rowNum) -> rs.getString("config_value"), key);
    if (values.isEmpty() || values.get(0) == null) return defaultValue;
    try {
      return Integer.parseInt(values.get(0));
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /** 读取 JSON 数组系统配置 */
  private List<String> readJsonListConfig(String key, List<String> defaultValue) {
    List<String> values = jdbcTemplate.query(
        "SELECT config_value FROM mo_system_config WHERE config_key = ?",
        (rs, rowNum) -> rs.getString("config_value"), key);
    if (values.isEmpty() || values.get(0) == null) return defaultValue;
    try {
      return objectMapper.readValue(values.get(0), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /** 系统配置不存在时插入，存在时更新（先查后写，避免依赖 affectedRows 语义） */
  private void upsertConfig(String key, String value, String remark) {
    List<String> existing = jdbcTemplate.query(
        "SELECT config_value FROM mo_system_config WHERE config_key = ?",
        (rs, rowNum) -> rs.getString("config_value"), key);
    if (existing.isEmpty()) {
      jdbcTemplate.update(
          "INSERT INTO mo_system_config (config_key, config_value, remark, create_time, update_time) " +
              "VALUES (?, ?, ?, NOW(), NOW())", key, value, remark);
    } else {
      jdbcTemplate.update(
          "UPDATE mo_system_config SET config_value = ? WHERE config_key = ?", value, key);
    }
  }
}
