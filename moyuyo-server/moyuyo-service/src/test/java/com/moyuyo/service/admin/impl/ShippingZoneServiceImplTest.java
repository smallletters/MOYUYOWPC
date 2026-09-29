package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShippingZoneServiceImpl 单元测试
 *
 * 重点覆盖:
 *  1. loadShippableCountries 只聚合 ACTIVE 区域的国家码
 *  2. listSupportedCountries 返回全量（含 INACTIVE），排序去重
 *  3. 输入脏数据（空格 / 大小写 / 重复 / null / 空串）容错
 *  4. 表为空时返回安全默认值（空集合），避免业务空指针
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class ShippingZoneServiceImplTest {

  @Mock
  private ShippingZoneMapper shippingZoneMapper;

  @InjectMocks
  private ShippingZoneServiceImpl service;

  // ==================== loadShippableCountries ====================

  @Test
  void loadShippableCountries_shouldOnlyIncludeActiveZones() {
    ShippingZoneEntity active = zone(1L, "北美", "US,CA", "ACTIVE");
    // loadShippableCountries 只查 ACTIVE，由 selectList 走 status=ACTIVE 条件
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(active));

    Set<String> result = service.loadShippableCountries();
    assertTrue(result.contains("US"));
    assertTrue(result.contains("CA"));
    assertEquals(2, result.size());
  }

  @Test
  void loadShippableCountries_shouldNormalizeCaseAndTrimAndDedupe() {
    ShippingZoneEntity z = zone(1L, "脏数据", " us , CA ,us , ca , DE ", "ACTIVE");
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(z));

    Set<String> result = service.loadShippableCountries();
    assertEquals(3, result.size(), "去重 + 转大写后应为 3 个国家码");
    assertTrue(result.contains("US"));
    assertTrue(result.contains("CA"));
    assertTrue(result.contains("DE"));
  }

  @Test
  void loadShippableCountries_shouldHandleEmptyTable() {
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(new ArrayList<>());
    Set<String> result = service.loadShippableCountries();
    assertNotNull(result);
    assertTrue(result.isEmpty(), "表为空时返回空集合，避免下游误判");
  }

  @Test
  void loadShippableCountries_shouldHandleNullAndBlankCountryCodes() {
    ShippingZoneEntity z1 = zone(1L, "空区域", null, "ACTIVE");
    ShippingZoneEntity z2 = zone(2L, "空字符串", "", "ACTIVE");
    ShippingZoneEntity z3 = zone(3L, "纯空白", "   ", "ACTIVE");
    ShippingZoneEntity z4 = zone(4L, "正常", "JP", "ACTIVE");
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(z1, z2, z3, z4));

    Set<String> result = service.loadShippableCountries();
    assertEquals(1, result.size());
    assertTrue(result.contains("JP"));
  }

  // ==================== listSupportedCountries ====================

  @Test
  void listSupportedCountries_shouldReturnAllIncludingInactiveSorted() {
    ShippingZoneEntity active = zone(1L, "北美", "US,CA", "ACTIVE");
    ShippingZoneEntity inactive = zone(2L, "东南亚（停用）", "SG,MY", "INACTIVE");
    // listSupportedCountries 走 selectList(null)，全量返回
    when(shippingZoneMapper.selectList(null)).thenReturn(List.of(active, inactive));

    List<String> result = service.listSupportedCountries();
    assertEquals(4, result.size());
    // 字典序：CA < MY < SG < US
    assertEquals(List.of("CA", "MY", "SG", "US"), result);
  }

  @Test
  void listSupportedCountries_shouldDedupeAndNormalizeCase() {
    ShippingZoneEntity z = zone(1L, "去重", "us, US, Us, ca", "ACTIVE");
    when(shippingZoneMapper.selectList(null)).thenReturn(List.of(z));

    List<String> result = service.listSupportedCountries();
    assertEquals(2, result.size());
    assertEquals(List.of("CA", "US"), result);
  }

  @Test
  void listSupportedCountries_shouldReturnEmptyWhenTableEmpty() {
    when(shippingZoneMapper.selectList(null)).thenReturn(new ArrayList<>());
    assertTrue(service.listSupportedCountries().isEmpty());
  }

  // ==================== 缓存行为（问题7：30s TTL） ====================

  @Test
  void loadShippableCountries_shouldHitCacheOnSecondCall() {
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(zone(1L, "北美", "US,CA", "ACTIVE")));

    Set<String> first = service.loadShippableCountries();
    Set<String> second = service.loadShippableCountries();

    // 第二次不应再打 DB
    verify(shippingZoneMapper, times(1))
        .selectList(any(LambdaQueryWrapper.class));
    assertEquals(first, second);
  }

  @Test
  void loadShippableCountries_cacheShouldBeImmutable() {
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(zone(1L, "北美", "US", "ACTIVE")));

    Set<String> cached = service.loadShippableCountries();
    // 调用方修改返回集合不应破坏缓存（防御：业务侧若对结果做 add/remove，不能污染下次读到集合）
    assertThrows(UnsupportedOperationException.class, () -> cached.add("CN"));
  }

  @Test
  void loadShippableCountries_multipleCallsHitCacheOnce() {
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(zone(1L, "北美", "US", "ACTIVE")));
    service.loadShippableCountries();
    service.loadShippableCountries();
    service.loadShippableCountries();
    verify(shippingZoneMapper, times(1))
        .selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  void evictCache_shouldForceReload() {
    // 第一次取到 US,第二次 evict 后应重新查 DB 拿到新的 CN
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(zone(1L, "北美", "US", "ACTIVE")),
                    List.of(zone(2L, "东南亚", "CN", "ACTIVE")));

    Set<String> first = service.loadShippableCountries();
    assertTrue(first.contains("US"));

    service.evictCache();

    Set<String> second = service.loadShippableCountries();
    assertTrue(second.contains("CN"));
    assertFalse(second.contains("US"));
    // 第二次调用查询次数应是 2（第一次 + evict 后）
    verify(shippingZoneMapper, times(2))
        .selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  void evictCache_adminEvictHit_doesNotPolluteMissRatio() {
    // 验证问题3修复：运营主动 evict 后第一次加载只计 adminEvict，不计常规 miss
    when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(zone(1L, "北美", "US", "ACTIVE")));

    service.loadShippableCountries();  // 第一次：DB miss → 写缓存
    service.evictCache();              // 运营主动失效（不计入 miss）
    service.loadShippableCountries();  // 第二次：DB miss（被 evict 强制），但走 adminEvict 路径

    // 命中率分母 = hit + miss + adminEvict = 0 + 1 + 1 = 2
    // 命中率分子（hit）= 0
    // 即常规 miss 数为 1（首次冷启动的 miss），adminEvict 数为 1（运营主动失效命中）
    assertEquals(0.0, service.cacheHitRatio(), 1e-9);
    // 再调一次后续常规调用：算 hit（缓存未过期）
    service.loadShippableCountries();
    // 命中率 = hit / total = 1 / 3 ≈ 0.333
    assertEquals(1.0 / 3.0, service.cacheHitRatio(), 1e-9);
  }

  // ==================== helper ====================

  private ShippingZoneEntity zone(Long id, String name, String codes, String status) {
    ShippingZoneEntity e = new ShippingZoneEntity();
    e.setId(id);
    e.setName(name);
    e.setCountryCodes(codes);
    e.setStatus(status);
    return e;
  }
}