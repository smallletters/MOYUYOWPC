package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
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
    // 从数据库分页查询用户列表（含会员等级信息）
    Map<String, Object> data = adminUserManageService.listUsers(page, size, search, level, status);
    return Result.success(data);
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
      String newPassword = body.getOrDefault("password", "123456");
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
