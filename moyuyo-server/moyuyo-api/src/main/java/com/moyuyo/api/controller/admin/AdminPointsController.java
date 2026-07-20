package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminPointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 积分管理")
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class AdminPointsController {

    private final AdminPointsService adminPointsService;

    @Operation(summary = "积分活动列表")
    @GetMapping("/activities")
    public Result<Map<String, Object>> activities() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", (long) adminPointsService.listActivities().size());
        result.put("records", adminPointsService.listActivities());
        return Result.success(result);
    }

    @Operation(summary = "创建积分活动")
    @PostMapping("/activities/create")
    public Result<Map<String, Object>> createActivity(@RequestBody Map<String, Object> body) {
        adminPointsService.createActivity(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "积分流水")
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) Long userId) {
        return Result.success(adminPointsService.listLogs(page, size, userId));
    }

    @Operation(summary = "积分统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(adminPointsService.getStats());
    }
}
