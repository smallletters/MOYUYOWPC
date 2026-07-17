package com.moyuyo.service.admin.impl;

import com.moyuyo.dao.admin.entity.TicketEntity;
import com.moyuyo.service.admin.AdminTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台工单服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

  @Override
  public List<Map<String, Object>> listAll(String status, String type, String priority, String keyword) {
    List<Map<String, Object>> records = new ArrayList<>();
    String[][] data = {
        {"TK-001", "退款", "高", "商品质量问题", "Alice", "PENDING"},
        {"TK-002", "物流", "中", "快递丢失", "Bob", "PROCESSING"},
        {"TK-003", "咨询", "低", "尺码推荐", "Charlie", "CLOSED"},
        {"TK-004", "投诉", "高", "客服态度差", "David", "PROCESSING"},
        {"TK-005", "退款", "中", "申请仅退款", "Eva", "PENDING"},
    };
    for (String[] t : data) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("ticketNo", t[0]);
      item.put("type", t[1]);
      item.put("priority", t[2]);
      item.put("title", t[3]);
      item.put("user", t[4]);
      item.put("status", t[5]);
      item.put("createTime", LocalDateTime.now().minusHours((long) (Math.random() * 48)));
      records.add(item);
    }
    return records;
  }

  @Override
  public TicketEntity getById(Long id) {
    return null;
  }

  @Override
  public void update(TicketEntity entity) {
    // 待实现：调用 TicketMapper.updateById(entity)
  }

  @Override
  public void assignAgent(Long id, String agent) {
    // 待实现：分配客服后更新工单
  }
}
