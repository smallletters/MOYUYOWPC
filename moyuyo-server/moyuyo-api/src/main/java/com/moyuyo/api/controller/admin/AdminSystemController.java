package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 — 系统管理相关接口
 * 原 AdminController 拆分而来，负责系统配置和系统信息
 */
@Tag(name = "管理后台 - 系统管理")
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    @Operation(summary = "获取系统安全配置")
    @GetMapping("/security-config")
    public Result<List<Map<String, Object>>> securityConfig() {
        List<Map<String, Object>> configs = Arrays.asList(
            Map.of("key", "password_policy", "value", "medium", "description", "密码策略"),
            Map.of("key", "session_timeout", "value", "30", "description", "会话超时时间(分钟)"),
            Map.of("key", "max_login_attempts", "value", "5", "description", "最大登录尝试次数")
        );
        return Result.success(configs);
    }

    @Operation(summary = "获取系统信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "1.0.0");
        info.put("dbStatus", "正常");
        info.put("cacheStatus", "正常");
        info.put("lastBackup", "2026-07-18");
        return Result.success(info);
    }
}
