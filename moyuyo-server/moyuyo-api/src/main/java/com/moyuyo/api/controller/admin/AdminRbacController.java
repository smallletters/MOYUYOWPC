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

  /** 资源名中文映射 */
  private static final Map<String, String> RESOURCE_NAME_MAP = Map.ofEntries(
      Map.entry("products", "商品管理"),
      Map.entry("orders", "订单管理"),
      Map.entry("users", "用户管理"),
      Map.entry("marketing", "营销管理"),
      Map.entry("analytics", "数据统计"),
      Map.entry("system", "系统设置"),
      Map.entry("finance", "财务管理"),
      Map.entry("cms", "内容管理"),
      Map.entry("cs", "客服管理"),
      Map.entry("logistics", "物流管理"),
      Map.entry("inventory", "库存管理"),
      Map.entry("rbac", "权限管理"),
      Map.entry("push", "推送管理"),
      Map.entry("ticket", "工单管理"),
      Map.entry("settings", "设置管理")
  );

  /** 操作类型中文映射 */
  private static final Map<String, String> ACTION_NAME_MAP = Map.of(
      "view", "查看",
      "create", "创建",
      "edit", "编辑",
      "delete", "删除"
  );

  @Operation(summary = "角色列表")
  @GetMapping("/roles")
  public Result<List<Map<String, Object>>> roles() {
    List<AdminRoleEntity> entities = adminRoleService.listRoles();
    List<Map<String, Object>> list = new ArrayList<>();
    for (AdminRoleEntity e : entities) {
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

      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "新建角色")
  @PostMapping("/roles")
  public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> body) {
    AdminRoleEntity entity = new AdminRoleEntity();
    entity.setName((String) body.get("name"));
    entity.setDescription((String) body.get("description"));
    adminRoleService.create(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("name", entity.getName());
    result.put("message", "角色创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新角色")
  @PutMapping("/roles/{id}")
  public Result<Map<String, Object>> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    AdminRoleEntity entity = new AdminRoleEntity();
    entity.setId(id);
    entity.setName((String) body.get("name"));
    entity.setDescription((String) body.get("description"));
    adminRoleService.update(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("name", body.get("name"));
    result.put("message", "角色更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除角色")
  @DeleteMapping("/roles/{id}")
  public Result<Map<String, Object>> deleteRole(@PathVariable Long id) {
    adminRoleService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "角色删除成功");
    return Result.success(result);
  }

  @Operation(summary = "获取角色权限")
  @GetMapping("/roles/{id}/permissions")
  public Result<Map<String, Object>> getPermissions(@PathVariable Long id) {
    List<Long> permissionIds = adminRoleService.getPermissionsByRole(id);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("roleId", id);
    data.put("permissionIds", permissionIds);
    return Result.success(data);
  }

  @Operation(summary = "更新角色权限")
  @PutMapping("/roles/{id}/permissions")
  public Result<Map<String, Object>> updatePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Integer> rawIds = (List<Integer>) body.get("permissionIds");
    List<Long> permissionIds = rawIds != null
        ? rawIds.stream().map(Long::valueOf).toList()
        : List.of();
    adminRoleService.updatePermissions(id, permissionIds);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("roleId", id);
    result.put("message", "权限更新成功");
    return Result.success(result);
  }

  @Operation(summary = "管理员列表")
  @GetMapping("/users")
  public Result<List<Map<String, Object>>> users() {
    try {
      // 从 mo_admin_user 表查询真实的管理员列表
      List<Map<String, Object>> list = adminStaffService.listUsers();
      return Result.success(list);
    } catch (Exception e) {
      return Result.success(Collections.emptyList());
    }
  }

  @Operation(summary = "新建管理员")
  @PostMapping("/users")
  public Result<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
    // 通过服务层创建管理员用户
    Map<String, Object> result = adminStaffService.createUser(body);
    return Result.success(result);
  }

  @Operation(summary = "更新管理员")
  @PutMapping("/users/{id}")
  public Result<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 通过服务层更新管理员用户
    Map<String, Object> result = adminStaffService.updateUser(id, body);
    return Result.success(result);
  }

  @Operation(summary = "获取所有权限列表（用于系统设置页）")
  @GetMapping("/permissions")
  public Result<List<Map<String, Object>>> permissions() {
    // 返回所有可用的权限资源及其操作列表
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
  }
}
