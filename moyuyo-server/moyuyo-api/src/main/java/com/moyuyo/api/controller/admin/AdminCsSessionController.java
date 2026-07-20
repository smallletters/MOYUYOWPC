package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminCsSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 客服会话管理")
@RestController
@RequestMapping("/api/admin/cs-sessions")
@RequiredArgsConstructor
public class AdminCsSessionController {

    private final AdminCsSessionService adminCsSessionService;

    @Operation(summary = "会话列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status) {
        return Result.success(adminCsSessionService.listAll(page, size, status));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(adminCsSessionService.getById(id));
    }

    @Operation(summary = "客服绩效统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(adminCsSessionService.getStats());
    }
}
