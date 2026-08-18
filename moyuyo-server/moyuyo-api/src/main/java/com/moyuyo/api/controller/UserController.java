package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.auth.ProfileUpdateRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserEntity> getCurrentUser() {
        return Result.success(authService.getCurrentUser(UserContextHolder.getUserId()));
    }

    @Operation(summary = "更新当前用户信息")
    @PutMapping("/me")
    public Result<UserEntity> updateCurrentUser(@Valid @RequestBody ProfileUpdateRequest request) {
        // 关键修复：原实现直接接受 UserEntity（含 passwordHash/role/status/points/twoFactorEnabled/emailVerified 等敏感字段），
        // 攻击者可构造 JSON 注入尝试越权修改（如 {"role":"ADMIN","points":99999999}）。
        // 现改为 ProfileUpdateRequest DTO 白名单：仅暴露昵称/头像/性别/生日/国家/语言/时区/营销订阅 8 个字段，
        // 与 ProfileUpdateRequest#isAvatarValid() 协同兜底，强制头像 URL 必须 https?:// 协议（拒绝 javascript:/data:）。
        return Result.success(authService.updateCurrentUser(UserContextHolder.getUserId(), request));
    }
}
