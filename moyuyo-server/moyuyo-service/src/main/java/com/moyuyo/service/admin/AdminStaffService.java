package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 管理员用户服务接口
 */
public interface AdminStaffService {

  /**
   * 获取所有管理员用户列表
   */
  List<Map<String, Object>> listUsers();

  /**
   * 创建管理员用户
   */
  Map<String, Object> createUser(Map<String, Object> body);

  /**
   * 更新管理员用户
   */
  Map<String, Object> updateUser(Long id, Map<String, Object> body);
}
