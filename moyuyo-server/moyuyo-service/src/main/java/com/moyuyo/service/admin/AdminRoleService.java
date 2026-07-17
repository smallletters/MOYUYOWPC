package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.AdminRoleEntity;

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
   * 删除角色
   */
  void delete(Long id);

  /**
   * 根据角色ID获取权限列表
   */
  List<Long> getPermissionsByRole(Long roleId);

  /**
   * 更新角色权限
   */
  void updatePermissions(Long roleId, List<Long> permissionIds);
}
