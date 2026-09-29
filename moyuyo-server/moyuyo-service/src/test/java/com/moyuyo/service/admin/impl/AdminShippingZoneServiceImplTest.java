package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingStrategyMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.admin.ShippingZoneService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AdminShippingZoneServiceImpl 单元测试
 *
 * 重点覆盖：
 *  1. create: countryCodes 归一化 / 空拒绝 / name 业务查重 / DB UNIQUE 兜底
 *  2. update: 部分字段更新 / 仅显式传 countryCodes 才校验非空 / 改名查重
 *  3. delete: 引用计数 > 0 抛 BusinessException(409) / 引用 0 时正常删并 evictCache
 *  4. CRUD 后必须调 evictCache（强一致性）
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class AdminShippingZoneServiceImplTest {

  @Mock
  private ShippingZoneMapper shippingZoneMapper;
  @Mock
  private ShippingStrategyMapper shippingStrategyMapper;
  @Mock
  private ShippingZoneService shippingZoneService;

  @InjectMocks
  private AdminShippingZoneServiceImpl service;

  // ==================== create ====================

  @Test
  void create_shouldNormalizeCountryCodesAndEvictCache() {
    ShippingZoneCreateRequest req = new ShippingZoneCreateRequest();
    req.setName("北美");
    req.setCountryCodes(" us , CA ,us ");
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

    ShippingZoneEntity entity = service.create(req);

    assertEquals("北美", entity.getName());
    assertEquals("US,CA", entity.getCountryCodes());
    verify(shippingZoneService, times(1)).evictCache();
  }

  @Test
  void create_shouldRejectBlankCountryCodes() {
    ShippingZoneCreateRequest req = new ShippingZoneCreateRequest();
    req.setName("x");
    req.setCountryCodes("   ");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
    assertEquals(400, ex.getCode());
    verify(shippingZoneMapper, never()).insert(any(ShippingZoneEntity.class));
  }

  @Test
  void create_shouldRejectDuplicateNameAtAppLayer() {
    ShippingZoneCreateRequest req = new ShippingZoneCreateRequest();
    req.setName("北美");
    req.setCountryCodes("US");
    // 应用层查重：已存在同名
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
    assertEquals(409, ex.getCode());
    assertTrue(ex.getMessage().contains("北美"));
    verify(shippingZoneMapper, never()).insert(any(ShippingZoneEntity.class));
    verify(shippingZoneService, never()).evictCache();
  }

  @Test
  void create_shouldCatchDuplicateKeyFromDbUniqueIndex() {
    ShippingZoneCreateRequest req = new ShippingZoneCreateRequest();
    req.setName("北美");
    req.setCountryCodes("US");
    // 应用层查重通过，但 DB UNIQUE 兜底（两个并发 create 都过了应用层查重）
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    doThrow(new DuplicateKeyException("Duplicate entry")).when(shippingZoneMapper).insert(any(ShippingZoneEntity.class));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
    assertEquals(409, ex.getCode());
    verify(shippingZoneService, never()).evictCache();
  }

  // ==================== update ====================

  @Test
  void update_shouldOnlyUpdateProvidedFields() {
    ShippingZoneEntity existing = zone(1L, "原名", "US");
    existing.setSortOrder(10);
    when(shippingZoneMapper.selectById(1L)).thenReturn(existing);

    ShippingZoneUpdateRequest req = new ShippingZoneUpdateRequest();
    req.setSortOrder(99);  // 只传了 sortOrder
    ShippingZoneEntity result = service.update(1L, req);

    // 不应该改其他字段
    assertEquals("原名", result.getName());
    assertEquals("US", result.getCountryCodes());
    assertEquals(99, result.getSortOrder());
    verify(shippingZoneMapper, times(1)).updateById(any(ShippingZoneEntity.class));
    verify(shippingZoneService, times(1)).evictCache();
  }

  @Test
  void update_shouldRejectEmptyCountryCodesWhenProvided() {
    ShippingZoneEntity existing = zone(1L, "x", "US");
    when(shippingZoneMapper.selectById(1L)).thenReturn(existing);

    ShippingZoneUpdateRequest req = new ShippingZoneUpdateRequest();
    req.setCountryCodes(" , , ");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
    assertEquals(400, ex.getCode());
    verify(shippingZoneMapper, never()).updateById(any(ShippingZoneEntity.class));
  }

  @Test
  void update_renameToExistingName_shouldReject() {
    ShippingZoneEntity existing = zone(1L, "原名", "US");
    when(shippingZoneMapper.selectById(1L)).thenReturn(existing);
    // 查重：另一条 id=2 已用"新名"
    when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

    ShippingZoneUpdateRequest req = new ShippingZoneUpdateRequest();
    req.setName("新名");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
    assertEquals(409, ex.getCode());
    verify(shippingZoneMapper, never()).updateById(any(ShippingZoneEntity.class));
  }

  @Test
  void update_renameToSameName_shouldNotCheckDup() {
    ShippingZoneEntity existing = zone(1L, "原名", "US");
    when(shippingZoneMapper.selectById(1L)).thenReturn(existing);

    ShippingZoneUpdateRequest req = new ShippingZoneUpdateRequest();
    req.setName("原名");  // 没改名

    service.update(1L, req);
    verify(shippingZoneMapper, never()).selectCount(any(LambdaQueryWrapper.class));
  }

  @Test
  void update_zoneNotFound_throws404() {
    when(shippingZoneMapper.selectById(999L)).thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.update(999L, new ShippingZoneUpdateRequest()));
    assertEquals(404, ex.getCode());
  }

  // ==================== delete ====================

  @Test
  void delete_inUse_shouldThrow409() {
    when(shippingStrategyMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
    assertEquals(409, ex.getCode());
    assertTrue(ex.getMessage().contains("5"));
    verify(shippingZoneMapper, never()).deleteById(anyLong());
    verify(shippingZoneService, never()).evictCache();
  }

  @Test
  void delete_unused_shouldDeleteAndEvictCache() {
    when(shippingStrategyMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    when(shippingZoneMapper.deleteById(anyLong())).thenReturn(1);

    service.delete(1L);

    verify(shippingZoneMapper, times(1)).deleteById(anyLong());
    verify(shippingZoneService, times(1)).evictCache();
  }

  @Test
  void delete_unused_zeroRows_noEvict() {
    when(shippingStrategyMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    when(shippingZoneMapper.deleteById(anyLong())).thenReturn(0);

    service.delete(1L);

    // 0 行删除（id 已被并发删掉）不应触发 evictCache
    verify(shippingZoneService, never()).evictCache();
  }

  // ==================== listAll ====================

  @Test
  void listAll_shouldReturnOrdered() {
    List<ShippingZoneEntity> expected = new ArrayList<>();
    expected.add(zone(1L, "a", "US"));
    expected.add(zone(2L, "b", "GB"));
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(expected);

    assertSame(expected, service.listAll());
  }

  // ==================== normalizeCountryCodes 静态方法 ====================

  @Test
  void normalizeCountryCodes_blankReturnsEmpty() {
    assertEquals("", AdminShippingZoneServiceImpl.normalizeCountryCodes(null));
    assertEquals("", AdminShippingZoneServiceImpl.normalizeCountryCodes(""));
    assertEquals("", AdminShippingZoneServiceImpl.normalizeCountryCodes(" , , "));
  }

  @Test
  void normalizeCountryCodes_dedupCaseAndTrim() {
    assertEquals("US,CA,GB", AdminShippingZoneServiceImpl.normalizeCountryCodes("us, CA ,us , ca , GB"));
  }

  // ==================== helper ====================

  private ShippingZoneEntity zone(Long id, String name, String codes) {
    ShippingZoneEntity e = new ShippingZoneEntity();
    e.setId(id);
    e.setName(name);
    e.setCountryCodes(codes);
    return e;
  }
}