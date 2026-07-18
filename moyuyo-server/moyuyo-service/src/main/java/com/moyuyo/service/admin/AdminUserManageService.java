package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 管理后台 - 用户管理服务接口
 */
public interface AdminUserManageService {

  /**
   * 获取用户统计面板数据（总用户数、今日新增、活跃用户、会员数等）
   */
  Map<String, Object> getStats();

  /**
   * 分页查询用户列表（含会员等级信息）
   */
  Map<String, Object> listUsers(int page, int size, String search, String level, String status);
}
