package com.moyuyo.api.controller.admin;

import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 — 认证相关接口
 * 原 AdminController 拆分而来，负责管理员登录/登出/信息查询
 */
@Tag(name = "管理后台 - 认证")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtUtil jwtUtil;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
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

    @Operation(summary = "管理员退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，客户端清除 token 即可
        return Result.success();
    }

    @Operation(summary = "获取管理员信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> adminInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Admin");
        info.put("email", adminEmail);
        info.put("role", "超级管理员");
        return Result.success(info);
    }
}
