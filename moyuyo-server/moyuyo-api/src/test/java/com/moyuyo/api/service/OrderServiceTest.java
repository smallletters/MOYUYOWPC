package com.moyuyo.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.OrderItemRequest;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private OrderItemMapper orderItemMapper;

  @Mock
  private PaymentMapper paymentMapper;

  @Mock
  private ProductMapper productMapper;

  @Mock
  private ProductSkuMapper productSkuMapper;

  @InjectMocks
  private OrderServiceImpl orderService;

  @Captor
  private ArgumentCaptor<OrderEntity> orderCaptor;

  @Captor
  private ArgumentCaptor<OrderItemEntity> orderItemCaptor;

  // ==================== createOrder ====================

  @Test
  void createOrder_validRequest_shouldReturnOrder() {
    // 准备：订单项数据
    Long userId = 1L;
    Long addressId = 10L;
    BigDecimal price = new BigDecimal("99.99");
    int quantity = 2;

    OrderItemEntity item = new OrderItemEntity();
    item.setPrice(price);
    item.setQuantity(quantity);

    List<OrderItemEntity> items = List.of(item);

    // 模拟 orderMapper.insert 设置 ID
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(100L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    // 执行
    OrderEntity result = orderService.createOrder(userId, items, addressId, "测试备注", "coupon-001");

    // 验证订单基本信息
    assertNotNull(result);
    assertNotNull(result.getOrderNo());
    assertTrue(result.getOrderNo().startsWith("ORD")); // 订单号以 ORD 开头
    assertEquals(userId, result.getUserId());
    assertEquals("PENDING_PAY", result.getStatus());
    assertEquals(addressId, result.getAddressId());
    assertEquals("测试备注", result.getRemark());
    assertEquals("coupon-001", result.getCouponId());
    assertEquals(Integer.valueOf(0), result.getDeleteStatus());

    // 验证金额计算正确：99.99 * 2 = 199.98
    assertEquals(new BigDecimal("199.98"), result.getGoodsAmount());
    assertEquals(BigDecimal.ZERO, result.getFreight());
    assertEquals(new BigDecimal("199.98"), result.getPayAmount());

    // 验证 orderMapper.insert 被调用
    verify(orderMapper).insert(any(OrderEntity.class));

    // 验证订单项保存
    verify(orderItemMapper).insert(orderItemCaptor.capture());
    OrderItemEntity savedItem = orderItemCaptor.getValue();
    assertEquals(100L, savedItem.getOrderId()); // 关联到订单 ID
    assertEquals(price, savedItem.getPrice());
    assertEquals(quantity, savedItem.getQuantity());
    assertEquals(new BigDecimal("199.98"), savedItem.getSubtotal()); // 99.99 * 2
  }

  @Test
  void createOrder_emptyItems_shouldCreateOrderWithZeroAmount() {
    // 准备：空订单项列表
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(200L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    // 执行
    OrderEntity result = orderService.createOrder(1L, Collections.emptyList(), null, null, null);

    // 验证：空订单项不会抛异常，金额为 0
    assertNotNull(result);
    assertEquals(BigDecimal.ZERO, result.getGoodsAmount());
    assertEquals(BigDecimal.ZERO, result.getPayAmount());

    // 验证 orderItemMapper.insert 没有被调用（无订单项）
    verify(orderItemMapper, never()).insert(any(OrderItemEntity.class));
  }

  // ==================== createOrderFromRequest ====================

  @Test
  void createOrderFromRequest_validRequest_shouldReturnOrder() {
    // 准备：请求对象
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(10L);
    itemReq.setProductId(20L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));
    request.setAddressId(100L);
    request.setRemark("备注");
    request.setCouponId("coupon-002");

    // 模拟 SKU 存在
    ProductSkuEntity sku = new ProductSkuEntity();
    sku.setId(10L);
    sku.setProductId(20L);
    sku.setPrice(new BigDecimal("199.00"));
    when(productSkuMapper.selectById(10L)).thenReturn(sku);

    // 模拟商品存在
    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setMainImage("http://example.com/img.jpg");
    when(productMapper.selectById(20L)).thenReturn(product);

    // 模拟 orderMapper.insert
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(300L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    // 执行
    OrderEntity result = orderService.createOrderFromRequest(1L, request);

    // 验证
    assertNotNull(result);
    assertEquals("PENDING_PAY", result.getStatus());
    assertEquals(new BigDecimal("199.00"), result.getGoodsAmount());

    // 验证 SKU 和商品查询
    verify(productSkuMapper).selectById(10L);
    verify(productMapper).selectById(20L);

    // 验证订单项中的商品信息
    verify(orderItemMapper).insert(orderItemCaptor.capture());
    OrderItemEntity savedItem = orderItemCaptor.getValue();
    assertEquals(10L, savedItem.getSkuId());
    assertEquals(20L, savedItem.getProductId());
    assertEquals("测试商品", savedItem.getProductName());
    assertEquals("http://example.com/img.jpg", savedItem.getMainImage());
    assertEquals(new BigDecimal("199.00"), savedItem.getPrice());
    assertEquals(Integer.valueOf(1), savedItem.getQuantity());
  }

  @Test
  void createOrderFromRequest_skuNotExist_shouldThrowIllegalArgumentException() {
    // 准备：SKU 不存在
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(999L);
    itemReq.setProductId(20L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));

    when(productSkuMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.createOrderFromRequest(1L, request));
    assertTrue(ex.getMessage().contains("SKU不存在"));

    // 验证商品查询没有被调用
    verify(productMapper, never()).selectById(any());
  }

  @Test
  void createOrderFromRequest_productNotExist_shouldThrowIllegalArgumentException() {
    // 准备：商品不存在
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(10L);
    itemReq.setProductId(999L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));

    // SKU 存在
    ProductSkuEntity sku = new ProductSkuEntity();
    sku.setId(10L);
    sku.setProductId(999L);
    sku.setPrice(new BigDecimal("100.00"));
    when(productSkuMapper.selectById(10L)).thenReturn(sku);

    // 商品不存在
    when(productMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.createOrderFromRequest(1L, request));
    assertTrue(ex.getMessage().contains("商品不存在"));
  }

  // ==================== listOrders ====================

  @SuppressWarnings("unchecked")
  @Test
  void listOrders_withStatusFilter_shouldReturnPagedOrders() {
    // 准备：分页数据
    Page<OrderEntity> mockPage = new Page<>(1, 10);
    mockPage.setRecords(List.of(new OrderEntity(), new OrderEntity()));

    when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行
    IPage<OrderEntity> result = orderService.listOrders(1L, 1, 10, "PENDING_PAY");

    // 验证
    assertNotNull(result);
    assertEquals(2, result.getRecords().size());

    // 验证参数传递
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(orderMapper).selectPage(any(Page.class), wrapperCaptor.capture());

    // 验证查询条件
    LambdaQueryWrapper<OrderEntity> wrapper = wrapperCaptor.getValue();
    assertNotNull(wrapper);
  }

  @SuppressWarnings("unchecked")
  @Test
  void listOrders_withNullUserId_shouldNotFilterByUserId() {
    // 准备：管理员查询
    Page<OrderEntity> mockPage = new Page<>(1, 20);
    when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行：userId = null 表示管理员
    IPage<OrderEntity> result = orderService.listOrders(null, 1, 20, null);

    // 验证
    assertNotNull(result);
    verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
  }

  // ==================== getOrderDetail ====================

  @Test
  void getOrderDetail_orderExists_shouldReturnOrder() {
    // 准备：订单存在
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setOrderNo("ORD20260721000001");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    OrderEntity result = orderService.getOrderDetail(1L, 1L);

    // 验证
    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals(1L, result.getUserId());
  }

  @Test
  void getOrderDetail_orderNotExist_shouldThrowIllegalArgumentException() {
    // 准备：订单不存在
    when(orderMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.getOrderDetail(999L, 1L));
    assertEquals("订单不存在", ex.getMessage());
  }

  @Test
  void getOrderDetail_nullUserId_shouldSkipPermissionCheck() {
    // 准备：userId 为 null（管理员查询）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(2L); // 与传入的 userId 不同
    order.setOrderNo("ORD20260721000002");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行：userId = null 表示管理员，跳过权限校验
    OrderEntity result = orderService.getOrderDetail(1L, null);

    // 验证：不抛异常，正常返回
    assertNotNull(result);
    assertEquals(2L, result.getUserId());
  }

  // ==================== cancelOrder ====================

  @Test
  void cancelOrder_pendingPayStatus_shouldCancelSuccessfully() {
    // 准备：PENDING_PAY 状态的订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    orderService.cancelOrder(1L, 1L, "不想要了");

    // 验证状态更新
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals("CANCELLED", updatedOrder.getStatus());
    assertEquals("不想要了", updatedOrder.getCancelReason());
    assertNotNull(updatedOrder.getCancelTime());
  }

  @Test
  void cancelOrder_paidStatus_shouldThrowIllegalStateException() {
    // 准备：PAID 状态的订单（不允许取消）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PAID");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行 & 验证
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> orderService.cancelOrder(1L, 1L, "想取消"));
    assertEquals("当前订单状态不允许取消", ex.getMessage());

    // 验证没有执行更新
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  // ==================== payCallback ====================

  @Test
  void payCallback_validRequest_shouldUpdateOrderAndCreatePayment() {
    // 准备：PENDING_PAY 状态的订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");
    order.setPayAmount(new BigDecimal("199.98"));

    when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

    // 执行
    orderService.payCallback("ORD20260721000001", "STRIPE", "pi_test_123");

    // 验证订单状态更新
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals("PAID", updatedOrder.getStatus());
    assertEquals("STRIPE", updatedOrder.getPayChannel());
    assertEquals("pi_test_123", updatedOrder.getPayTransactionId());
    assertNotNull(updatedOrder.getPaidAt());

    // 验证支付流水创建
    verify(paymentMapper).insert(any(PaymentEntity.class));
  }

  @Test
  void payCallback_duplicateCallback_shouldNotThrowException() {
    // 准备：已支付订单（重复回调）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setStatus("PAID"); // 已支付状态

    when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

    // 执行：重复回调不应抛异常
    orderService.payCallback("ORD20260721000001", "STRIPE", "pi_test_123");

    // 验证：没有再次更新订单或创建支付流水
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
    verify(paymentMapper, never()).insert(any(PaymentEntity.class));
  }

  // ==================== confirmReceived ====================

  @Test
  void confirmReceived_paidStatus_shouldUpdateToReceived() {
    // 准备：PAID 状态的订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PAID");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    orderService.confirmReceived(1L, 1L);

    // 验证状态更新
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals("RECEIVED", updatedOrder.getStatus());
    assertNotNull(updatedOrder.getReceivedTime());
  }

  @Test
  void confirmReceived_wrongStatus_shouldThrowIllegalStateException() {
    // 准备：PENDING_PAY 状态的订单（不允许确认收货）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行 & 验证
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> orderService.confirmReceived(1L, 1L));
    assertTrue(ex.getMessage().contains("当前订单状态不允许确认收货"));

    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  // ==================== deleteOrder ====================

  @Test
  void deleteOrder_validRequest_shouldSetDeleteStatusToOne() {
    // 准备：正常订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("RECEIVED");
    order.setDeleteStatus(0);

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    orderService.deleteOrder(1L, 1L);

    // 验证 deleteStatus 置 1
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals(Integer.valueOf(1), updatedOrder.getDeleteStatus());
  }
}
