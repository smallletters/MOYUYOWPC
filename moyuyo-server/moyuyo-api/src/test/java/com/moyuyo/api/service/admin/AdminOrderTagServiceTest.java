package com.moyuyo.api.service.admin;

import com.moyuyo.common.dto.admin.ordertag.OrderTagCreateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagUpdateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagVO;
import com.moyuyo.dao.admin.entity.OrderTagEntity;
import com.moyuyo.dao.admin.mapper.OrderTagMapper;
import com.moyuyo.service.admin.impl.AdminOrderTagServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单标签服务单元测试
 * 覆盖:listAll / create / update / delete / getOrderTags / setOrderTags
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderTagServiceTest {

  @Mock
  private OrderTagMapper orderTagMapper;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private AdminOrderTagServiceImpl adminOrderTagService;

  /** 构造测试用标签实体 */
  private OrderTagEntity buildEntity(Long id, String name, Integer enabled) {
    OrderTagEntity entity = new OrderTagEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setColor("#FF0000");
    entity.setDescription("测试标签");
    entity.setSortOrder(1);
    entity.setEnabled(enabled);
    entity.setCreateTime(LocalDateTime.of(2026, 8, 4, 12, 0, 0));
    return entity;
  }

  @Test
  void listAll_shouldReturnVOListWithUsageCount() {
    // given:数据库返回 2 个启用的标签
    OrderTagEntity tag1 = buildEntity(1L, "加急", 1);
    OrderTagEntity tag2 = buildEntity(2L, "VIP", 1);
    when(orderTagMapper.selectList(any())).thenReturn(List.of(tag1, tag2));
    // 使用次数查询
    when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM mo_order_tag_rel WHERE tag_id = ?"), eq(Integer.class), anyLong()))
        .thenReturn(3, 5);

    // when
    List<OrderTagVO> result = adminOrderTagService.listAll();

    // then
    assertEquals(2, result.size());
    OrderTagVO vo1 = result.get(0);
    assertEquals(1L, vo1.getId());
    assertEquals("加急", vo1.getName());
    assertEquals("#FF0000", vo1.getColor());
    assertEquals("ENABLED", vo1.getStatus());
    assertEquals(3, vo1.getUsageCount());
    // createTime 与 createdAt 应保持一致(前端兼容字段)
    assertEquals(vo1.getCreateTime(), vo1.getCreatedAt());
  }

  @Test
  void listAll_emptyResult_shouldReturnEmptyList() {
    when(orderTagMapper.selectList(any())).thenReturn(List.of());

    List<OrderTagVO> result = adminOrderTagService.listAll();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void create_validRequest_shouldInsertEntityWithDefaultEnabled() {
    // given:name 非空,enabled 未指定
    OrderTagCreateRequest request = new OrderTagCreateRequest();
    request.setName("新标签");
    request.setColor("#00FF00");
    request.setDescription("描述");

    // when
    adminOrderTagService.create(request);

    // then:验证插入的实体 enabled 默认为 1
    ArgumentCaptor<OrderTagEntity> captor = ArgumentCaptor.forClass(OrderTagEntity.class);
    verify(orderTagMapper).insert(captor.capture());
    OrderTagEntity inserted = captor.getValue();
    assertEquals("新标签", inserted.getName());
    assertEquals("#00FF00", inserted.getColor());
    assertEquals(1, inserted.getEnabled());
  }

  @Test
  void create_withExplicitEnabled_shouldRespectRequest() {
    OrderTagCreateRequest request = new OrderTagCreateRequest();
    request.setName("禁用标签");
    request.setEnabled(0);

    adminOrderTagService.create(request);

    ArgumentCaptor<OrderTagEntity> captor = ArgumentCaptor.forClass(OrderTagEntity.class);
    verify(orderTagMapper).insert(captor.capture());
    assertEquals(0, captor.getValue().getEnabled());
  }

  @Test
  void update_existingEntity_shouldUpdateNonNullFields() {
    // given:数据库中已有标签
    OrderTagEntity existing = buildEntity(1L, "旧名称", 1);
    when(orderTagMapper.selectById(1L)).thenReturn(existing);

    OrderTagUpdateRequest request = new OrderTagUpdateRequest();
    request.setId(1L);
    request.setName("新名称");
    request.setEnabled(0);

    // when
    adminOrderTagService.update(request);

    // then:验证更新后的实体
    ArgumentCaptor<OrderTagEntity> captor = ArgumentCaptor.forClass(OrderTagEntity.class);
    verify(orderTagMapper).updateById(captor.capture());
    assertEquals("新名称", captor.getValue().getName());
    assertEquals(0, captor.getValue().getEnabled());
  }

  @Test
  void update_nonExistingEntity_shouldDoNothing() {
    when(orderTagMapper.selectById(anyLong())).thenReturn(null);

    OrderTagUpdateRequest request = new OrderTagUpdateRequest();
    request.setId(999L);
    request.setName("不存在");

    adminOrderTagService.update(request);

    // 不应调用 updateById
    verify(orderTagMapper, never()).updateById(any(OrderTagEntity.class));
  }

  @Test
  void delete_shouldDeleteTagAndRelations() {
    adminOrderTagService.delete(1L);

    // 验证删除标签本身
    verify(orderTagMapper).deleteById(1L);
    // 验证删除关联关系
    verify(jdbcTemplate).update("DELETE FROM mo_order_tag_rel WHERE tag_id = ?", 1L);
  }

  @Test
  void getOrderTags_orderHasTags_shouldReturnTagVOs() {
    // given:订单 100 关联标签 1 和 2
    when(jdbcTemplate.queryForList(anyString(), eq(100L)))
        .thenReturn(List.of(
            Map.of("tag_id", 1L),
            Map.of("tag_id", 2L)
        ));
    OrderTagEntity tag1 = buildEntity(1L, "加急", 1);
    OrderTagEntity tag2 = buildEntity(2L, "VIP", 1);
    when(orderTagMapper.selectBatchIds(anyList())).thenReturn(List.of(tag1, tag2));

    // when
    List<OrderTagVO> result = adminOrderTagService.getOrderTags(100L);

    // then
    assertEquals(2, result.size());
    assertEquals("加急", result.get(0).getName());
    assertEquals("VIP", result.get(1).getName());
  }

  @Test
  void getOrderTags_orderHasNoTags_shouldReturnEmptyList() {
    when(jdbcTemplate.queryForList(anyString(), eq(100L))).thenReturn(List.of());

    List<OrderTagVO> result = adminOrderTagService.getOrderTags(100L);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void setOrderTags_shouldReplaceExistingRelations() {
    // when:为订单 100 设置标签 [1, 2, 3]
    adminOrderTagService.setOrderTags(100L, List.of(1L, 2L, 3L));

    // then:先删除旧关系,再逐条插入新关系
    verify(jdbcTemplate).update("DELETE FROM mo_order_tag_rel WHERE order_id = ?", 100L);
    verify(jdbcTemplate).update("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)", 1L, 100L);
    verify(jdbcTemplate).update("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)", 2L, 100L);
    verify(jdbcTemplate).update("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)", 3L, 100L);
  }

  @Test
  void setOrderTags_emptyTagList_shouldOnlyDeleteRelations() {
    adminOrderTagService.setOrderTags(100L, List.of());

    // 仅删除,不插入
    verify(jdbcTemplate).update("DELETE FROM mo_order_tag_rel WHERE order_id = ?", 100L);
    verify(jdbcTemplate, never()).update(eq("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)"), anyLong(), anyLong());
  }

  @Test
  void setOrderTags_nullTagList_shouldOnlyDeleteRelations() {
    adminOrderTagService.setOrderTags(100L, null);

    verify(jdbcTemplate).update("DELETE FROM mo_order_tag_rel WHERE order_id = ?", 100L);
    verify(jdbcTemplate, never()).update(eq("INSERT INTO mo_order_tag_rel (tag_id, order_id) VALUES (?, ?)"), anyLong(), anyLong());
  }
}
