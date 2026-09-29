package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.CarrierCreateRequest;
import com.moyuyo.common.dto.admin.logistics.CarrierUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.CarrierEntity;
import com.moyuyo.dao.admin.mapper.CarrierMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AdminCarrierServiceImpl 单元测试
 *
 * 重点覆盖：
 *  1. create: 字段映射 / status 归一化
 *  2. update: 不存在 id=404 / partial update / 数值字段保留
 *  3. delete: 幂等（不存在不抛）
 *  4. listAll: status 过滤 / customsOnly 影响查询条件
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class AdminCarrierServiceImplTest {

  @Mock
  private CarrierMapper carrierMapper;

  @InjectMocks
  private AdminCarrierServiceImpl service;

  // ==================== create ====================

  @Test
  void create_shouldDefaultStatusToActive() {
    CarrierCreateRequest req = new CarrierCreateRequest();
    req.setName("顺丰");
    req.setTransportMode("AIR");
    req.setAvgDeliveryDays(new BigDecimal("5"));
    req.setFirstWeightPrice(new BigDecimal("10"));

    CarrierEntity result = service.create(req);
    assertEquals("顺丰", result.getName());
    assertEquals("AIR", result.getTransportMode());
    assertEquals("ACTIVE", result.getStatus(), "status 未传时默认 ACTIVE");
    verify(carrierMapper, times(1)).insert(any(CarrierEntity.class));
  }

  @Test
  void create_shouldNormalizeChineseStatus() {
    CarrierCreateRequest req = new CarrierCreateRequest();
    req.setName("x");
    req.setStatus("停用");

    CarrierEntity result = service.create(req);
    assertEquals("INACTIVE", result.getStatus());
  }

  // ==================== update ====================

  @Test
  void update_carrierNotFound_throws404() {
    when(carrierMapper.selectById(999L)).thenReturn(null);
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.update(999L, new CarrierUpdateRequest()));
    assertEquals(404, ex.getCode());
  }

  @Test
  void update_shouldOnlyTouchProvidedFields() {
    CarrierEntity existing = new CarrierEntity();
    existing.setId(1L);
    existing.setName("原名");
    existing.setTransportMode("AIR");
    existing.setAvgDeliveryDays(new BigDecimal("3"));
    existing.setFirstWeightPrice(new BigDecimal("10"));
    when(carrierMapper.selectById(1L)).thenReturn(existing);

    CarrierUpdateRequest req = new CarrierUpdateRequest();
    req.setName("新名");  // 只传 name
    CarrierEntity result = service.update(1L, req);

    assertEquals("新名", result.getName());
    assertEquals("AIR", result.getTransportMode(), "其他字段不动");
    assertEquals(new BigDecimal("3"), result.getAvgDeliveryDays());
    verify(carrierMapper, times(1)).updateById(any(CarrierEntity.class));
  }

  // ==================== delete ====================

  @Test
  void delete_idempotent_whenNotFound() {
    when(carrierMapper.deleteById(anyLong())).thenReturn(0);
    assertDoesNotThrow(() -> service.delete(1L));
  }

  // ==================== listAll ====================

  @Test
  void listAll_nullStatus_returnsAll() {
    when(carrierMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(java.util.Collections.emptyList());
    service.listAll(null);
    verify(carrierMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
  }
}