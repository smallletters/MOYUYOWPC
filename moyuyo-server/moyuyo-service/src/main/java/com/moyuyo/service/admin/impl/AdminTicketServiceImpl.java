package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.entity.TicketEntity;
import com.moyuyo.dao.admin.entity.TicketMessageEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.dao.admin.mapper.TicketMapper;
import com.moyuyo.dao.admin.mapper.TicketMessageMapper;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台工单服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

  /** SLA 阈值：首响超过此分钟数视为超时 */
  private static final int SLA_MINUTES = 30;

  private final TicketMapper ticketMapper;
  private final TicketMessageMapper ticketMessageMapper;
  private final UserMapper userMapper;
  private final AdminUserMapper adminUserMapper;

  @Override
  public List<Map<String, Object>> listAll(String status, String type, String priority, String keyword) {
    LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(TicketEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(TicketEntity::getType, type);
    }
    if (priority != null && !priority.isEmpty()) {
      wrapper.eq(TicketEntity::getPriority, priority);
    }
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(TicketEntity::getTitle, keyword);
    }
    wrapper.orderByDesc(TicketEntity::getCreateTime);

    List<TicketEntity> entityList = ticketMapper.selectList(wrapper);

    List<Map<String, Object>> records = new ArrayList<>();
    for (TicketEntity t : entityList) {
      records.add(toVoMap(t));
    }
    return records;
  }

  /** 工单实体 -> 前端展示 Map（统一字段命名与派生计算） */
  private Map<String, Object> toVoMap(TicketEntity t) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", t.getId());
    item.put("ticketNo", t.getTicketNo());
    item.put("no", t.getTicketNo());
    item.put("type", t.getType());
    item.put("typeKey", t.getType());
    item.put("typeLabel", getTypeLabel(t.getType()));
    item.put("typeClass", getTypeClass(t.getType()));
    item.put("priority", t.getPriority());
    item.put("priorityKey", t.getPriority());
    item.put("priorityLabel", getPriorityLabel(t.getPriority()));
    item.put("priorityClass", getPriorityClass(t.getPriority()));
    item.put("title", t.getTitle());
    item.put("user", t.getUserName() != null ? t.getUserName() : "");
    item.put("userId", t.getUserId());
    item.put("userName", t.getUserName());
    item.put("agentName", t.getAgentName());
    item.put("status", t.getStatus());
    item.put("statusKey", t.getStatus());
    item.put("statusLabel", getStatusLabel(t.getStatus()));
    item.put("statusDot", getStatusDotClass(t.getStatus()));
    item.put("createTime", t.getCreateTime());
    // 字段语义修正：首响耗时是数值（分钟），回复内容是文本
    item.put("firstResponseMinutes", t.getFirstResponseMinutes());
    item.put("replyContent", t.getReplyContent() != null ? t.getReplyContent() : "");
    // 是否超时基于 SLA 阈值计算（数据库字段保持兼容）
    boolean timeout = t.getFirstResponseMinutes() != null && t.getFirstResponseMinutes() > SLA_MINUTES;
    item.put("timeout", timeout);
    // 给前端同时返回格式化文本（用于列表展示，单位：分钟）
    item.put("responseTime", t.getFirstResponseMinutes() != null ? (t.getFirstResponseMinutes() + "分钟") : "-");
    return item;
  }

  @Override
  public TicketEntity getById(Long id) {
    return ticketMapper.selectById(id);
  }

  @Override
  public Map<String, Object> getTicketDetail(Long id) {
    TicketEntity ticket = ticketMapper.selectById(id);
    if (ticket == null) {
      throw new IllegalArgumentException("工单不存在");
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", ticket.getId());
    detail.put("ticketNo", ticket.getTicketNo());
    detail.put("title", ticket.getTitle());
    detail.put("type", ticket.getType());
    detail.put("priority", ticket.getPriority());
    detail.put("status", ticket.getStatus());
    detail.put("userName", ticket.getUserName() != null ? ticket.getUserName() : "");
    detail.put("assignee", ticket.getAgentName() != null ? ticket.getAgentName() : "");
    detail.put("createTime", ticket.getCreateTime());
    detail.put("replyContent", ticket.getReplyContent() != null ? ticket.getReplyContent() : "");
    detail.put("firstResponseMinutes", ticket.getFirstResponseMinutes());

    if (ticket.getUserId() != null) {
      UserEntity user = userMapper.selectById(ticket.getUserId());
      detail.put("userContact", user != null && user.getEmail() != null ? user.getEmail() : "");
    } else {
      detail.put("userContact", "");
    }

    // 回复历史：当前 reply_content 是合并文本，按行拆成列表供前端时间线展示
    List<String> replies = new ArrayList<>();
    if (ticket.getReplyContent() != null && !ticket.getReplyContent().isEmpty()) {
      for (String line : ticket.getReplyContent().split("\n")) {
        if (!line.isEmpty()) {
          replies.add(line);
        }
      }
    }
    detail.put("replies", replies);
    return detail;
  }

  @Override
  public void update(TicketEntity entity) {
    ticketMapper.updateById(entity);
  }

  /**
   * 工单状态机校验：
   *   PENDING → PROCESSING → CLOSED（单向，不可逆向）
   *  任何跨级跳转或回退都抛 IllegalStateException
   */
  private void ensureStatusTransitionAllowed(String from, String to) {
    if (from == null || to == null) {
      throw new IllegalStateException("状态不能为空");
    }
    if (from.equals(to)) return;
    boolean ok = switch (from) {
      case "PENDING"   -> "PROCESSING".equals(to);
      case "PROCESSING" -> "CLOSED".equals(to);
      case "CLOSED"   -> false;
      default -> false;
    };
    if (!ok) {
      throw new IllegalStateException(
        "工单状态不允许从 [" + from + "] 变更为 [" + to + "]；仅允许 PENDING → PROCESSING → CLOSED 单向流转");
    }
  }

  @Override
  public void assignAgent(Long id, String agent) {
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("工单不存在: " + id);
    }
    // 已关闭工单不能再分配客服
    if ("CLOSED".equals(entity.getStatus())) {
      throw new IllegalStateException("工单已关闭，无法分配客服");
    }
    entity.setAgentName(agent);
    ticketMapper.updateById(entity);
  }

  @Override
  public void assignToOperator(Long id, Long operatorId) {
    if (operatorId == null) {
      throw new IllegalArgumentException("客服ID不能为空");
    }
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("工单不存在: " + id);
    }
    if ("CLOSED".equals(entity.getStatus())) {
      throw new IllegalStateException("工单已关闭，无法分配客服");
    }
    // 通过 admin_user 校验 operatorId 真实存在（避免分配不存在的客服）
    com.moyuyo.dao.admin.entity.AdminUserEntity admin = adminUserMapper.selectById(operatorId);
    if (admin == null) {
      throw new IllegalArgumentException("客服账号不存在: " + operatorId);
    }
    if (admin.getStatus() != null && !"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
      throw new IllegalStateException("客服账号已禁用，无法分配");
    }
    entity.setAgentName(admin.getName() != null ? admin.getName() : ("客服" + operatorId));
    ticketMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void appendReply(Long id, String content, LocalDateTime replyAt) {
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("工单不存在");
    }
    // 已关闭工单不能再追加回复（业务规则：客服关闭后即结束）
    if ("CLOSED".equals(entity.getStatus())) {
      throw new IllegalStateException("工单已关闭，无法继续回复");
    }
    String existing = entity.getReplyContent();
    String timestamp = replyAt.toString().replace("T", " ");
    String replyRecord = "[" + timestamp + "] " + content;
    entity.setReplyContent(
        existing == null || existing.isEmpty() ? replyRecord : existing + "\n" + replyRecord);

    // 首响耗时：首条客服回复 - 工单创建时间（分钟，向下取整）
    if (entity.getFirstResponseMinutes() == null && entity.getCreateTime() != null) {
      long minutes = Duration.between(entity.getCreateTime(), replyAt).toMinutes();
      entity.setFirstResponseMinutes((int) Math.max(minutes, 0));
    }

    // 自动从待处理升级为处理中（不写死状态机，但遵循 PENDING→PROCESSING 单向）
    if ("PENDING".equals(entity.getStatus())) {
      entity.setStatus("PROCESSING");
    }
    ticketMapper.updateById(entity);

    // 同步落库 mo_ticket_message，作为可独立渲染的工单对话历史
    TicketMessageEntity msg = new TicketMessageEntity();
    msg.setTicketId(id);
    msg.setSenderType("AGENT");
    Long operatorId = UserContextHolder.getUserId();
    msg.setSenderId(operatorId);
    msg.setSenderName("客服" + (operatorId == null ? "" : operatorId));
    msg.setContent(content);
    msg.setContentType("TEXT");
    msg.setCreateTime(replyAt);
    ticketMessageMapper.insert(msg);
  }

  @Override
  public List<TicketMessageEntity> listMessages(Long ticketId) {
    if (ticketId == null) return List.of();
    return ticketMessageMapper.selectList(
        new LambdaQueryWrapper<TicketMessageEntity>()
            .eq(TicketMessageEntity::getTicketId, ticketId)
            .orderByAsc(TicketMessageEntity::getCreateTime)
    );
  }

  // ==================== 标签映射辅助方法 ====================

  private String getTypeLabel(String type) {
    if (type == null) return "未知";
    switch (type) {
      case "售后": return "售后问题";
      case "咨询": return "售前咨询";
      case "投诉": return "用户投诉";
      default: return type;
    }
  }

  private String getTypeClass(String type) {
    if (type == null) return "";
    switch (type) {
      case "售后": return "type-aftersale";
      case "咨询": return "type-consult";
      case "投诉": return "type-complaint";
      default: return "";
    }
  }

  private String getPriorityLabel(String priority) {
    if (priority == null) return "未知";
    switch (priority.toUpperCase()) {
      case "HIGH": return "高";
      case "MEDIUM": return "中";
      case "LOW": return "低";
      default: return priority;
    }
  }

  private String getPriorityClass(String priority) {
    if (priority == null) return "";
    switch (priority.toUpperCase()) {
      case "HIGH": return "priority-high";
      case "MEDIUM": return "priority-medium";
      case "LOW": return "priority-low";
      default: return "";
    }
  }

  private String getStatusLabel(String status) {
    if (status == null) return "未知";
    switch (status.toUpperCase()) {
      case "PENDING": return "待处理";
      case "PROCESSING": return "处理中";
      case "CLOSED": return "已关闭";
      case "RESOLVED": return "已完结";
      default: return status;
    }
  }

  private String getStatusDotClass(String status) {
    if (status == null) return "dot-default";
    switch (status.toUpperCase()) {
      case "PENDING": return "dot-warning";
      case "PROCESSING": return "dot-info";
      case "CLOSED": return "dot-success";
      case "RESOLVED": return "dot-success";
      default: return "dot-default";
    }
  }
}