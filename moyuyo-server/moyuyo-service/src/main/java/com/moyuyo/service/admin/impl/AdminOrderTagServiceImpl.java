package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.OrderTagEntity;
import com.moyuyo.dao.admin.mapper.OrderTagMapper;
import com.moyuyo.service.admin.AdminOrderTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单标签服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminOrderTagServiceImpl implements AdminOrderTagService {

  private final OrderTagMapper orderTagMapper;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public List<Map<String, Object>> listAll() {
    List<OrderTagEntity> tags = orderTagMapper.selectList(
        new LambdaQueryWrapper<OrderTagEntity>()
            .eq(OrderTagEntity::getStatus, "ENABLED")
            .orderByAsc(OrderTagEntity::getSortOrder)
    );
    List<Map<String, Object>> list = new ArrayList<>();
    for (OrderTagEntity tag : tags) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", tag.getId());
      item.put("tagName", tag.getTagName());
      item.put("tagColor", tag.getTagColor());
      item.put("sortOrder", tag.getSortOrder());
      item.put("status", tag.getStatus());
      item.put("createTime", tag.getCreateTime());
      list.add(item);
    }
    return list;
  }

  @Override
  public void create(Map<String, Object> data) {
    OrderTagEntity entity = new OrderTagEntity();
    if (data.get("tagName") != null) entity.setTagName((String) data.get("tagName"));
    if (data.get("tagColor") != null) entity.setTagColor((String) data.get("tagColor"));
    if (data.get("sortOrder") != null) entity.setSortOrder(Integer.valueOf(data.get("sortOrder").toString()));
    entity.setStatus("ENABLED");
    orderTagMapper.insert(entity);
  }

  @Override
  public void update(Map<String, Object> data) {
    if (data.get("id") == null) return;
    OrderTagEntity entity = orderTagMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("tagName") != null) entity.setTagName((String) data.get("tagName"));
    if (data.get("tagColor") != null) entity.setTagColor((String) data.get("tagColor"));
    if (data.get("sortOrder") != null) entity.setSortOrder(Integer.valueOf(data.get("sortOrder").toString()));
    if (data.get("status") != null) entity.setStatus((String) data.get("status"));
    orderTagMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    orderTagMapper.deleteById(id);
    // 同时删除关联关系
    jdbcTemplate.update("DELETE FROM mo_order_tag_rel WHERE tag_id = ?", id);
  }

  @Override
  public List<Map<String, Object>> getOrderTags(Long orderId) {
    // 查询订单关联的标签ID
    List<Map<String, Object>> rels = jdbcTemplate.queryForList(
        "SELECT tag_id FROM mo_order_tag_rel WHERE order_id = ?", orderId);
    if (rels.isEmpty()) {
      return new ArrayList<>();
    }
    List<Long> tagIds = rels.stream()
        .map(r -> ((Number) r.get("tag_id")).longValue())
        .collect(Collectors.toList());
    // 查询标签详情
    List<OrderTagEntity> tags = orderTagMapper.selectBatchIds(tagIds);
    List<Map<String, Object>> list = new ArrayList<>();
    for (OrderTagEntity tag : tags) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", tag.getId());
      item.put("tagName", tag.getTagName());
      item.put("tagColor", tag.getTagColor());
      list.add(item);
    }
    return list;
  }

  @Override
  public void setOrderTags(Long orderId, List<Long> tagIds) {
    // 删除旧的关联关系
    jdbcTemplate.update("DELETE FROM mo_order_tag_rel WHERE order_id = ?", orderId);
    // 插入新的关联关系
    if (tagIds != null && !tagIds.isEmpty()) {
      for (Long tagId : tagIds) {
        jdbcTemplate.update("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)", tagId, orderId);
      }
    }
  }
}
