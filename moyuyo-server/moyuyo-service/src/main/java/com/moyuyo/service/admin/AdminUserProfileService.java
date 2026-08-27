package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.userprofile.UserVisitedPageResponse;
import com.moyuyo.common.dto.admin.userprofile.UserVisitedProductResponse;
import com.moyuyo.dao.entity.UserEntity;

import java.util.List;
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
   * 用户详情（含订单数、总消费、积分、会员等级等）
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

  /**
   * 用户访问过的商品列表（聚合 mo_browsing_history）
   */
  List<UserVisitedProductResponse> listVisitedProducts(Long userId, int size);

  /**
   * 用户访问过的页面列表（聚合 mo_visit_log）
   */
  List<UserVisitedPageResponse> listVisitedPages(Long userId, int size);
}