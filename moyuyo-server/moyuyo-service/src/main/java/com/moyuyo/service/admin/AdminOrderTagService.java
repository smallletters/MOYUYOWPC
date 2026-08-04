package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.ordertag.OrderTagCreateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagUpdateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagVO;

import java.util.List;

/**
 * 管理后台订单标签服务
 */
public interface AdminOrderTagService {

  /**
   * 标签列表
   */
  List<OrderTagVO> listAll();

  /**
   * 创建标签
   */
  void create(OrderTagCreateRequest request);

  /**
   * 更新标签
   */
  void update(OrderTagUpdateRequest request);

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
  List<OrderTagVO> getOrderTags(Long orderId);
}
