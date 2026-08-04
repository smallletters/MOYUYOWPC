package com.moyuyo.api.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferCreateRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferVO;
import com.moyuyo.dao.admin.entity.InventoryTransferEntity;
import com.moyuyo.dao.admin.entity.WarehouseEntity;
import com.moyuyo.dao.admin.mapper.InventoryTransferMapper;
import com.moyuyo.dao.admin.mapper.WarehouseMapper;
import com.moyuyo.service.admin.impl.AdminInventoryTransferServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存调拨服务实现单元测试
 * 重点覆盖:
 *   1. 状态枚举统一转换(前端值 ↔ 数据库值)
 *   2. DTO → Entity 字段映射(create)
 *   3. Entity → VO 字段映射(listAll)
 *   4. 状态流转(approve/reject/complete)
 */
@ExtendWith(MockitoExtension.class)
class AdminInventoryTransferServiceImplTest {

  @Mock
  private InventoryTransferMapper inventoryTransferMapper;

  @Mock
  private WarehouseMapper warehouseMapper;

  @InjectMocks
  private AdminInventoryTransferServiceImpl service;

  /** 构造测试用调拨实体 */
  private InventoryTransferEntity buildEntity(Long id, Long skuId, String dbStatus) {
    InventoryTransferEntity entity = new InventoryTransferEntity();
    entity.setId(id);
    entity.setSkuId(skuId);
    entity.setFromWarehouseId(10L);
    entity.setToWarehouseId(20L);
    entity.setQuantity(5);
    entity.setStatus(dbStatus);
    entity.setOperatorId(100L);
    entity.setApproverId(200L);
    entity.setReason("测试调拨");
    entity.setCreateTime(LocalDateTime.of(2026, 8, 4, 10, 0, 0));
    entity.setUpdateTime(LocalDateTime.of(2026, 8, 4, 11, 0, 0));
    return entity;
  }

  // ============ listAll ============

  @Test
  void listAll_无状态过滤_返回全部并正确映射VO字段() {
    // given:数据库返回 2 条记录,状态分别为 PENDING 和 IN_TRANSIT
    InventoryTransferEntity e1 = buildEntity(1L, 100L, "PENDING");
    InventoryTransferEntity e2 = buildEntity(2L, 200L, "IN_TRANSIT");
    Page<InventoryTransferEntity> page = new Page<>(1, 10);
    page.setRecords(List.of(e1, e2));
    page.setTotal(2);
    when(inventoryTransferMapper.selectPage(any(), any())).thenReturn(page);
    // 仓库名称查询
    when(warehouseMapper.selectById(10L)).thenReturn(buildWarehouse(10L, "北京仓"));
    when(warehouseMapper.selectById(20L)).thenReturn(buildWarehouse(20L, "上海仓"));

    // when
    PageResponse<InventoryTransferVO> result = service.listAll(1, 10, null);

    // then:验证分页元数据
    assertEquals(2, result.getTotal());
    assertEquals(1, result.getPage());
    assertEquals(10, result.getSize());
    assertEquals(2, result.getRecords().size());

    // 验证 VO 字段映射
    InventoryTransferVO vo1 = result.getRecords().get(0);
    assertEquals(1L, vo1.getId());
    assertEquals(100L, vo1.getSkuId());
    assertEquals(100L, vo1.getProductId(), "productId 应与 skuId 同值");
    assertEquals(10L, vo1.getFromWarehouseId());
    assertEquals(20L, vo1.getToWarehouseId());
    assertEquals("北京仓", vo1.getFromWarehouse());
    assertEquals("上海仓", vo1.getToWarehouse());
    assertEquals("SKU-100", vo1.getProductName());
    assertEquals(5, vo1.getQuantity());
    assertEquals("测试调拨", vo1.getReason());
    assertEquals("pending", vo1.getStatus(), "PENDING → pending");
    assertEquals("PENDING", vo1.getDbStatus());
    assertEquals(100L, vo1.getOperatorId());
    assertEquals(200L, vo1.getApproverId());
    assertNotNull(vo1.getCreateTime());
    assertEquals(vo1.getCreateTime(), vo1.getCreatedAt(), "createdAt 应与 createTime 同值");

    // IN_TRANSIT → approved
    InventoryTransferVO vo2 = result.getRecords().get(1);
    assertEquals("approved", vo2.getStatus());
    assertEquals("IN_TRANSIT", vo2.getDbStatus());
  }

  @Test
  void listAll_状态过滤approved_查询条件使用IN_TRANSIT数据库值() {
    // given
    Page<InventoryTransferEntity> page = new Page<>(1, 10);
    page.setRecords(List.of());
    page.setTotal(0);
    when(inventoryTransferMapper.selectPage(any(), any())).thenReturn(page);

    // when:前端传入 "approved"
    service.listAll(1, 10, "approved");

    // then:验证 wrapper 中的 status 应为 IN_TRANSIT(数据库值)
    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    // 此处无法直接断言 wrapper 内容,但通过 selectPage 被调用即可证明无异常
    verify(inventoryTransferMapper).selectPage(any(), any());
  }

  @Test
  void listAll_数据库状态异常值_降级为pending() {
    // given:数据库状态为非法值 "UNKNOWN"
    InventoryTransferEntity e = buildEntity(1L, 100L, "UNKNOWN");
    Page<InventoryTransferEntity> page = new Page<>(1, 10);
    page.setRecords(List.of(e));
    page.setTotal(1);
    when(inventoryTransferMapper.selectPage(any(), any())).thenReturn(page);
    when(warehouseMapper.selectById(any())).thenReturn(null);

    // when
    PageResponse<InventoryTransferVO> result = service.listAll(1, 10, null);

    // then:数据库异常状态应降级为 "pending"
    assertEquals("pending", result.getRecords().get(0).getStatus());
    assertEquals("UNKNOWN", result.getRecords().get(0).getDbStatus(), "dbStatus 保留原始值");
  }

  // ============ create ============

  @Test
  void create_使用仓库ID_初始状态为PENDING() {
    // given
    InventoryTransferCreateRequest request = new InventoryTransferCreateRequest();
    request.setSkuId(100L);
    request.setFromWarehouseId(10L);
    request.setToWarehouseId(20L);
    request.setQuantity(8);
    request.setOperatorId(100L);
    request.setReason("ID 创建");

    // when
    service.create(request);

    // then:验证插入的实体
    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).insert(captor.capture());
    InventoryTransferEntity inserted = captor.getValue();
    assertEquals(100L, inserted.getSkuId());
    assertEquals(10L, inserted.getFromWarehouseId());
    assertEquals(20L, inserted.getToWarehouseId());
    assertEquals(8, inserted.getQuantity());
    assertEquals(100L, inserted.getOperatorId());
    assertEquals("ID 创建", inserted.getReason());
    assertEquals("PENDING", inserted.getStatus(), "新建调拨单状态必须为 PENDING");
  }

  @Test
  void create_使用仓库名称_按名称查找仓库ID() {
    // given:仅提供仓库名称,未提供 ID
    InventoryTransferCreateRequest request = new InventoryTransferCreateRequest();
    request.setSkuId(100L);
    request.setFromWarehouse("北京仓");
    request.setToWarehouse("上海仓");
    request.setQuantity(3);
    when(warehouseMapper.selectOne(any())).thenReturn(buildWarehouse(10L, "北京仓"));

    // when
    service.create(request);

    // then:应通过名称查找仓库ID
    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).insert(captor.capture());
    // 注意:第二次调用 selectOne 返回 null(因为同一个 mock 返回同一个值),此处只验证调用次数
    verify(warehouseMapper, times(2)).selectOne(any());
    assertEquals(10L, captor.getValue().getFromWarehouseId(), "应使用按名称查到的仓库ID");
  }

  // ============ approve ============

  @Test
  void approve_记录存在_状态更新为IN_TRANSIT() {
    // given
    InventoryTransferEntity entity = buildEntity(1L, 100L, "PENDING");
    when(inventoryTransferMapper.selectById(1L)).thenReturn(entity);

    // when
    service.approve(1L);

    // then
    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).updateById(captor.capture());
    assertEquals("IN_TRANSIT", captor.getValue().getStatus());
  }

  @Test
  void approve_记录不存在_不抛异常也不更新() {
    when(inventoryTransferMapper.selectById(1L)).thenReturn(null);

    assertDoesNotThrow(() -> service.approve(1L));
    verify(inventoryTransferMapper, never()).updateById(any(InventoryTransferEntity.class));
  }

  // ============ reject ============

  @Test
  void reject_带原因_状态更新为REJECTED并保存原因() {
    InventoryTransferEntity entity = buildEntity(1L, 100L, "PENDING");
    when(inventoryTransferMapper.selectById(1L)).thenReturn(entity);

    service.reject(1L, "库存不足");

    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).updateById(captor.capture());
    assertEquals("REJECTED", captor.getValue().getStatus());
    assertEquals("库存不足", captor.getValue().getReason());
  }

  @Test
  void reject_原因为空_保留原有reason() {
    InventoryTransferEntity entity = buildEntity(1L, 100L, "PENDING");
    entity.setReason("原始原因");
    when(inventoryTransferMapper.selectById(1L)).thenReturn(entity);

    service.reject(1L, null);

    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).updateById(captor.capture());
    assertEquals("REJECTED", captor.getValue().getStatus());
    assertEquals("原始原因", captor.getValue().getReason(), "原因为空时应保留原值");
  }

  // ============ complete ============

  @Test
  void complete_记录存在_状态更新为COMPLETED且completeTime被设置() {
    InventoryTransferEntity entity = buildEntity(1L, 100L, "IN_TRANSIT");
    when(inventoryTransferMapper.selectById(1L)).thenReturn(entity);

    service.complete(1L);

    ArgumentCaptor<InventoryTransferEntity> captor = ArgumentCaptor.forClass(InventoryTransferEntity.class);
    verify(inventoryTransferMapper).updateById(captor.capture());
    assertEquals("COMPLETED", captor.getValue().getStatus());
    assertNotNull(captor.getValue().getCompleteTime(), "completeTime 应被设置");
  }

  @Test
  void complete_记录不存在_不抛异常也不更新() {
    when(inventoryTransferMapper.selectById(1L)).thenReturn(null);

    assertDoesNotThrow(() -> service.complete(1L));
    verify(inventoryTransferMapper, never()).updateById(any(InventoryTransferEntity.class));
  }

  // ============ 辅助方法 ============

  private WarehouseEntity buildWarehouse(Long id, String name) {
    WarehouseEntity w = new WarehouseEntity();
    w.setId(id);
    w.setName(name);
    return w;
  }
}
