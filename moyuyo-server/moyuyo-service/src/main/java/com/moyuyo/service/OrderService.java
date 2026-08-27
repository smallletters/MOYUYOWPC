package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService {

  OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark, String couponId);

  /**
   * 创建订单（含运费、优惠券、积分抵扣）
   *
   * @param userId         用户 ID
   * @param items          订单商品列表
   * @param addressId      收货地址 ID
   * @param remark         订单备注
   * @param couponId       优惠券 ID（可空）
   * @param couponDiscount 优惠券减免金额（BigDecimal.ZERO 表示无）
   * @param pointsUsed     使用的积分数
   * @param pointsDiscount 积分抵扣金额
   * @param shippingMethod 配送方式 standard/express
   * @param freight        运费
   */
  OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark,
                          String couponId, BigDecimal couponDiscount, Integer pointsUsed,
                          BigDecimal pointsDiscount, String shippingMethod, BigDecimal freight);

  /**
   * 从请求对象创建订单，内部处理 SKU/商品校验和订单项组装
   */
  OrderEntity createOrderFromRequest(Long userId, CreateOrderRequest request);

  IPage<OrderEntity> listOrders(Long userId, int page, int size, String status);

  OrderEntity getOrderDetail(Long orderId, Long userId);

  void cancelOrder(Long orderId, Long userId, String reason);

  void payCallback(String orderNo, String payChannel, String transactionId);

  void confirmReceived(Long orderId, Long userId);

  void deleteOrder(Long orderId, Long userId);

  List<OrderItemEntity> getOrderItems(Long orderId);

  /**
   * 批量获取订单项（消除 N+1 查询）
   *
   * @param orderIds 订单 ID 列表
   * @return Map<orderId, List<OrderItemEntity>>，订单无项时 Map 中无该 key
   */
  Map<Long, List<OrderItemEntity>> getOrderItemsByOrderIds(List<Long> orderIds);

  OrderEntity getOrderByOrderNo(String orderNo);
}
