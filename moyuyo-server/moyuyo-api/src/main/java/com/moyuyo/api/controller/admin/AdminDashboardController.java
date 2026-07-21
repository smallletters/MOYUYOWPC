package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 — 仪表盘相关接口
 * 原 AdminController 拆分而来，负责首页数据展示
 */
@Tag(name = "管理后台 - 仪表盘")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "仪表盘统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> dashboardStats() {
        return Result.success(dashboardService.getDashboardStats());
    }

    @Operation(summary = "最近订单")
    @GetMapping("/recent-orders")
    public Result<List<Map<String, Object>>> recentOrders() {
        return Result.success(dashboardService.getRecentOrders());
    }

    @Operation(summary = "销售趋势")
    @GetMapping("/sales-trend")
    public Result<List<Map<String, Object>>> salesTrend() {
        return Result.success(dashboardService.getSalesTrend());
    }
}
