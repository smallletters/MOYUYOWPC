package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.RiskRuleEntity;
import com.moyuyo.service.admin.AdminRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 风控管理")
@RestController
@RequestMapping("/api/admin/risk")
@RequiredArgsConstructor
public class AdminRiskController {

  private final AdminRiskService adminRiskService;

  @Operation(summary = "风控规则列表")
  @GetMapping("/rules")
  public Result<?> rules(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminRiskService.listRules(page, size));
  }

  @Operation(summary = "创建规则")
  @PostMapping("/rules")
  public Result<Map<String, Object>> createRule(@RequestBody RiskRuleEntity body) {
    adminRiskService.createRule(body);
    return Result.success(Map.of("id", body.getId(), "message", "规则创建成功"));
  }

  @Operation(summary = "更新规则")
  @PutMapping("/rules/{id}")
  public Result<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody RiskRuleEntity body) {
    body.setId(id);
    adminRiskService.updateRule(body);
    return Result.success(Map.of("id", id, "message", "规则更新成功"));
  }

  @Operation(summary = "启用/禁用规则")
  @PutMapping("/rules/{id}/status")
  public Result<Map<String, Object>> toggleRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Boolean enabled = Boolean.valueOf(String.valueOf(body.getOrDefault("enabled", true)));
    adminRiskService.toggleRule(id, enabled);
    return Result.success(Map.of("id", id, "enabled", enabled, "message", "规则状态更新成功"));
  }

  @Operation(summary = "删除规则")
  @DeleteMapping("/rules/{id}")
  public Result<Map<String, Object>> deleteRule(@PathVariable Long id) {
    adminRiskService.deleteRule(id);
    return Result.success(Map.of("id", id, "message", "规则删除成功"));
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
}
