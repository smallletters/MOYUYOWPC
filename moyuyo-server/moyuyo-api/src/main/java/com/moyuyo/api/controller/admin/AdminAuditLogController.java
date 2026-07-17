package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 审计日志")
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

  private final AdminAuditLogService adminAuditLogService;

  @Operation(summary = "审计日志列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String module,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminAuditLogService.listAll(action, module, page, size));
  }

  @Operation(summary = "审计日志统计")
  @GetMapping("/stats")
  public Result<?> stats() {
    return Result.success(adminAuditLogService.stats());
  }
}
