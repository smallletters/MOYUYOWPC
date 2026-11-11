package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台客服会话服务
 */
public interface AdminCsSessionService {

  /**
   * 会话列表（分页）
   */
  Map<String, Object> listAll(int page, int size, String status, String sessionId, String userId);

  /**
   * 会话详情
   */
  Map<String, Object> getById(Long id);

  /**
   * 会话对应用户的最近订单（供客服发送订单卡片 / 了解订单上下文）
   */
  List<Map<String, Object>> listUserOrders(Long sessionId, int limit);

  /**
   * 某用户的历史会话列表（客服聊天侧栏切换查看同一用户的其他会话）
   */
  List<Map<String, Object>> listByUserId(Long userId, int limit);

  /**
   * 客服会话统计
   */
  Map<String, Object> getStats();
}
