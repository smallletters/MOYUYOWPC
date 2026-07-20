package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.service.admin.AdminRoleService;
import com.moyuyo.service.admin.AdminStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 权限管理")
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
public class AdminRbacController {

  private final AdminRoleService adminRoleService;
  private final AdminStaffService adminStaffService;

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
    List<Long> permissionIds = (List<Long>) body.get("permissionIds");
    adminRoleService.updatePermissions(id, permissionIds);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("roleId", id);
    result.put("message", "权限更新成功");
    return Result.success(result);
  }

  @Operation(summary = "管理员列表")
  @GetMapping("/users")
  public Result<List<Map<String, Object>>> users() {
    // 从 mo_admin_user 表查询真实的管理员列表
    List<Map<String, Object>> list = adminStaffService.listUsers();
    return Result.success(list);
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
}
