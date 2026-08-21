package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.entity.AdminUserEntity;

import java.util.List;

/**
 * 管理后台角色服务
 */
public interface AdminRoleService {

  /**
   * 获取角色列表
   */
  List<AdminRoleEntity> listRoles();

  /**
   * 创建角色
   */
  void create(AdminRoleEntity entity);

  /**
   * 更新角色
   */
  void update(AdminRoleEntity entity);

  /**
   * 删除角色（系统预设角色禁止删除）
   */
  void delete(Long id);

  /**
   * 根据角色ID获取权限键列表（格式：resource:action，如 products:view）
   */
  List<String> getPermissionsByRole(Long roleId);

  /**
   * 更新角色权限（permKeys 格式：resource:action，如 products:view）
   */
  void updatePermissions(Long roleId, List<String> permKeys);

  /**
   * 列出绑定该角色（按 name 匹配）的所有 ACTIVE 管理员
   *
   * @param roleName 角色名称（与 admin_user.role 字段匹配）
   * @return 管理员列表
   */
  List<AdminUserEntity> listAdminsByRoleName(String roleName);
}
