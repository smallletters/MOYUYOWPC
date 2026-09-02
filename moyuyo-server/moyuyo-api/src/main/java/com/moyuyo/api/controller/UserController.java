package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.auth.ProfileUpdateRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户主页")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserMapper userMapper;
  private final FollowMapper followMapper;
  private final AuthService authService;

  @GetMapping("/{id}/profile")
  public Result<Map<String, Object>> profile(@PathVariable Long id) {
    UserEntity u = userMapper.selectById(id);
    if (u == null) throw new IllegalArgumentException("用户不存在");
    long following = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getUserId, id));
    long followers = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getTargetId, id));
    Map<String, Object> p = new HashMap<>();
    p.put("id", u.getId());
    p.put("nickname", u.getNickname());
    p.put("avatar", u.getAvatar());
    p.put("country", u.getCountry());
    p.put("gender", u.getGender());
    p.put("bio", "");
    p.put("points", u.getPoints());
    p.put("following", following);
    p.put("followers", followers);
    p.put("isFollowing", false);
    return Result.success(p);
  }

  /**
   * 当前登录用户的个人资料：APP 端登录后调用此接口填充用户信息。
   * 修复历史：原项目未提供该端点,前端 userStore.fetchProfile 调用 /api/v1/users/me 返回 403/404。
   * 返回字段与 user.js 的 getUserInfo() 期望对齐(id/email/nickname/avatar/phone/birthday/country/emailVerified/twoFactorEnabled)。
   */
  @GetMapping("/search")
  public Result<Map<String, Object>> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserEntity> pageReq =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.trim().isEmpty()) {
      String safe = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      wrapper.like(UserEntity::getNickname, "%" + safe + "%");
    }
    wrapper.orderByDesc(UserEntity::getId);
    var pageResult = userMapper.selectPage(pageReq, wrapper);
    // 仅返回公开字段，避免泄露 password_hash/email 等
    java.util.List<Map<String, Object>> records = pageResult.getRecords().stream().map(u -> {
      java.util.Map<String, Object> m = new java.util.HashMap<>();
      m.put("id", u.getId());
      m.put("nickname", u.getNickname());
      m.put("avatar", u.getAvatar());
      return m;
    }).collect(java.util.stream.Collectors.toList());
    java.util.Map<String, Object> data = new java.util.HashMap<>();
    data.put("records", records);
    data.put("total", pageResult.getTotal());
    data.put("size", pageResult.getSize());
    data.put("current", pageResult.getCurrent());
    return Result.success(data);
  }

  @GetMapping("/me")
  public Result<Map<String, Object>> me() {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      // JwtAuthFilter 已校验 token,这里兜底返回 401 而非 500
      return Result.error(401, "未登录");
    }
    UserEntity u = userMapper.selectById(userId);
    if (u == null) return Result.error(404, "用户不存在");
    return Result.success(toProfileMap(u));
  }

  /**
   * 更新当前登录用户的个人资料(头像/昵称/性别/生日/国家/时区/营销订阅等)。
   * <p>
   * 背景:修复前前端 {@code userStore.updateProfile} 调用 {@code PUT /api/v1/users/me},
   * 但 UserController 仅注册了 {@ GET /me},前端 PUT 收到 405 Method Not Allowed。
   * <p>
   * 设计要点:
   * <ol>
   *   <li>复用 {@link com.moyuyo.service.AuthService#updateCurrentUser} 与 {@link ProfileUpdateRequest},
   *     避免 UserEntity 暴露 passwordHash/role/status 等敏感字段被水平越权修改。</li>
   *   <li>Bean Validation(@Valid)在 Controller 入口触发,字段格式校验失败直接 400。</li>
   *   <li>Service 层二次校验(头像 URL 协议白名单、昵称 XSS 净化)作为注解被绕过兜底。</li>
   *   <li>返回结构与 GET /me 一致,前端可直接覆盖本地 userInfo。</li>
   * </ol>
   */
  @PutMapping("/me")
  @Operation(summary = "更新当前登录用户的个人资料")
  public Result<Map<String, Object>> updateMe(@Valid @RequestBody ProfileUpdateRequest req) {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      return Result.error(401, "未登录");
    }
    UserEntity updated;
    try {
      updated = authService.updateCurrentUser(userId, req);
    } catch (IllegalArgumentException e) {
      // Service 层兜底校验失败:头像 URL 非法 / 用户不存在
      return Result.badRequest(e.getMessage());
    }
    return Result.success(toProfileMap(updated));
  }

  /**
   * UserEntity → profile VO(GET /me 与 PUT /me 共用,保持响应结构一致)。
   */
  private Map<String, Object> toProfileMap(UserEntity u) {
    Map<String, Object> p = new HashMap<>();
    p.put("id", u.getId());
    p.put("email", u.getEmail());
    p.put("nickname", u.getNickname());
    p.put("avatar", u.getAvatar());
    p.put("phone", u.getPhone());
    p.put("birthday", u.getBirthday());
    p.put("country", u.getCountry());
    p.put("emailVerified", u.getEmailVerified() != null && u.getEmailVerified());
    p.put("twoFactorEnabled", u.getTwoFactorEnabled() != null && u.getTwoFactorEnabled());
    p.put("points", u.getPoints());
    p.put("registrationChannel", u.getRegistrationChannel());
    return p;
  }
}
