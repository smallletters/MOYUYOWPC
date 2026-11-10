package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 数据分析")
@RestController
@RequestMapping("/api/admin/analysis")
@RequiredArgsConstructor
public class AdminAnalysisController {

  private final AdminAnalysisService adminAnalysisService;
  private final JdbcTemplate jdbcTemplate;

  @Operation(summary = "漏斗分析数据")
  @GetMapping("/funnel")
  public Result<?> funnel() {
    return Result.success(adminAnalysisService.funnel());
  }

  @Operation(summary = "RFM分析")
  @GetMapping("/rfm")
  public Result<?> rfm() {
    return Result.success(adminAnalysisService.rfm());
  }

  @Operation(summary = "搜索分析KPI")
  @GetMapping("/search")
  public Result<Map<String, Object>> search() {
    return Result.success(adminAnalysisService.searchStats());
  }

  @Operation(summary = "零结果搜索词 TOP N")
  @GetMapping("/search/zero-result")
  public Result<?> zeroResultSearch(@RequestParam(defaultValue = "10") int limit) {
    return Result.success(adminAnalysisService.zeroResultKeywords(limit));
  }

  @Operation(summary = "流量分析KPI")
  @GetMapping("/traffic")
  public Result<Map<String, Object>> traffic() {
    return Result.success(adminAnalysisService.trafficStats());
  }

  @Operation(summary = "流失分析（最近 days 天，可选 7/30）")
  @GetMapping("/churn")
  public Result<Map<String, Object>> churn(@RequestParam(defaultValue = "7") int days) {
    return Result.success(adminAnalysisService.churn(days));
  }

  @Operation(summary = "复购率分析（最近 days 天，可选 7/30）")
  @GetMapping("/repurchase")
  public Result<Map<String, Object>> repurchase(@RequestParam(defaultValue = "30") int days) {
    return Result.success(adminAnalysisService.repurchase(days));
  }

  @Operation(summary = "用户画像（新老用户占比 / 设备分布）")
  @GetMapping("/profile")
  public Result<Map<String, Object>> profile() {
    return Result.success(adminAnalysisService.userProfile());
  }

  @Operation(summary = "渠道访问统计（近 30 天）")
  @GetMapping("/channels")
  public Result<?> channels() {
    return Result.success(adminAnalysisService.channelStats());
  }

  @Operation(summary = "创建选品需求（零结果搜索词）")
  @PostMapping("/search/sourcing")
  public Result<Map<String, Object>> createSourcing(@RequestBody Map<String, Object> body) {
    String keyword = body.get("keyword") == null ? "" : String.valueOf(body.get("keyword")).trim();
    if (keyword.isEmpty()) {
      return Result.error(400, "参数错误：keyword 不能为空");
    }
    jdbcTemplate.update(
        "INSERT INTO mo_search_keyword_action (keyword, action_type, category, target_user, priority, operator) " +
            "VALUES (?, 'SOURCING', ?, ?, ?, ?)",
        keyword,
        toNullable(body.get("category")),
        toNullable(body.get("targetUser")),
        toNullable(body.get("priority")),
        "admin");
    return Result.success(Map.of("keyword", keyword, "message", "选品需求已创建"));
  }

  @Operation(summary = "标记搜索词已处理")
  @PostMapping("/search/suggest/resolve")
  public Result<Map<String, Object>> resolveSearchSuggestion(@RequestBody Map<String, Object> body) {
    String keyword = body.get("keyword") == null ? "" : String.valueOf(body.get("keyword")).trim();
    if (keyword.isEmpty()) {
      return Result.error(400, "参数错误：keyword 不能为空");
    }
    jdbcTemplate.update(
        "INSERT INTO mo_search_keyword_action (keyword, action_type, operator) VALUES (?, 'RESOLVED', ?)",
        keyword, "admin");
    return Result.success(Map.of("keyword", keyword, "message", "已标记为已处理"));
  }

  /** 空字符串统一转 null，避免写入无意义的空串 */
  private String toNullable(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }
}
