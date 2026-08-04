package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.ordertag.OrderTagCreateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagUpdateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagVO;
import com.moyuyo.dao.admin.entity.OrderTagEntity;
import com.moyuyo.dao.admin.mapper.OrderTagMapper;
import com.moyuyo.service.admin.AdminOrderTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
  public List<OrderTagVO> listAll() {
    List<OrderTagEntity> tags = orderTagMapper.selectList(
        new LambdaQueryWrapper<OrderTagEntity>()
            .eq(OrderTagEntity::getEnabled, 1)
            .orderByAsc(OrderTagEntity::getSortOrder)
    );
    List<OrderTagVO> list = new ArrayList<>();
    for (OrderTagEntity tag : tags) {
      list.add(toVO(tag));
    }
    return list;
  }

  /**
   * 将实体转换为视图对象,同时统计标签使用次数
   */
  private OrderTagVO toVO(OrderTagEntity tag) {
    OrderTagVO vo = new OrderTagVO();
    vo.setId(tag.getId());
    vo.setName(tag.getName());
    vo.setColor(tag.getColor());
    vo.setDescription(tag.getDescription());
    vo.setSortOrder(tag.getSortOrder());
    vo.setEnabled(tag.getEnabled());
    vo.setStatus(tag.getEnabled() != null && tag.getEnabled() == 1 ? "ENABLED" : "DISABLED");
    vo.setCreateTime(tag.getCreateTime());
    vo.setCreatedAt(tag.getCreateTime());
    // 统计使用次数
    Integer cnt = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mo_order_tag_rel WHERE tag_id = ?", Integer.class, tag.getId());
    vo.setUsageCount(cnt != null ? cnt : 0);
    return vo;
  }

  @Override
  public void create(OrderTagCreateRequest request) {
    OrderTagEntity entity = new OrderTagEntity();
    entity.setName(request.getName());
    entity.setColor(request.getColor());
    entity.setDescription(request.getDescription());
    entity.setSortOrder(request.getSortOrder());
    // 未显式指定启用状态时默认启用
    entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
    orderTagMapper.insert(entity);
  }

  @Override
  public void update(OrderTagUpdateRequest request) {
    OrderTagEntity entity = orderTagMapper.selectById(request.getId());
    if (entity == null) return;
    if (request.getName() != null) entity.setName(request.getName());
    if (request.getColor() != null) entity.setColor(request.getColor());
    if (request.getDescription() != null) entity.setDescription(request.getDescription());
    if (request.getSortOrder() != null) entity.setSortOrder(request.getSortOrder());
    if (request.getEnabled() != null) entity.setEnabled(request.getEnabled());
    orderTagMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    orderTagMapper.deleteById(id);
    // 同时删除关联关系
    jdbcTemplate.update("DELETE FROM mo_order_tag_rel WHERE tag_id = ?", id);
  }

  @Override
  public List<OrderTagVO> getOrderTags(Long orderId) {
    // 查询订单关联的标签ID
    List<java.util.Map<String, Object>> rels = jdbcTemplate.queryForList(
        "SELECT tag_id FROM mo_order_tag_rel WHERE order_id = ?", orderId);
    if (rels.isEmpty()) {
      return new ArrayList<>();
    }
    List<Long> tagIds = rels.stream()
        .map(r -> ((Number) r.get("tag_id")).longValue())
        .collect(Collectors.toList());
    // 查询标签详情
    List<OrderTagEntity> tags = orderTagMapper.selectBatchIds(tagIds);
    List<OrderTagVO> list = new ArrayList<>();
    for (OrderTagEntity tag : tags) {
      OrderTagVO vo = new OrderTagVO();
      vo.setId(tag.getId());
      vo.setName(tag.getName());
      vo.setColor(tag.getColor());
      list.add(vo);
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
