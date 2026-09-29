package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.dao.mapper.CartMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.admin.ShippingZoneService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CartServiceImpl.checkout 关于"发货区域"拦截的单元测试。
 *
 * 重点覆盖：
 *  1. 地址 country 在 ACTIVE 区域 → 进入下单流程
 *  2. 地址 country 不在 ACTIVE 区域 → 抛 BusinessException 400，下游 orderService 不被调用
 *  3. 地址 country 空白 / null → 抛 IllegalArgumentException
 *  4. 地址不存在 → 抛 IllegalArgumentException
 *  5. 购物车为空 → 原有"请选择要结算的商品"提示，拦截优先于地址校验
 *  6. 运营可发国家动态变化：删/加 ACTIVE 区域后下一次 checkout 立即生效
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class CartServiceImplCheckoutZoneTest {

  @Mock
  private CartMapper cartMapper;
  @Mock
  private ProductSkuMapper productSkuMapper;
  @Mock
  private ProductMapper productMapper;
  @Mock
  private AddressMapper addressMapper;
  @Mock
  private ShippingZoneService shippingZoneService;
  @Mock
  private OrderService orderService;

  @InjectMocks
  private CartServiceImpl service;

  private static final Long USER_ID = 100L;
  private static final Long ADDRESS_ID = 200L;

  // ==================== 1. 可发货：进入下单流程 ====================

  @Test
  void checkout_shippableCountry_callsOrderService() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(addr(ADDRESS_ID, USER_ID, "US"));
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "CA", "GB"));
    when(productSkuMapper.selectById(anyLong())).thenReturn(sku(1L, 100L, new BigDecimal("9.99")));
    when(productMapper.selectById(100L)).thenReturn(product(100L, "测试商品"));
    OrderEntity stubOrder = new OrderEntity();
    stubOrder.setId(999L);
    stubOrder.setOrderNo("TEST-001");
    // 用 doReturn().when() 而非 when().thenReturn()：避免 Mockito 严格模式把
    // 多参数匹配 (any, any, any, any, any) 误报为 stubbing 不匹配
    doReturn(stubOrder).when(orderService)
        .createOrder(anyLong(), any(), anyLong(), any(), any());

    OrderEntity result = service.checkout(USER_ID, ADDRESS_ID, "remark", null);

    assertNotNull(result);
    assertEquals("TEST-001", result.getOrderNo());
    verify(orderService, times(1))
        .createOrder(eq(USER_ID), any(), eq(ADDRESS_ID), eq("remark"), eq(null));
    verify(shippingZoneService, times(1)).loadShippableCountries();
  }

  // ==================== 2. 不可发货：抛 BusinessException，不下单 ====================

  @Test
  void checkout_unshippableCountry_throwsBusinessExceptionAndSkipsOrder() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(addr(ADDRESS_ID, USER_ID, "CN"));
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "CA", "GB"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.checkout(USER_ID, ADDRESS_ID, null, null));
    assertEquals(400, ex.getCode());
    assertTrue(ex.getMessage().contains("CN"), "错误信息应透出不可发国家码，便于 APP 排查");
    // 关键断言：不可发时下游 orderService 不应被调用
    verify(orderService, never()).createOrder(anyLong(), any(), anyLong(), anyString(), anyString());
    verify(productSkuMapper, never()).selectById(anyLong());
  }

  // ==================== 3. country 空白 / null ====================

  @Test
  void checkout_blankCountry_throwsIllegalArgument() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(addr(ADDRESS_ID, USER_ID, "   "));

    assertThrows(IllegalArgumentException.class,
        () -> service.checkout(USER_ID, ADDRESS_ID, null, null));
    verify(orderService, never()).createOrder(anyLong(), any(), anyLong(), anyString(), anyString());
  }

  @Test
  void checkout_nullCountry_throwsIllegalArgument() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(addr(ADDRESS_ID, USER_ID, null));

    assertThrows(IllegalArgumentException.class,
        () -> service.checkout(USER_ID, ADDRESS_ID, null, null));
    verify(orderService, never()).createOrder(anyLong(), any(), anyLong(), anyString(), anyString());
  }

  // ==================== 4. 地址不存在 ====================

  @Test
  void checkout_addressNotFound_throwsIllegalArgument() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(null);

    assertThrows(IllegalArgumentException.class,
        () -> service.checkout(USER_ID, ADDRESS_ID, null, null));
    verify(orderService, never()).createOrder(anyLong(), any(), anyLong(), anyString(), anyString());
  }

  // ==================== 5. 购物车为空 ====================

  @Test
  void checkout_emptyCart_throwsIllegalArgumentWithoutAddressLookup() {
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

    assertThrows(IllegalArgumentException.class,
        () -> service.checkout(USER_ID, ADDRESS_ID, null, null));
    // 短路：cart 为空时根本不应去查地址，避免无谓 IO
    verify(addressMapper, never()).selectById(anyLong());
    verify(orderService, never()).createOrder(anyLong(), any(), anyLong(), anyString(), anyString());
  }

  // ==================== 6. 运营动态调整 ACTIVE 区域生效 ====================

  @Test
  void checkout_zoneChangeEffectiveImmediately() {
    // 模拟运营刚把 SG 加入 ACTIVE 区域：上一次失败地址这次应该通过
    when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(selectedCart());
    when(addressMapper.selectById(ADDRESS_ID)).thenReturn(addr(ADDRESS_ID, USER_ID, "SG"));
    when(shippingZoneService.loadShippableCountries()).thenReturn(Set.of("US", "SG"));
    when(productSkuMapper.selectById(anyLong())).thenReturn(sku(1L, 100L, new BigDecimal("9.99")));
    when(productMapper.selectById(100L)).thenReturn(product(100L, "测试商品"));
    OrderEntity stubOrder = new OrderEntity();
    stubOrder.setId(1234L);
    stubOrder.setOrderNo("TEST-SG");
    doReturn(stubOrder).when(orderService)
        .createOrder(anyLong(), any(), anyLong(), any(), any());

    OrderEntity result = service.checkout(USER_ID, ADDRESS_ID, null, null);

    assertEquals("TEST-SG", result.getOrderNo(), "运营把 SG 加入 ACTIVE 后应能正常下单");
  }

  // ==================== helper ====================

  private List<CartEntity> selectedCart() {
    CartEntity c = new CartEntity();
    c.setId(1L);
    c.setUserId(USER_ID);
    c.setSkuId(1L);
    c.setProductId(100L);
    c.setSelected(true);
    c.setQuantity(1);
    return List.of(c);
  }

  private AddressEntity addr(Long id, Long userId, String country) {
    AddressEntity a = new AddressEntity();
    a.setId(id);
    a.setUserId(userId);
    a.setCountry(country);
    return a;
  }

  private ProductSkuEntity sku(Long id, Long productId, BigDecimal price) {
    ProductSkuEntity s = new ProductSkuEntity();
    s.setId(id);
    s.setProductId(productId);
    s.setPrice(price);
    return s;
  }

  private ProductEntity product(Long id, String name) {
    ProductEntity p = new ProductEntity();
    p.setId(id);
    p.setName(name);
    p.setMainImage("https://example.com/x.png");
    return p;
  }
}