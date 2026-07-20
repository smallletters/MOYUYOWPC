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

    @Operation(summary = "管理员退出登录")
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        // JWT 无状态，客户端清除 token 即可
        return Result.success();
    }

    @Operation(summary = "获取管理员信息")
    @GetMapping("/auth/me")
    public Result<Map<String, Object>> adminInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Admin");
        info.put("email", adminEmail);
        info.put("role", "超级管理员");
        return Result.success(info);
    }

    @Operation(summary = "获取系统安全配置")
    @GetMapping("/system/security-config")
    public Result<List<Map<String, Object>>> securityConfig() {
        List<Map<String, Object>> configs = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", "password_policy");
        item.put("value", "medium");
        item.put("description", "密码策略");
        configs.add(item);
        item = new LinkedHashMap<>();
        item.put("key", "session_timeout");
        item.put("value", "30");
        item.put("description", "会话超时时间(分钟)");
        configs.add(item);
        item = new LinkedHashMap<>();
        item.put("key", "max_login_attempts");
        item.put("value", "5");
        item.put("description", "最大登录尝试次数");
        configs.add(item);
        return Result.success(configs);
    }

    @Operation(summary = "获取系统信息")
    @GetMapping("/system/info")
    public Result<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "1.0.0");
        info.put("dbStatus", "正常");
        info.put("cacheStatus", "正常");
        info.put("lastBackup", "2026-07-18");
        return Result.success(info);
    }

    @Operation(summary = "获取支付方式")
    @GetMapping("/settings/payment-methods")
    public Result<List<Map<String, String>>> paymentMethods() {
        List<Map<String, String>> methods = new ArrayList<>();
        Map<String, String> stripe = new LinkedHashMap<>();
        stripe.put("name", "Stripe");
        stripe.put("code", "stripe");
        stripe.put("status", "active");
        methods.add(stripe);
        Map<String, String> paypal = new LinkedHashMap<>();
        paypal.put("name", "PayPal");
        paypal.put("code", "paypal");
        paypal.put("status", "active");
        methods.add(paypal);
        return Result.success(methods);
    }

}
