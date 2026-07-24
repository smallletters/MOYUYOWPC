package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 — 认证相关接口
 * 负责管理员登录/登出/信息查询，通过数据库验证用户
 */
@Tag(name = "管理后台 - 认证")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;

    @Operation(summary = "管理员登录（支持邮箱或用户名）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Result.error(400, "邮箱和密码不能为空");
        }

        // 优先按邮箱查找，再按用户名查找
        AdminUserEntity adminUser = adminUserMapper.selectOne(
            new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getEmail, email)
                .eq(AdminUserEntity::getStatus, "ACTIVE")
        );
        if (adminUser == null) {
            adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                    .eq(AdminUserEntity::getUsername, email)
                    .eq(AdminUserEntity::getStatus, "ACTIVE")
            );
        }

        if (adminUser == null) {
            return Result.error(401, "邮箱或密码错误");
        }

        // 使用 BCrypt 验证密码
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(password, adminUser.getPassword())) {
            return Result.error(401, "邮箱或密码错误");
        }

        // 更新最后登录时间
        adminUser.setLastLoginTime(java.time.LocalDateTime.now());
        adminUserMapper.updateById(adminUser);

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generate(adminUser.getId(), email));
        data.put("name", adminUser.getName());
        data.put("role", adminUser.getRole());
        return Result.success(data);
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
        Long userId = UserContextHolder.getUserId();
        Map<String, Object> info = new HashMap<>();

        // 优先从数据库查询当前用户信息
        if (userId != null && userId > 0) {
            AdminUserEntity adminUser = adminUserMapper.selectById(userId);
            if (adminUser != null) {
                info.put("name", adminUser.getName());
                info.put("email", adminUser.getEmail());
                info.put("role", adminUser.getRole());
                return Result.success(info);
            }
        }

        // 降级：返回默认信息（JWT 解析失败时）
        info.put("name", "Admin");
        info.put("email", "");
        info.put("role", "SUPER_ADMIN");
        return Result.success(info);
    }
}
