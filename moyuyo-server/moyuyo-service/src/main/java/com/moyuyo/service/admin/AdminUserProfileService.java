package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.UserEntity;

import java.util.Map;

/**
 * 管理后台 - 用户画像服务接口
 */
public interface AdminUserProfileService {

  /**
   * 分页查询用户列表
   */
  Page<UserEntity> listAll(String keyword, Integer status, int page, int size);

  /**
   * 用户统计数据
   */
  Map<String, Object> stats();

  /**
   * 用户详情（含订单数、总消费等）
   */
  Map<String, Object> getDetail(Long id);

  /**
   * 更新用户状态
   */
  void updateStatus(Long id, Integer status);

  /**
   * 删除用户
   */
  void delete(Long id);
}
