package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminGdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}
