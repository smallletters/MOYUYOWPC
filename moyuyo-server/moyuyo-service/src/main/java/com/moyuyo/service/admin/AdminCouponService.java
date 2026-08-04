package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台优惠券服务
 */
public interface AdminCouponService {

  /**
   * 优惠券列表（全部）
   */
  List<Map<String, Object>> listAll();

  /**
   * 优惠券分页列表
   */
  Map<String, Object> listPage(int page, int size);

  /**
   * 根据ID查询优惠券详情
   */
  Map<String, Object> getById(Long id);

  /**
   * 创建优惠券
   */
  void create(Map<String, Object> data);

  /**
   * 更新优惠券
   */
  void update(Map<String, Object> data);

  /**
   * 删除优惠券
   */
  void delete(Long id);

  /**
   * 优惠券统计
   */
  Map<String, Object> getStats();
}
