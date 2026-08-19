package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.service.admin.AdminUserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserManageService adminUserManageService;

  @Operation(summary = "用户统计数据")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    // 从数据库查询真实的用户和会员统计数据
    Map<String, Object> stats = adminUserManageService.getStats();
    return Result.success(stats);
  }

  @Operation(summary = "用户列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String status) {
    // 分页参数统一守卫：避免 size=100000 触发 OOM / 全表扫描
    int[] pageParams = PageParamGuard.normalize(page, size, 15);
    // 会员等级与注册渠道校验：仅允许已知枚举值，避免任意 SQL 片段注入
    String normalizedLevel = normalizeEnum(level, java.util.Set.of("NORMAL", "SILVER", "GOLD", "DIAMOND"));
    String normalizedChannel = normalizeEnum(channel, java.util.Set.of("web", "app", "wechat"));
    // 状态值映射：前端传 active/banned/inactive -> 后端 ACTIVE/INACTIVE
    String normalizedStatus = mapStatus(status);
    // 从数据库分页查询用户列表（含会员等级信息）
    Map<String, Object> data = adminUserManageService.listUsers(
        pageParams[0], pageParams[1], search, normalizedLevel, normalizedChannel, normalizedStatus);
    return Result.success(data);
  }

  /**
   * 枚举值白名单过滤：仅保留白名单内的合法值，避免任意 SQL 片段注入
   */
  private String normalizeEnum(String value, java.util.Set<String> whitelist) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return whitelist.contains(value) ? value : null;
  }

  /**
   * 状态值映射：前端筛选器用 active/banned/inactive，后端存储用 ACTIVE/INACTIVE
   * 未识别值视为不过滤
   */
  private String mapStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    switch (status) {
      case "active":
        return "ACTIVE";
      case "banned":
      case "inactive":
        return "INACTIVE";
      default:
        return null;
    }
  }

  @Operation(summary = "用户详情")
  @GetMapping("/{id}")
  public Result<Map<String, Object>> detail(@PathVariable Long id) {
    Map<String, Object> detail = adminUserManageService.getUserDetail(id);
    return Result.success(detail);
  }

  @Operation(summary = "更新用户状态")
  @PutMapping("/{id}/status")
  public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
    String status = body.get("status");
    adminUserManageService.updateUserStatus(id, status);
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("id", id);
    result.put("status", status);
    result.put("message", "用户状态更新成功");
    return Result.success(result);
  }

  @Operation(summary = "创建用户")
  @PostMapping("/create")
  public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    try {
      Map<String, Object> result = adminUserManageService.createUser(body);
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    } catch (Exception e) {
      return Result.error("创建用户失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新用户信息")
  @PutMapping("/{id}")
  public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      adminUserManageService.updateUser(id, body);
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "用户信息更新成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    } catch (Exception e) {
      return Result.error("更新用户失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除用户")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    try {
      adminUserManageService.deleteUser(id);
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "用户已删除");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    } catch (Exception e) {
      return Result.error("删除用户失败: " + e.getMessage());
    }
  }

  @Operation(summary = "重置用户密码")
  @PostMapping("/{id}/reset-password")
  public Result<Map<String, Object>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
    try {
      // P0 安全修复：拒绝缺省弱密码兜底，避免 body 缺 password 字段时被设置为 "123456"
      // 强校验由 adminUserManageService.resetPassword 内部抛 IllegalArgumentException
      String newPassword = body.get("password");
      if (newPassword == null || newPassword.isBlank()) {
        return Result.error(400, "新密码不能为空");
      }
      if (newPassword.length() < 12) {
        return Result.error(400, "新密码至少 12 位");
      }
      adminUserManageService.resetPassword(id, newPassword);
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "密码已重置");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    } catch (Exception e) {
      return Result.error("重置密码失败: " + e.getMessage());
    }
  }
}
