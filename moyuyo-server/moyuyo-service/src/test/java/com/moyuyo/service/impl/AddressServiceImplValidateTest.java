package com.moyuyo.service.impl;

import com.moyuyo.common.dto.address.AddressValidateResponse;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.service.admin.ShippingZoneService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AddressServiceImpl.validateAddress / listSupportedCountries 单元测试
 *
 * 重点覆盖：
 *  1. 国家码在 ACTIVE 区域 → shippable=true
 *  2. 国家码不在 ACTIVE 区域 → shippable=false，文案带出国家码
 *  3. 国家码大小写 / 空白不影响命中
 *  4. 国家码为空 / null → 返回 shippable=false + 友好文案
 *  5. 地址不存在 → shippable=false + Address not found
 *  6. listSupportedCountries 直接透传 ShippingZoneService 返回
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceImplValidateTest {

  @Mock
  private AddressMapper addressMapper;

  @Mock
  private ShippingZoneService shippingZoneService;

  @InjectMocks
  private AddressServiceImpl service;

  // ==================== validateAddress ====================

  @Test
  void validateAddress_shippableCountry_returnsTrue() {
    AddressEntity addr = addr(100L, 1L, "US");
    when(addressMapper.selectById(100L)).thenReturn(addr);
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "CA", "GB"));

    AddressValidateResponse resp = service.validateAddress(100L, 1L);

    assertNotNull(resp);
    assertTrue(resp.isShippable());
    assertTrue(resp.getMessage().contains("US"), "成功提示应包含国家码，便于 APP 调试");
  }

  @Test
  void validateAddress_unshippableCountry_returnsFalse() {
    AddressEntity addr = addr(101L, 1L, "CN");
    when(addressMapper.selectById(101L)).thenReturn(addr);
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "CA"));

    AddressValidateResponse resp = service.validateAddress(101L, 1L);

    assertFalse(resp.isShippable());
    assertTrue(resp.getMessage().contains("CN"));
  }

  @Test
  void validateAddress_caseInsensitiveAndTrimmed() {
    // 存储是 " us "，应仍然命中 US
    AddressEntity addr = addr(102L, 1L, " us ");
    when(addressMapper.selectById(102L)).thenReturn(addr);
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US"));

    AddressValidateResponse resp = service.validateAddress(102L, 1L);
    assertTrue(resp.isShippable());
  }

  @Test
  void validateAddress_blankCountry_returnsFalse() {
    AddressEntity addr = addr(103L, 1L, "   ");
    when(addressMapper.selectById(103L)).thenReturn(addr);

    AddressValidateResponse resp = service.validateAddress(103L, 1L);
    assertFalse(resp.isShippable());
    assertEquals("Country is required", resp.getMessage());
  }

  @Test
  void validateAddress_nullCountry_returnsFalse() {
    AddressEntity addr = addr(104L, 1L, null);
    when(addressMapper.selectById(104L)).thenReturn(addr);

    AddressValidateResponse resp = service.validateAddress(104L, 1L);
    assertFalse(resp.isShippable());
    assertEquals("Country is required", resp.getMessage());
  }

  @Test
  void validateAddress_addressNotFound_returnsFalse() {
    when(addressMapper.selectById(999L)).thenReturn(null);

    AddressValidateResponse resp = service.validateAddress(999L, 1L);
    assertFalse(resp.isShippable());
    assertEquals("Address not found", resp.getMessage());
  }

  @Test
  void validateAddress_addressBelongsToOtherUser_throwsAccessDenied() {
    AddressEntity addr = addr(105L, 999L /* other user */, "US");
    when(addressMapper.selectById(105L)).thenReturn(addr);

    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> service.validateAddress(105L, 1L),
        "getById 会校验归属，validateAddress 透传此行为");
  }

  // ==================== listSupportedCountries ====================

  @Test
  void listSupportedCountries_delegatesToShippingZoneService() {
    List<String> expected = List.of("CA", "DE", "FR", "JP", "US");
    when(shippingZoneService.listSupportedCountries()).thenReturn(expected);

    List<String> actual = service.listSupportedCountries();
    assertSame(expected, actual, "应当直接转发，不做二次加工");
  }

  // ==================== batchValidate ====================

  @Test
  void batchValidate_mixedCountries_returnsCorrectShippable() {
    when(addressMapper.selectByIds(java.util.List.of(100L, 101L, 102L)))
        .thenReturn(java.util.List.of(addr(100L, 1L, "US"), addr(101L, 1L, "CN"), addr(102L, 1L, "GB")));
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "GB"));

    var items = service.batchValidate(java.util.List.of(100L, 101L, 102L), 1L);

    assertEquals(3, items.size());
    var byId = new java.util.HashMap<Long, Boolean>();
    for (var it : items) byId.put(it.getAddressId(), it.isShippable());
    assertTrue(byId.get(100L));   // US 可发
    assertFalse(byId.get(101L));  // CN 不可发
    assertTrue(byId.get(102L));   // GB 可发
  }

  @Test
  void batchValidate_emptyList_returnsEmpty() {
    assertTrue(service.batchValidate(java.util.Collections.emptyList(), 1L).isEmpty());
    verify(addressMapper, never()).selectByIds(any());
  }

  @Test
  void batchValidate_otherUsersAddress_filteredOut() {
    // 100L 是当前用户，200L 是另一个用户 → batch-validate 应只返回 100L
    when(addressMapper.selectByIds(java.util.List.of(100L, 200L)))
        .thenReturn(java.util.List.of(addr(100L, 1L, "US"), addr(200L, 999L, "US")));
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US"));

    var items = service.batchValidate(java.util.List.of(100L, 200L), 1L);
    assertEquals(1, items.size(), "其他用户的地址不应出现在结果里（防止越权）");
    assertEquals(100L, items.get(0).getAddressId());
  }

  @Test
  void batchValidate_missingIds_returnsEmpty() {
    when(addressMapper.selectByIds(java.util.List.of(999L))).thenReturn(java.util.Collections.emptyList());
    assertTrue(service.batchValidate(java.util.List.of(999L), 1L).isEmpty());
  }

  // ==================== helper ====================

  private AddressEntity addr(Long id, Long userId, String country) {
    AddressEntity a = new AddressEntity();
    a.setId(id);
    a.setUserId(userId);
    a.setCountry(country);
    return a;
  }
}