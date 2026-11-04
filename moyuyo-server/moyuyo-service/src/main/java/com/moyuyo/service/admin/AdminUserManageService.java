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
   * 分页查询用户列表（含会员等级与注册渠道过滤）
   */
  Map<String, Object> listUsers(int page, int size, String search, String level, String channel, String status);

  /**
   * 获取用户详情
   */
  Map<String, Object> getUserDetail(Long id);

  /**
   * 更新用户状态（封禁/解封）
   *
   * @param id 用户ID
   * @param status ACTIVE 解封 / INACTIVE 封禁
   * @param banReason 封禁原因（封禁时必填，解封时忽略）
   * @param banDurationDays 封禁天数：>0 为临时封禁，0 或 null 为永久封禁
   */
  void updateUserStatus(Long id, String status, String banReason, Integer banDurationDays);

  /**
   * 创建用户
   */
  Map<String, Object> createUser(Map<String, Object> body);

  /**
   * 更新用户信息
   */
  void updateUser(Long id, Map<String, Object> body);

  /**
   * 删除用户
   */
  void deleteUser(Long id);

  /**
   * 重置用户密码
   */
  void resetPassword(Long id, String newPassword);
}
