package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台订单标签服务
 */
public interface AdminOrderTagService {

  /**
   * 标签列表
   */
  List<Map<String, Object>> listAll();

  /**
   * 创建标签
   */
  void create(Map<String, Object> data);

  /**
   * 更新标签
   */
  void update(Map<String, Object> data);

  /**
   * 删除标签
   */
  void delete(Long id);

  /**
   * 设置订单标签
   */
  void setOrderTags(Long orderId, List<Long> tagIds);

  /**
   * 获取订单标签
   */
  List<Map<String, Object>> getOrderTags(Long orderId);
}
