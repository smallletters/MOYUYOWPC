package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.service.admin.AdminRoleService;
import com.moyuyo.service.admin.AdminStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 权限管理")
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
public class AdminRbacController {

  private final AdminRoleService adminRoleService;
  private final AdminStaffService adminStaffService;
  private final AdminUserMapper adminUserMapper;
  private final AdminPermissionMapper adminPermissionMapper;

  /** 资源名中文映射（key 为 URL 模块段，即权限 resource） */
  private static final Map<String, String> RESOURCE_NAME_MAP = Map.ofEntries(
      Map.entry("dashboard", "仪表盘"),
      Map.entry("analysis", "数据分析"),
      Map.entry("products", "商品管理"),
      Map.entry("product-approval", "商品审批"),
      Map.entry("product-analysis", "商品分析"),
      Map.entry("orders", "订单管理"),
      Map.entry("order-ops", "订单操作"),
      Map.entry("order-tags", "订单标签"),
      Map.entry("users", "用户管理"),
      Map.entry("user-profile", "用户画像"),
      Map.entry("blacklist", "黑名单"),
      Map.entry("crm", "客户管理"),
      Map.entry("marketing", "营销管理"),
      Map.entry("coupons", "优惠券"),
      Map.entry("flash-sales", "秒杀活动"),
      Map.entry("live", "直播管理"),
      Map.entry("push", "推送管理"),
      Map.entry("sms", "短信管理"),
      Map.entry("finance", "财务管理"),
      Map.entry("settlement", "结算管理"),
      Map.entry("refunds", "退款管理"),
      Map.entry("review", "评价管理"),
      Map.entry("content-review", "内容审核"),
      Map.entry("cms", "内容管理"),
      Map.entry("knowledge-base", "知识库"),
      Map.entry("cs-sessions", "客服会话"),
      Map.entry("ticket", "工单管理"),
      Map.entry("complaint", "投诉管理"),
      Map.entry("satisfaction", "满意度"),
      Map.entry("logistics", "物流管理"),
      Map.entry("inventory", "库存管理"),
      Map.entry("inventory-transfer", "库存调拨"),
      Map.entry("price", "价格管理"),
      Map.entry("tariff", "关税管理"),
      Map.entry("points", "积分管理"),
      Map.entry("risk", "风控管理"),
      Map.entry("risk-alert", "风控预警"),
      Map.entry("gdpr", "GDPR合规"),
      Map.entry("sensitive", "敏感词"),
      Map.entry("rbac", "权限管理"),
      Map.entry("system", "系统管理"),
      Map.entry("system-info", "系统信息"),
      Map.entry("settings", "系统设置"),
      Map.entry("app-version", "版本管理"),
      Map.entry("batch-import", "批量导入"),
      Map.entry("audit-log", "审计日志")
  );

  /** 操作类型中文映射 */
  private static final Map<String, String> ACTION_NAME_MAP = Map.of(
      "view", "查看",
      "create", "创建",
      "edit", "编辑",
      "delete", "删除"
  );

  // ==================== 角色管理 ====================

  @Operation(summary = "角色列表")
  @GetMapping("/roles")
  public Result<List<Map<String, Object>>> roles() {
    try {
      List<AdminRoleEntity> entities = adminRoleService.listRoles();
      List<Map<String, Object>> list = new ArrayList<>();
      for (AdminRoleEntity e : entities) {
        Map<String, Object> item = buildRoleItem(e);
        list.add(item);
      }
      return Result.success(list);
    } catch (Exception e) {
      return Result.error("查询角色列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "新建角色")
  @PostMapping("/roles")
  public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> body) {
    try {
      // 前置参数校验：name/code 为 NOT NULL UNIQUE 字段，空 body 时直接返回友好错误而非数据库异常
      String name = (String) body.get("name");
      if (name == null || name.trim().isEmpty()) {
        return Result.error("角色名称不能为空");
      }
      AdminRoleEntity entity = new AdminRoleEntity();
      entity.setName(name);
      // code 字段在数据库中为 UNIQUE NOT NULL，必须有值；缺省时使用 name 作为 code
      String code = (String) body.get("code");
      if (code == null || code.isEmpty()) {
        code = entity.getName();
      }
      entity.setCode(code);
      entity.setDescription((String) body.get("description"));
      Object statusVal = body.get("status");
      if (statusVal != null) {
        entity.setStatus(statusVal.toString());
      }
      Object sortVal = body.get("sortOrder");
      if (sortVal instanceof Number) {
        entity.setSortOrder(((Number) sortVal).intValue());
      }
      adminRoleService.create(entity);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entity.getId());
      result.put("name", entity.getName());
      result.put("code", entity.getCode());
      result.put("message", "角色创建成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      // 业务校验异常（如角色名/编码已存在）— 走业务错误 400
      return Result.error(400, e.getMessage());
    }
    // DataIntegrityViolationException 等数据异常由 GlobalExceptionHandler 统一转为 409，不在此吞掉以避免泄漏 SQL 细节
  }

  @Operation(summary = "更新角色")
  @PutMapping("/roles/{id}")
  public Result<Map<String, Object>> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      AdminRoleEntity entity = new AdminRoleEntity();
      entity.setId(id);
      entity.setName((String) body.get("name"));
      // code 也需写入，避免被 NULL 覆盖
      String code = (String) body.get("code");
      if (code == null || code.isEmpty()) {
        code = entity.getName();
      }
      entity.setCode(code);
      entity.setDescription((String) body.get("description"));
      Object statusVal = body.get("status");
      if (statusVal != null) {
        entity.setStatus(statusVal.toString());
      }
      Object sortVal = body.get("sortOrder");
      if (sortVal instanceof Number) {
        entity.setSortOrder(((Number) sortVal).intValue());
      }
      adminRoleService.update(entity);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("name", body.get("name"));
      result.put("message", "角色更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新角色失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除角色")
  @DeleteMapping("/roles/{id}")
  public Result<Map<String, Object>> deleteRole(@PathVariable Long id) {
    try {
      adminRoleService.delete(id);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "角色删除成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("删除角色失败: " + e.getMessage());
    }
  }

  @Operation(summary = "获取角色权限")
  @GetMapping("/roles/{id}/permissions")
  public Result<Map<String, Object>> getPermissions(@PathVariable Long id) {
    try {
      List<String> permKeys = adminRoleService.getPermissionsByRole(id);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("roleId", id);
      data.put("permKeys", permKeys);
      return Result.success(data);
    } catch (Exception e) {
      return Result.error("获取角色权限失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新角色权限")
  @PutMapping("/roles/{id}/permissions")
  public Result<Map<String, Object>> updatePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      // permKeys 格式：["resource:action", ...]，如 ["products:view", "orders:edit"]
      List<String> permKeys = new ArrayList<>();
      Object rawKeys = body.get("permKeys");
      if (rawKeys instanceof List) {
        for (Object item : (List<?>) rawKeys) {
          if (item != null) {
            permKeys.add(item.toString());
          }
        }
      }
      adminRoleService.updatePermissions(id, permKeys);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("roleId", id);
      result.put("message", "权限更新成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    } catch (Exception e) {
      return Result.error("更新角色权限失败: " + e.getMessage());
    }
  }

  // ==================== 管理员管理 ====================

  @Operation(summary = "管理员列表（分页）")
  @GetMapping("/users")
  public Result<Map<String, Object>> users(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      return Result.success(adminStaffService.listUsersPage(page, size));
    } catch (Exception e) {
      return Result.error("查询管理员列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "管理员详情")
  @GetMapping("/users/{id}")
  public Result<Map<String, Object>> userDetail(@PathVariable Long id) {
    try {
      AdminUserEntity user = adminUserMapper.selectById(id);
      if (user == null) {
        return Result.error("管理员不存在");
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", user.getId());
      item.put("name", user.getName());
      item.put("username", user.getUsername());
      item.put("email", user.getEmail());
      item.put("role", user.getRole());
      item.put("status", user.getStatus());
      item.put("lastLogin", user.getLastLoginTime());
      item.put("createTime", user.getCreateTime());
      return Result.success(item);
    } catch (Exception e) {
      return Result.error("查询管理员详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "新建管理员")
  @PostMapping("/users")
  public Result<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
    try {
      Map<String, Object> result = adminStaffService.createUser(body);
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("创建管理员失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新管理员")
  @PutMapping("/users/{id}")
  public Result<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      Map<String, Object> result = adminStaffService.updateUser(id, body);
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新管理员失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除管理员")
  @DeleteMapping("/users/{id}")
  public Result<Map<String, Object>> deleteUser(@PathVariable Long id) {
    try {
      adminStaffService.deleteUser(id);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "管理员删除成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("删除管理员失败: " + e.getMessage());
    }
  }

  @Operation(summary = "重置管理员密码")
  @PostMapping("/users/{id}/reset-password")
  public Result<Map<String, Object>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
    try {
      String newPassword = body.getOrDefault("password", "123456");
      adminStaffService.resetPassword(id, newPassword);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "密码已重置");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("重置密码失败: " + e.getMessage());
    }
  }

  // ==================== 权限列表 ====================

  @Operation(summary = "获取所有权限列表（用于系统设置页）")
  @GetMapping("/permissions")
  public Result<List<Map<String, Object>>> permissions() {
    try {
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (Map.Entry<String, String> entry : RESOURCE_NAME_MAP.entrySet()) {
        for (Map.Entry<String, String> actionEntry : ACTION_NAME_MAP.entrySet()) {
          Map<String, Object> perm = new LinkedHashMap<>();
          perm.put("resource", entry.getKey());
          perm.put("resourceName", entry.getValue());
          perm.put("action", actionEntry.getKey());
          perm.put("actionName", actionEntry.getValue());
          perm.put("name", entry.getValue() + "-" + actionEntry.getValue());
          permissionList.add(perm);
        }
      }
      return Result.success(permissionList);
    } catch (Exception e) {
      return Result.error("获取权限列表失败: " + e.getMessage());
    }
  }

  // ==================== 辅助方法 ====================

  /** 构建角色展示项 */
  private Map<String, Object> buildRoleItem(AdminRoleEntity e) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", e.getId());
    item.put("name", e.getName());
    item.put("description", e.getDescription());
    item.put("status", e.getStatus());
    item.put("createTime", e.getCreateTime());

    // 统计该角色下的管理员用户数量
    Long userCount = adminUserMapper.selectCount(
        new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getRole, e.getName())
    );
    item.put("userCount", userCount != null ? userCount.intValue() : 0);

    // 是否系统预设角色
    item.put("isPreset", e.getIsPreset() != null ? e.getIsPreset() : false);

    // 该角色拥有的权限名称列表，如 ["商品管理-查看", "订单管理-编辑"]
    List<AdminPermissionEntity> perms = adminPermissionMapper.selectList(
        new LambdaQueryWrapper<AdminPermissionEntity>()
            .eq(AdminPermissionEntity::getRoleId, e.getId())
    );
    List<String> permNames = perms.stream()
        .map(p -> {
          String resourceName = RESOURCE_NAME_MAP.getOrDefault(p.getResource(), p.getResource());
          String actionName = ACTION_NAME_MAP.getOrDefault(p.getAction(), p.getAction());
          return resourceName + "-" + actionName;
        })
        .collect(Collectors.toList());
    item.put("permissions", permNames);

    return item;
  }
}
