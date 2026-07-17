package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
import com.moyuyo.service.admin.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理后台角色服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

  private final AdminRoleMapper adminRoleMapper;
  private final AdminPermissionMapper adminPermissionMapper;

  @Override
  public List<AdminRoleEntity> listRoles() {
    return adminRoleMapper.selectList(null);
  }

  @Override
  public void create(AdminRoleEntity entity) {
    adminRoleMapper.insert(entity);
  }

  @Override
  public void update(AdminRoleEntity entity) {
    adminRoleMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    adminRoleMapper.deleteById(id);
    // 级联删除权限
    adminPermissionMapper.delete(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, id)
    );
  }

  @Override
  public List<Long> getPermissionsByRole(Long roleId) {
    List<AdminPermissionEntity> perms = adminPermissionMapper.selectList(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, roleId)
    );
    return perms.stream().map(AdminPermissionEntity::getId).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void updatePermissions(Long roleId, List<Long> permissionIds) {
    // 删除旧权限
    adminPermissionMapper.delete(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, roleId)
    );
    // 插入新权限
    if (permissionIds != null) {
      for (Long permId : permissionIds) {
        AdminPermissionEntity perm = new AdminPermissionEntity();
        perm.setRoleId(roleId);
        perm.setId(permId);
        adminPermissionMapper.insert(perm);
      }
    }
  }
}
