package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.TicketEntity;

import java.util.List;
import java.util.Map;

/**
 * 管理后台工单服务
 */
public interface AdminTicketService {

  /**
   * 工单列表（支持筛选）
   */
  List<Map<String, Object>> listAll(String status, String type, String priority, String keyword);

  /**
   * 根据ID获取工单
   */
  TicketEntity getById(Long id);

  /**
   * 获取工单详情（含用户信息、回复列表等）
   */
  Map<String, Object> getTicketDetail(Long id);

  /**
   * 更新工单
   */
  void update(TicketEntity entity);

  /**
   * 分配客服
   */
  void assignAgent(Long id, String agent);
}
