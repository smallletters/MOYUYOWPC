package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminRiskAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 风险告警")
@RestController
@RequestMapping("/api/admin/risk-alert")
@RequiredArgsConstructor
public class AdminRiskAlertController {

    private final AdminRiskAlertService adminRiskAlertService;

    @Operation(summary = "告警配置列表")
    @GetMapping("/configs")
    public Result<Map<String, Object>> configs() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", (long) adminRiskAlertService.listConfigs().size());
        result.put("records", adminRiskAlertService.listConfigs());
        return Result.success(result);
    }

    @Operation(summary = "创建告警配置")
    @PostMapping("/configs/create")
    public Result<Map<String, Object>> createConfig(@RequestBody Map<String, Object> body) {
        adminRiskAlertService.createConfig(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新告警配置")
    @PutMapping("/configs/update")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        adminRiskAlertService.updateConfig(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除告警配置")
    @DeleteMapping("/configs/{id}")
    public Result<Map<String, Object>> deleteConfig(@PathVariable Long id) {
        adminRiskAlertService.deleteConfig(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "告警历史")
    @GetMapping("/history")
    public Result<Map<String, Object>> history(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminRiskAlertService.listHistory(page, size));
    }
}
