package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.service.admin.AdminStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台管理员用户服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminStaffServiceImpl implements AdminStaffService {

  private final AdminUserMapper adminUserMapper;

  @Override
  public List<Map<String, Object>> listUsers() {
    // 查询所有管理员用户，按创建时间倒序
    List<AdminUserEntity> entities = adminUserMapper.selectList(
        new LambdaQueryWrapper<AdminUserEntity>()
            .orderByDesc(AdminUserEntity::getCreatedAt)
    );

    List<Map<String, Object>> list = new ArrayList<>();
    for (AdminUserEntity user : entities) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", user.getId());
      item.put("name", user.getName());
      item.put("email", user.getEmail());
      item.put("role", user.getRole());
      item.put("status", user.getStatus());
      item.put("lastLogin", user.getLastLoginTime());
      list.add(item);
    }
    return list;
  }

  @Override
  @Transactional
  public Map<String, Object> createUser(Map<String, Object> body) {
    AdminUserEntity entity = new AdminUserEntity();
    entity.setUsername((String) body.get("username"));
    entity.setName((String) body.get("name"));
    entity.setEmail((String) body.get("email"));
    entity.setRole((String) body.get("role"));

    // 密码使用 BCrypt 加密存储
    String password = (String) body.get("password");
    if (password != null && !password.isEmpty()) {
      BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
      entity.setPassword(encoder.encode(password));
    }

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

    // 更新字段
    if (body.containsKey("name")) {
      entity.setName((String) body.get("name"));
    }
    if (body.containsKey("email")) {
      entity.setEmail((String) body.get("email"));
    }
    if (body.containsKey("role")) {
      entity.setRole((String) body.get("role"));
    }
    if (body.containsKey("status")) {
      entity.setStatus((String) body.get("status"));
    }
    if (body.containsKey("password")) {
      String password = (String) body.get("password");
      if (password != null && !password.isEmpty()) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        entity.setPassword(encoder.encode(password));
      }
    }

    adminUserMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("name", entity.getName());
    result.put("message", "管理员更新成功");
    return result;
  }
}
