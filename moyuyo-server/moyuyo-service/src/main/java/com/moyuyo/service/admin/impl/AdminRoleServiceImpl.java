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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;

/**
 * 管理后台角色服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

  private final AdminRoleMapper adminRoleMapper;
  private final AdminPermissionMapper adminPermissionMapper;

  /**
   * 权限ID到(resource, action)的静态映射，与前端PERM_MAP保持一致
   */
  private static final Map<Long, SimpleEntry<String, String>> PERM_MAP;

  static {
    Map<Long, SimpleEntry<String, String>> map = new HashMap<>();
    map.put(1L, new SimpleEntry<>("product", "view"));
    map.put(2L, new SimpleEntry<>("product", "create"));
    map.put(3L, new SimpleEntry<>("product", "edit"));
    map.put(4L, new SimpleEntry<>("product", "delete"));
    map.put(5L, new SimpleEntry<>("order", "view"));
    map.put(6L, new SimpleEntry<>("order", "create"));
    map.put(7L, new SimpleEntry<>("order", "edit"));
    map.put(8L, new SimpleEntry<>("order", "delete"));
    map.put(9L, new SimpleEntry<>("user", "view"));
    map.put(10L, new SimpleEntry<>("user", "create"));
    map.put(11L, new SimpleEntry<>("user", "edit"));
    map.put(12L, new SimpleEntry<>("user", "delete"));
    map.put(13L, new SimpleEntry<>("marketing", "view"));
    map.put(14L, new SimpleEntry<>("marketing", "create"));
    map.put(15L, new SimpleEntry<>("marketing", "edit"));
    map.put(16L, new SimpleEntry<>("marketing", "delete"));
    map.put(17L, new SimpleEntry<>("stats", "view"));
    map.put(18L, new SimpleEntry<>("stats", "create"));
    map.put(19L, new SimpleEntry<>("stats", "edit"));
    map.put(20L, new SimpleEntry<>("stats", "delete"));
    map.put(21L, new SimpleEntry<>("system", "view"));
    map.put(22L, new SimpleEntry<>("system", "create"));
    map.put(23L, new SimpleEntry<>("system", "edit"));
    map.put(24L, new SimpleEntry<>("system", "delete"));
    map.put(25L, new SimpleEntry<>("finance", "view"));
    map.put(26L, new SimpleEntry<>("finance", "create"));
    map.put(27L, new SimpleEntry<>("finance", "edit"));
    map.put(28L, new SimpleEntry<>("finance", "delete"));
    map.put(29L, new SimpleEntry<>("review", "view"));
    map.put(30L, new SimpleEntry<>("review", "create"));
    map.put(31L, new SimpleEntry<>("review", "edit"));
    map.put(32L, new SimpleEntry<>("review", "delete"));
    PERM_MAP = Collections.unmodifiableMap(map);
  }

  @Override
  public List<AdminRoleEntity> listRoles() {
    return adminRoleMapper.selectList(null);
  }

  @Override
  @Transactional
  public void create(AdminRoleEntity entity) {
    adminRoleMapper.insert(entity);
  }

  @Override
  @Transactional
  public void update(AdminRoleEntity entity) {
    adminRoleMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (id == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
    adminRoleMapper.deleteById(id);
    // 级联删除权限
    adminPermissionMapper.delete(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, id)
    );
  }

  @Override
  public List<Long> getPermissionsByRole(Long roleId) {
    if (roleId == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
    List<AdminPermissionEntity> perms = adminPermissionMapper.selectList(
        new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getRoleId, roleId)
    );
    return perms.stream().map(AdminPermissionEntity::getId).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void updatePermissions(Long roleId, List<Long> permissionIds) {
    if (roleId == null) {
      throw new IllegalArgumentException("角色ID不能为空");
    }
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
        // 根据映射表设置resource和action
        Map.Entry<String, String> entry = PERM_MAP.get(permId);
        if (entry != null) {
          perm.setResource(entry.getKey());
          perm.setAction(entry.getValue());
        }
        adminPermissionMapper.insert(perm);
      }
    }
  }
}
