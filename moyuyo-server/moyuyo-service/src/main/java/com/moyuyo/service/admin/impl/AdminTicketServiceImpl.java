package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.TicketEntity;
import com.moyuyo.dao.admin.mapper.TicketMapper;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 管理后台工单服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

  private final TicketMapper ticketMapper;
  private final UserMapper userMapper;

  @Override
  public List<Map<String, Object>> listAll(String status, String type, String priority, String keyword) {
    // 从 mo_ticket 表查询工单数据，支持筛选条件
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
    // 按创建时间倒序排列
    wrapper.orderByDesc(TicketEntity::getCreateTime);

    List<TicketEntity> entityList = ticketMapper.selectList(wrapper);

    // 转换为前端需要的 Map 格式
    List<Map<String, Object>> records = new ArrayList<>();
    for (TicketEntity t : entityList) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("ticketNo", t.getTicketNo());
      item.put("type", t.getType());
      item.put("priority", t.getPriority());
      item.put("title", t.getTitle());
      item.put("user", t.getUserName() != null ? t.getUserName() : "");
      item.put("status", t.getStatus());
      item.put("createTime", t.getCreateTime());
      records.add(item);
    }
    return records;
  }

  @Override
  public TicketEntity getById(Long id) {
    return ticketMapper.selectById(id);
  }

  @Override
  public Map<String, Object> getTicketDetail(Long id) {
    // 查询工单基础信息
    TicketEntity ticket = ticketMapper.selectById(id);
    if (ticket == null) {
      return null;
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

    // 查询关联用户信息
    if (ticket.getUserId() != null) {
      UserEntity user = userMapper.selectById(ticket.getUserId());
      if (user != null) {
        detail.put("userContact", user.getEmail() != null ? user.getEmail() : "");
      } else {
        detail.put("userContact", "");
      }
    } else {
      detail.put("userContact", "");
    }

    // 回复列表（当前无独立回复表，返回空列表）
    detail.put("replies", new ArrayList<>());

    return detail;
  }

  @Override
  public void update(TicketEntity entity) {
    ticketMapper.updateById(entity);
  }

  @Override
  public void assignAgent(Long id, String agent) {
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity != null) {
      entity.setAgentName(agent);
      ticketMapper.updateById(entity);
    }
  }
}
