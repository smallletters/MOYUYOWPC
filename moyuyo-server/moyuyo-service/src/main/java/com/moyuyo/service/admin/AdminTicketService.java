package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.TicketEntity;
import com.moyuyo.dao.admin.entity.TicketMessageEntity;

import java.time.LocalDateTime;
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

  /**
   * 分配客服（基于 admin_user.id，会校验该用户存在 + 角色属于客服/管理员）
   */
  void assignToOperator(Long id, Long operatorId);

  /**
   * 追加客服回复并自动计算首响耗时（同时把回复写入 mo_ticket_message 表形成历史）
   */
  void appendReply(Long id, String content, LocalDateTime replyAt);

  /**
   * 获取工单的历史消息列表（按时间升序）
   */
  List<TicketMessageEntity> listMessages(Long ticketId);
}
