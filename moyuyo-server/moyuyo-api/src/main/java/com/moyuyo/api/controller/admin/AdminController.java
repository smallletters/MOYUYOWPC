package com.moyuyo.api.controller.admin;

import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final JwtUtil jwtUtil;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Operation(summary = "管理员登录")
    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (adminEmail.isEmpty() || adminPassword.isEmpty()) {
            return Result.error(500, "管理员账号未配置，请在 application-prod.yml 中设置 admin.email 和 admin.password");
        }
        if (adminEmail.equals(email) && adminPassword.equals(password)) {
            Map<String, Object> data = new HashMap<>();
            // 生成有效的 JWT token，使用固定管理员 ID 0
            data.put("token", jwtUtil.generate(0L, email));
            data.put("name", "Admin");
            data.put("role", "超级管理员");
            return Result.success(data);
        }
        return Result.error(401, "邮箱或密码错误");
    }

    @Operation(summary = "仪表盘统计数据")
    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> dashboardStats() {
        return Result.success(dashboardService.getDashboardStats());
    }

    @Operation(summary = "最近订单")
    @GetMapping("/dashboard/recent-orders")
    public Result<List<Map<String, Object>>> recentOrders() {
        return Result.success(dashboardService.getRecentOrders());
    }

    @Operation(summary = "销售趋势")
    @GetMapping("/dashboard/sales-trend")
    public Result<List<Map<String, Object>>> salesTrend() {
        return Result.success(dashboardService.getSalesTrend());
    }

}
