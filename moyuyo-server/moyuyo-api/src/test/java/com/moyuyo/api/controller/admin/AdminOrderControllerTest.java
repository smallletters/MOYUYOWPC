package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.order.OrderAddressUpdateRequest;
import com.moyuyo.common.dto.admin.order.OrderShipRequest;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.WooCommerceSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台订单 Controller 单元测试
 * 覆盖:updateAddress / ship / cancel 三个 DTO 化改造后的方法
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

  @Mock
  private OrderService orderService;

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private OrderItemMapper orderItemMapper;

  @Mock
  private UserMapper userMapper;

  @Mock
  private LogisticsService logisticsService;

  @Mock
  private WooCommerceSyncService wooCommerceSyncService;

  @InjectMocks
  private AdminOrderController adminOrderController;

  /** 构造测试订单实体 */
  private OrderEntity buildOrder(Long id, OrderStatusEnum status) {
    OrderEntity order = new OrderEntity();
    order.setId(id);
    order.setStatus(status.name());
    order.setReceiverName("原收件人");
    order.setReceiverPhone("13800000000");
    order.setReceiverAddress("原地址");
    return order;
  }

  // ============ updateAddress ============

  @Test
  void updateAddress_订单不存在_返回404() {
    // given
    when(orderService.getOrderDetail(1L, null)).thenReturn(null);

    // when
    Result<OperationResult> result = adminOrderController.updateAddress(1L, new OrderAddressUpdateRequest());

    // then
    assertEquals(404, result.getCode());
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  @Test
  void updateAddress_已发货订单_返回400() {
    // given:订单已发货,不允许修改地址
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.SHIPPED));

    // when
    Result<OperationResult> result = adminOrderController.updateAddress(1L, new OrderAddressUpdateRequest());

    // then
    assertEquals(400, result.getCode());
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  @Test
  void updateAddress_有效请求_更新对应字段并返回成功() {
    // given:待发货订单
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PENDING_SHIP));
    OrderAddressUpdateRequest request = new OrderAddressUpdateRequest();
    request.setShippingName("新收件人");
    request.setShippingPhone("13900000000");
    request.setShippingAddress("新地址");

    // when
    Result<OperationResult> result = adminOrderController.updateAddress(1L, request);

    // then:验证实体字段被正确映射并持久化
    ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
    verify(orderMapper).updateById(captor.capture());
    OrderEntity updated = captor.getValue();
    assertEquals("新收件人", updated.getReceiverName());
    assertEquals("13900000000", updated.getReceiverPhone());
    assertEquals("新地址", updated.getReceiverAddress());
    // 返回体校验
    assertEquals(0, result.getCode());
    assertNotNull(result.getData());
    assertEquals(1L, result.getData().getId());
    assertEquals("地址修改成功", result.getData().getMessage());
  }

  @Test
  void updateAddress_仅传部分字段_只更新对应字段() {
    // given:仅修改电话号码,姓名和地址保持原值
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PAID));
    OrderAddressUpdateRequest request = new OrderAddressUpdateRequest();
    request.setShippingPhone("13700000000");

    // when
    adminOrderController.updateAddress(1L, request);

    // then:姓名和地址应保持原值不被覆盖
    ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
    verify(orderMapper).updateById(captor.capture());
    OrderEntity updated = captor.getValue();
    // 注意:DTO 字段为 null 时不覆盖原值,与原 Map 逻辑保持一致
    assertEquals("原收件人", updated.getReceiverName(), "shippingName 为 null 时不应覆盖原值");
    assertEquals("13700000000", updated.getReceiverPhone());
    assertEquals("原地址", updated.getReceiverAddress(), "shippingAddress 为 null 时不应覆盖原值");
  }

  // ============ ship ============

  @Test
  void ship_订单不存在_返回404() {
    when(orderService.getOrderDetail(1L, null)).thenReturn(null);

    Result<OperationResult> result = adminOrderController.ship(1L, new OrderShipRequest());

    assertEquals(404, result.getCode());
    verify(logisticsService, never()).shipOrder(anyLong(), anyString(), anyString());
  }

  @Test
  void ship_未支付订单_返回400() {
    // given:待支付订单不允许发货
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PENDING_PAY));

    Result<OperationResult> result = adminOrderController.ship(1L, new OrderShipRequest());

    assertEquals(400, result.getCode());
    verify(logisticsService, never()).shipOrder(anyLong(), anyString(), anyString());
  }

  @Test
  void ship_已发货订单_返回400() {
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.SHIPPED));

    Result<OperationResult> result = adminOrderController.ship(1L, new OrderShipRequest());

    assertEquals(400, result.getCode());
    verify(logisticsService, never()).shipOrder(anyLong(), anyString(), anyString());
  }

  @Test
  void ship_有效请求_使用请求中的carrier和trackingNo() {
    // given:待发货订单,请求中携带完整物流信息
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PENDING_SHIP));
    OrderShipRequest request = new OrderShipRequest();
    request.setCarrier("UPS");
    request.setTrackingNo("1Z999AA10123456784");

    // when
    Result<OperationResult> result = adminOrderController.ship(1L, request);

    // then:调用 logisticsService 时应使用请求中的值
    verify(logisticsService).shipOrder(1L, "UPS", "1Z999AA10123456784");
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("发货成功", result.getData().getMessage());
  }

  @Test
  void ship_请求字段为空_使用默认值() {
    // given:待发货订单,请求未指定 carrier 和 trackingNo
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PAID));
    OrderShipRequest request = new OrderShipRequest();

    // when
    adminOrderController.ship(1L, request);

    // then:carrier 默认为"默认承运商",trackingNo 默认为空字符串
    verify(logisticsService).shipOrder(1L, "默认承运商", "");
  }

  // ============ cancel ============

  @Test
  void cancel_订单不存在_返回404() {
    when(orderService.getOrderDetail(1L, null)).thenReturn(null);

    Result<OperationResult> result = adminOrderController.cancel(1L, new CancelOrderRequest());

    assertEquals(404, result.getCode());
    verify(orderService, never()).cancelOrder(anyLong(), any(), anyString());
  }

  @Test
  void cancel_已发货订单_返回400() {
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.SHIPPED));

    Result<OperationResult> result = adminOrderController.cancel(1L, new CancelOrderRequest());

    assertEquals(400, result.getCode());
    verify(orderService, never()).cancelOrder(anyLong(), any(), anyString());
  }

  @Test
  void cancel_有效请求_使用请求中的reason() {
    // given:待支付订单,请求中指定取消原因
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PENDING_PAY));
    CancelOrderRequest request = new CancelOrderRequest();
    request.setReason("库存不足");

    // when
    Result<OperationResult> result = adminOrderController.cancel(1L, request);

    // then:调用 cancelOrder 时 reason 应为请求中的值
    verify(orderService).cancelOrder(1L, null, "库存不足");
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("订单已取消", result.getData().getMessage());
  }

  @Test
  void cancel_请求reason为空_使用默认reason() {
    // given:待支付订单,未指定 reason
    when(orderService.getOrderDetail(1L, null)).thenReturn(buildOrder(1L, OrderStatusEnum.PAID));

    // when
    adminOrderController.cancel(1L, new CancelOrderRequest());

    // then:reason 默认为"管理员操作"
    verify(orderService).cancelOrder(1L, null, "管理员操作");
  }
}
