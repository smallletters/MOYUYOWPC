package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
  @GetMapping("/me")
  public Result<Map<String, Object>> me() {
    Long userId = com.moyuyo.common.security.UserContextHolder.getUserId();
    if (userId == null) {
      // JwtAuthFilter 已校验 token,这里兜底返回 401 而非 500
      return Result.error(401, "未登录");
    }
    UserEntity u = userMapper.selectById(userId);
    if (u == null) return Result.error(404, "用户不存在");
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
    return Result.success(p);
  }
}
