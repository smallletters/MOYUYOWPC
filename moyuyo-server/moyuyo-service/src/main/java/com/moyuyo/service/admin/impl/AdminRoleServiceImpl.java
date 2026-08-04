package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
import com.moyuyo.service.admin.AdminPermissionService;
import com.moyuyo.service.admin.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理后台角色服务实现
 * 权限以 "resource:action" 字符串存储与传输（如 products:view），
 * 配置变更后通过 AdminPermissionService 清除对应角色的权限缓存。
 */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

  private final AdminRoleMapper adminRoleMapper;
  private final AdminPermissionMapper adminPermissionMapper;
  private final AdminPermissionService adminPermissionService;

  @Override
  public List<AdminRoleEntity> listRoles() {
    return adminRoleMapper.selectList(null);
  }

  @Override
  @Transactional
  public void create(AdminRoleEntity entity) {
    // 预校验 UNIQUE 字段，避免依赖数据库 DataIntegrityViolationException 返回通用 409，
    // 直接给前端"角色名称/编码已存在"的具体提示
    if (entity.getName() != null) {
      Long sameName = adminRoleMapper.selectCount(
          new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getName, entity.getName()));
      if (sameName != null && sameName > 0) {
        throw new IllegalArgumentException("角色名称已存在: " + entity.getName());
      }
    }
    if (entity.getCode() != null) {
      Long sameCode = adminRoleMapper.selectCount(
          new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getCode, entity.getCode()));
      if (sameCode != null && sameCode > 0) {
        throw new IllegalArgumentException("角色编码已存在: " + entity.getCode());
      }
    }
    adminRoleMapper.insert(entity);
  }

  @Override
  @Transactional
  public void update(AdminRoleEntity entity) {
    // 先查旧角色编码，编码/状态变更后需要清除新旧两个编码的权限缓存
    AdminRoleEntity old = entity.getId() != null ? adminRoleMapper.selectById(entity.getId()) : null;
    adminRoleMapper.updateById(entity);
    if (old != null) {
      adminPermissionService.evict(old.getCode());
    }
    if (entity.getCode() != null && !entity.getCode().equals(old != null ? old.getCode() : null)) {
      adminPermissionService.evict(entity.getCode());
    }
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (id == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
    AdminRoleEntity old = adminRoleMapper.selectById(id);
    adminRoleMapper.deleteById(id);
    // 级联删除权限
    adminPermissionMapper.delete(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, id)
    );
    // 清除权限缓存
    if (old != null) {
      adminPermissionService.evict(old.getCode());
    }
  }

  @Override
  public List<String> getPermissionsByRole(Long roleId) {
    if (roleId == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
    List<AdminPermissionEntity> perms = adminPermissionMapper.selectList(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, roleId)
    );
    // 转为 "resource:action" 权限键
    return perms.stream()
        .filter(p -> p.getResource() != null && p.getAction() != null)
        .map(p -> p.getResource() + ":" + p.getAction())
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void updatePermissions(Long roleId, List<String> permKeys) {
    if (roleId == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
    AdminRoleEntity role = adminRoleMapper.selectById(roleId);
    if (role == null) {
      throw new IllegalArgumentException("角色不存在: " + roleId);
    }
    // 删除旧权限
    adminPermissionMapper.delete(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, roleId)
    );
    // 插入新权限，permKey 格式：resource:action
    if (permKeys != null) {
      for (String permKey : permKeys) {
        if (permKey == null || permKey.isBlank()) {
          continue;
        }
        int sep = permKey.lastIndexOf(':');
        if (sep <= 0 || sep == permKey.length() - 1) {
          throw new IllegalArgumentException("非法权限格式: " + permKey);
        }
        AdminPermissionEntity perm = new AdminPermissionEntity();
        perm.setRoleId(roleId);
        perm.setResource(permKey.substring(0, sep));
        perm.setAction(permKey.substring(sep + 1));
        adminPermissionMapper.insert(perm);
      }
    }
    // 权限变更后清除缓存，立即生效
    adminPermissionService.evict(role.getCode());
  }
}
