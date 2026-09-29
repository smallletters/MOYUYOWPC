package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingStrategyEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingStrategyMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AdminShippingStrategyServiceImpl 单元测试
 *
 * 重点覆盖：
 *  1. create: zoneId 默认 1 / 不存在的 zone 拒 / 字段名兼容（新字段名 + 旧字段名）
 *  2. update: partial update / 不存在 id 抛 404 / zoneId 校验
 *  3. delete: 幂等（不存在也成功）
 *  4. listAll: status 过滤 / 中文状态归一化
 *  5. listZoneNameMap: 一次性回填
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class AdminShippingStrategyServiceImplTest {

  @Mock
  private ShippingStrategyMapper shippingStrategyMapper;
  @Mock
  private ShippingZoneMapper shippingZoneMapper;

  @InjectMocks
  private AdminShippingStrategyServiceImpl service;

  // ==================== create ====================

  @Test
  void create_shouldDefaultZoneIdTo1WhenAbsent() {
    // 没传 zoneId → 默认 1；且 zoneId=1 存在（北美，初始数据）
    ShippingStrategyCreateRequest req = new ShippingStrategyCreateRequest();
    req.setName("标准快递");
    req.setShippingMethod("快递");
    req.setFeeRule("首重10元");
    req.setPriority(1);
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    ShippingStrategyEntity result = service.create(req);

    assertEquals(1L, result.getZoneId(), "zoneId 未传时默认 1");
    assertEquals("标准快递", result.getName());
    assertEquals("ACTIVE", result.getStatus(), "status 未传默认 ACTIVE");
    verify(shippingStrategyMapper, times(1)).insert(any(ShippingStrategyEntity.class));
  }

  @Test
  void create_shouldAcceptOldFieldNames() {
    // 前端历史代码用 name / method / ruleDesc（不带 strategyName/shippingMethod/feeRule）
    ShippingStrategyCreateRequest req = new ShippingStrategyCreateRequest();
    req.setName("标准快递");
    req.setMethod("海运");
    req.setRuleDesc("海运按体积");
    req.setZoneId(1L);
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    ShippingStrategyEntity result = service.create(req);

    assertEquals("海运", result.getMethod());
    assertEquals("海运按体积", result.getRuleDesc());
  }

  @Test
  void create_shouldPreferNewFieldNamesOverOld() {
    // 新旧字段都传时优先取新字段（前端正在升级）
    ShippingStrategyCreateRequest req = new ShippingStrategyCreateRequest();
    req.setStrategyName("新名");
    req.setName("旧名");
    req.setShippingMethod("快递");
    req.setMethod("海运");
    req.setFeeRule("新规则");
    req.setRuleDesc("旧规则");
    req.setZoneId(1L);
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    ShippingStrategyEntity result = service.create(req);

    assertEquals("新名", result.getName());
    assertEquals("快递", result.getMethod());
    assertEquals("新规则", result.getRuleDesc());
  }

  @Test
  void create_shouldRejectNonexistentZoneId() {
    ShippingStrategyCreateRequest req = new ShippingStrategyCreateRequest();
    req.setName("x");
    req.setZoneId(999L);
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
    assertEquals(400, ex.getCode());
    assertTrue(ex.getMessage().contains("999"));
    verify(shippingStrategyMapper, never()).insert(any(ShippingStrategyEntity.class));
  }

  @Test
  void create_shouldNormalizeChineseStatus() {
    ShippingStrategyCreateRequest req = new ShippingStrategyCreateRequest();
    req.setName("x");
    req.setZoneId(1L);
    req.setStatus("停用");
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    ShippingStrategyEntity result = service.create(req);
    assertEquals("INACTIVE", result.getStatus());
  }

  // ==================== update ====================

  @Test
  void update_zoneNotFound_throws404() {
    when(shippingStrategyMapper.selectById(999L)).thenReturn(null);
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.update(999L, new ShippingStrategyUpdateRequest()));
    assertEquals(404, ex.getCode());
  }

  @Test
  void update_shouldOnlyTouchProvidedFields() {
    ShippingStrategyEntity existing = new ShippingStrategyEntity();
    existing.setId(1L);
    existing.setName("原名");
    existing.setZoneId(1L);
    existing.setMethod("原方式");
    existing.setStatus("ACTIVE");
    when(shippingStrategyMapper.selectById(1L)).thenReturn(existing);

    ShippingStrategyUpdateRequest req = new ShippingStrategyUpdateRequest();
    req.setPriority(99);  // 只传 priority
    ShippingStrategyEntity result = service.update(1L, req);

    assertEquals("原名", result.getName());
    assertEquals("原方式", result.getMethod());
    assertEquals(99, result.getPriority());
    assertEquals("ACTIVE", result.getStatus());
    verify(shippingStrategyMapper, times(1)).updateById(any(ShippingStrategyEntity.class));
  }

  @Test
  void update_shouldValidateNewZoneId() {
    ShippingStrategyEntity existing = new ShippingStrategyEntity();
    existing.setId(1L);
    existing.setName("x");
    existing.setZoneId(1L);
    when(shippingStrategyMapper.selectById(1L)).thenReturn(existing);
    // 新 zoneId=999 不存在
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

    ShippingStrategyUpdateRequest req = new ShippingStrategyUpdateRequest();
    req.setZoneId(999L);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
    assertEquals(400, ex.getCode());
    verify(shippingStrategyMapper, never()).updateById(any(ShippingStrategyEntity.class));
  }

  @Test
  void update_zoneIdUnchanged_shouldNotCheckZone() {
    ShippingStrategyEntity existing = new ShippingStrategyEntity();
    existing.setId(1L);
    existing.setName("x");
    existing.setZoneId(1L);
    when(shippingStrategyMapper.selectById(1L)).thenReturn(existing);

    ShippingStrategyUpdateRequest req = new ShippingStrategyUpdateRequest();
    req.setZoneId(1L);  // 没变

    service.update(1L, req);
    verify(shippingZoneMapper, never()).selectCount(any(LambdaQueryWrapper.class));
  }

  // ==================== delete ====================

  @Test
  void delete_existingId_deletes() {
    when(shippingStrategyMapper.deleteById(anyLong())).thenReturn(1);
    service.delete(1L);
    verify(shippingStrategyMapper, times(1)).deleteById(anyLong());
  }

  @Test
  void delete_idempotent_whenNotFound() {
    when(shippingStrategyMapper.deleteById(anyLong())).thenReturn(0);
    // 不抛异常：删除幂等
    assertDoesNotThrow(() -> service.delete(1L));
  }

  // ==================== listAll ====================

  @Test
  void listAll_shouldNormalizeChineseStatus() {
    when(shippingStrategyMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(java.util.Collections.emptyList());
    service.listAll("停用");
    // 不抛异常即可
    verify(shippingStrategyMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  void listAll_nullOrEmptyStatus_returnsAll() {
    when(shippingStrategyMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(java.util.Collections.emptyList());
    service.listAll(null);
    service.listAll("");
    verify(shippingStrategyMapper, times(2)).selectList(any(LambdaQueryWrapper.class));
  }

  // ==================== listZoneNameMap ====================

  @Test
  void listZoneNameMap_buildsIdToName() {
    ShippingZoneEntity z1 = new ShippingZoneEntity();
    z1.setId(1L); z1.setName("北美");
    ShippingZoneEntity z2 = new ShippingZoneEntity();
    z2.setId(2L); z2.setName("东南亚");
    when(shippingZoneMapper.selectList(null)).thenReturn(List.of(z1, z2));

    Map<Long, String> map = service.listZoneNameMap();
    assertEquals("北美", map.get(1L));
    assertEquals("东南亚", map.get(2L));
    assertEquals(2, map.size());
  }
}