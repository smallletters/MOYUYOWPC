package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.service.admin.AdminStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台管理员用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStaffServiceImpl implements AdminStaffService {

  /** 系统允许的合法角色编码白名单（与 Flyway 种子数据保持一致） */
  private static final java.util.Set<String> ALLOWED_ROLES = java.util.Set.of(
      "SUPER_ADMIN", "OPERATOR", "CUSTOMER_SVC", "FINANCE", "VIEWER");

  /**
   * 校验角色编码是否在白名单内（RBAC 与 admin_user.role 字段一致性约束）
   * 任何新增/修改管理员都必须传入合法的角色编码；否则拒绝
   */
  private static void validateRoleOrThrow(String role) {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("角色不能为空");
    }
    if (!ALLOWED_ROLES.contains(role)) {
      throw new IllegalArgumentException(
        "无效的角色编码: " + role + "；可选值：" + ALLOWED_ROLES);
    }
  }

  private final AdminUserMapper adminUserMapper;
  /** 统一密码编码器 Bean，强度由 moyuyo.password.bcrypt-strength 控制（默认 12） */
  private final PasswordEncoder passwordEncoder;

  @Override
  public List<Map<String, Object>> listUsers() {
    // 查询所有管理员用户，按创建时间倒序
    List<AdminUserEntity> entities = adminUserMapper.selectList(
        new LambdaQueryWrapper<AdminUserEntity>()
            .orderByDesc(AdminUserEntity::getCreateTime)
    );

    List<Map<String, Object>> list = new ArrayList<>();
    for (AdminUserEntity user : entities) {
      list.add(toItem(user));
    }
    return list;
  }

  @Override
  public Map<String, Object> listUsersPage(int page, int size) {
    Page<AdminUserEntity> pageObj = new Page<>(page, size);
    Page<AdminUserEntity> result = adminUserMapper.selectPage(pageObj,
        new LambdaQueryWrapper<AdminUserEntity>().orderByDesc(AdminUserEntity::getCreateTime));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", result.getTotal());
    data.put("records", result.getRecords().stream().map(this::toItem).collect(java.util.stream.Collectors.toList()));
    return data;
  }

  @Override
  @Transactional
  public Map<String, Object> createUser(Map<String, Object> body) {
    // 必填字段非空校验
    String username = (String) body.get("username");
    String name = (String) body.get("name");
    String email = (String) body.get("email");
    String password = (String) body.get("password");
    String targetRole = (String) body.get("role");
    if (isBlank(username) || isBlank(name) || isBlank(email) || isBlank(password)) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("message", "用户名、姓名、邮箱和密码不能为空");
      return error;
    }

    // 1. 角色白名单校验（在 body 校验前先做，全局统一一处）
    validateRoleOrThrow(targetRole);
    // 2. 权限提升防护：非 SUPER_ADMIN 操作者禁止创建 SUPER_ADMIN 账号（最小权限原则）
    String operatorRole = com.moyuyo.common.security.UserContextHolder.getRole();
    if ("SUPER_ADMIN".equals(targetRole) && !"SUPER_ADMIN".equals(operatorRole)) {
      log.warn("非超级管理员 [{}] 尝试创建 SUPER_ADMIN 账号 [{}]，已拒绝", operatorRole, username);
      throw new org.springframework.security.access.AccessDeniedException("无权创建超级管理员账号");
    }

    AdminUserEntity entity = new AdminUserEntity();
    entity.setUsername(username);
    entity.setName(name);
    entity.setEmail(email);
    entity.setRole(targetRole);

    // 密码使用 BCrypt 加密存储，强度由 moyuyo.password.bcrypt-strength 控制（默认 12）
    entity.setPassword(passwordEncoder.encode(password));

    entity.setStatus("ACTIVE");
    adminUserMapper.insert(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("name", entity.getName());
    result.put("message", "管理员创建成功");
    return result;
  }

  @Override
  @Transactional
  public Map<String, Object> updateUser(Long id, Map<String, Object> body) {
    AdminUserEntity entity = adminUserMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("管理员不存在");
    }

    // 更新字段，非空校验（密码可为空，表示不修改）
    if (body.containsKey("name")) {
      String name = (String) body.get("name");
      if (isBlank(name)) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", "姓名不能为空");
        return error;
      }
      entity.setName(name);
    }
    if (body.containsKey("email")) {
      String email = (String) body.get("email");
      if (isBlank(email)) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", "邮箱不能为空");
        return error;
      }
      entity.setEmail(email);
    }
    if (body.containsKey("role")) {
      String newRole = (String) body.get("role");
      // 1. 角色白名单校验
      validateRoleOrThrow(newRole);
      // 2. 权限提升防护：非 SUPER_ADMIN 操作者不得将任何人角色变更为 SUPER_ADMIN
      String operatorRole = com.moyuyo.common.security.UserContextHolder.getRole();
      boolean upgradingToSuper = "SUPER_ADMIN".equals(newRole) && !"SUPER_ADMIN".equals(entity.getRole());
      if (upgradingToSuper && !"SUPER_ADMIN".equals(operatorRole)) {
        log.warn("非超级管理员 [{}] 尝试将管理员 [{}] 角色从 [{}] 提升为 SUPER_ADMIN，已拒绝",
            operatorRole, entity.getUsername(), entity.getRole());
        throw new org.springframework.security.access.AccessDeniedException("无权提升为超级管理员角色");
      }
      // 3. 降级防护：非 SUPER_ADMIN 操作者不得修改 SUPER_ADMIN 的角色（防止移除权限后重建提权）
      if ("SUPER_ADMIN".equals(entity.getRole()) && !"SUPER_ADMIN".equals(operatorRole)) {
        log.warn("非超级管理员 [{}] 尝试修改 SUPER_ADMIN [{}] 的角色为 [{}]，已拒绝",
            operatorRole, entity.getUsername(), newRole);
        throw new org.springframework.security.access.AccessDeniedException("无权修改超级管理员的角色");
      }
      entity.setRole(newRole);
    }
    if (body.containsKey("status")) {
      entity.setStatus((String) body.get("status"));
    }
    if (body.containsKey("password")) {
      String password = (String) body.get("password");
      // 密码可以为空，表示不修改密码
      if (password != null && !password.isEmpty()) {
        entity.setPassword(passwordEncoder.encode(password));
      }
    }

    adminUserMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("name", entity.getName());
    result.put("message", "管理员更新成功");
    return result;
  }

  /**
   * 判断字符串是否为空或空白
   */
  private boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }

  /** 将管理员实体转为前端展示用Map */
  private Map<String, Object> toItem(AdminUserEntity user) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", user.getId());
    item.put("name", user.getName());
    item.put("email", user.getEmail());
    item.put("role", user.getRole());
    item.put("status", user.getStatus());
    item.put("lastLogin", user.getLastLoginTime());
    return item;
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    AdminUserEntity entity = adminUserMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("管理员不存在");
    }
    // 删除超级管理员仅允许 SUPER_ADMIN 操作
    String operatorRole = com.moyuyo.common.security.UserContextHolder.getRole();
    if ("SUPER_ADMIN".equals(entity.getRole()) && !"SUPER_ADMIN".equals(operatorRole)) {
      log.warn("非超级管理员 [{}] 尝试删除 SUPER_ADMIN [{}]，已拒绝", operatorRole, entity.getUsername());
      throw new org.springframework.security.access.AccessDeniedException("无权删除超级管理员");
    }
    // 保护系统唯一超级管理员：剩余 SUPER_ADMIN 数量 ≤1 时禁止删除，避免系统失去最高权限账号
    if ("SUPER_ADMIN".equals(entity.getRole())) {
      Long superAdminCount = adminUserMapper.selectCount(
          new LambdaQueryWrapper<AdminUserEntity>().eq(AdminUserEntity::getRole, "SUPER_ADMIN"));
      if (superAdminCount != null && superAdminCount <= 1) {
        log.warn("尝试删除最后一个 SUPER_ADMIN [{}]，已拒绝（系统必须保留至少 1 个超级管理员）", entity.getUsername());
        throw new IllegalArgumentException("系统必须保留至少 1 名超级管理员，无法删除最后一个");
      }
    }
    adminUserMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void resetPassword(Long id, String newPassword) {
    if (newPassword == null || newPassword.trim().isEmpty()) {
      throw new IllegalArgumentException("新密码不能为空");
    }
    AdminUserEntity entity = adminUserMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("管理员不存在");
    }
    // 重置超级管理员密码仅允许 SUPER_ADMIN 操作
    String operatorRole = com.moyuyo.common.security.UserContextHolder.getRole();
    if ("SUPER_ADMIN".equals(entity.getRole()) && !"SUPER_ADMIN".equals(operatorRole)) {
      log.warn("非超级管理员 [{}] 尝试重置 SUPER_ADMIN [{}] 的密码，已拒绝", operatorRole, entity.getUsername());
      throw new org.springframework.security.access.AccessDeniedException("无权重置超级管理员的密码");
    }
    // 重置密码使用统一密码编码器，强度由 moyuyo.password.bcrypt-strength 控制
    entity.setPassword(passwordEncoder.encode(newPassword));
    adminUserMapper.updateById(entity);
  }
}
