package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminGdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - GDPR合规")
@RestController
@RequestMapping("/api/admin/gdpr")
@RequiredArgsConstructor
public class AdminGdprController {

  private final AdminGdprService adminGdprService;

  @Operation(summary = "用户同意记录列表")
  @GetMapping("/consent-records")
  public Result<?> consentRecords(
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminGdprService.listConsents(userId, page, size));
  }

  @Operation(summary = "数据主体请求列表")
  @GetMapping("/data-requests")
  public Result<?> dataRequests(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminGdprService.listRequests(status, page, size));
  }

  @Operation(summary = "处理数据请求")
  @PutMapping("/{id}/process")
  public Result<Map<String, Object>> processRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String result = (String) body.getOrDefault("result", "COMPLETED");
    String note = (String) body.getOrDefault("note", "");
    adminGdprService.processRequest(id, result, note);
    return Result.success(Map.of("id", id, "status", result, "message", "数据请求已处理"));
  }

  @Operation(summary = "获取隐私政策")
  @GetMapping("/policy")
  public Result<Map<String, Object>> policy() {
    return Result.success(adminGdprService.getActivePolicy());
  }

  @Operation(summary = "新建隐私政策")
  @PostMapping("/policy")
  public Result<Map<String, Object>> createPolicy(@RequestBody Map<String, Object> body) {
    return Result.success(adminGdprService.createPolicy(body));
  }

  @Operation(summary = "更新隐私政策")
  @PutMapping("/policy/{id}")
  public Result<Map<String, Object>> updatePolicy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return Result.success(adminGdprService.updatePolicy(id, body));
  }

  // ========== 未成年人保护 ==========

  @Operation(summary = "未成年人保护统计")
  @GetMapping("/minor/stats")
  public Result<Map<String, Object>> minorStats() {
    return Result.success(adminGdprService.getMinorStats());
  }

  @Operation(summary = "未成年人年龄验证列表")
  @GetMapping("/minor/list")
  public Result<List<Map<String, Object>>> minorList(@RequestParam(defaultValue = "20") int limit) {
    return Result.success(adminGdprService.listMinorVerifications(limit));
  }

  @Operation(summary = "重新发起年龄验证")
  @PostMapping("/minor/{userId}/reverify")
  public Result<Map<String, Object>> reverifyMinor(@PathVariable Long userId) {
    adminGdprService.reverifyMinor(userId);
    return Result.success(Map.of("userId", userId, "message", "已重新发起年龄验证"));
  }

  @Operation(summary = "家长同意凭证列表")
  @GetMapping("/minor/consent-proofs")
  public Result<List<Map<String, Object>>> consentProofs(@RequestParam(required = false) Long userId) {
    return Result.success(adminGdprService.listConsentProofs(userId));
  }

  @Operation(summary = "家长同意凭证存档概览")
  @GetMapping("/minor/archive-overview")
  public Result<Map<String, Object>> archiveOverview() {
    return Result.success(adminGdprService.getArchiveOverview());
  }

  @Operation(summary = "GDPR 快速操作")
  @PostMapping("/quick-action")
  public Result<Map<String, Object>> quickAction(@RequestBody Map<String, Object> body) {
    return Result.success(adminGdprService.executeQuickAction(body));
  }
}
