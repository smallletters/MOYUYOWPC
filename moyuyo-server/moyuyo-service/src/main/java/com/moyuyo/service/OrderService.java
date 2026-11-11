package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.CreatePresaleOrderRequest;
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

  /**
   * 创建预售订单。
   * <p>
   * FULL 全款预售：按 presalePrice 直接成单；
   * DEPOSIT 定金预售：按 depositAmount 生成定金单，尾款期再调用 {@link #createBalanceOrder(Long, Long)}。
   * 价格、限购、预售库存全部由服务端按 mo_product_presale 配置校验。
   */
  OrderEntity createPresaleOrder(Long userId, CreatePresaleOrderRequest request);

  /**
   * 支付尾款：为已支付定金的订单生成尾款单（幂等，已存在未取消的尾款单时直接返回）。
   *
   * @param depositOrderId 定金单 ID
   * @param userId         下单用户 ID
   */
  OrderEntity createBalanceOrder(Long depositOrderId, Long userId);

  /**
   * 处理尾款期结束仍未支付尾款的定金单：
   * <p>
   * 1) 关闭尚未支付的尾款单，避免退款后用户继续支付；
   * 2) 定金可退（deposit_refundable=true）时原路退回定金，否则没收定金并关闭订单；
   * 3) 释放定金占用的预售库存。
   *
   * @param depositOrderId 定金单 ID
   * @param reason         订单关闭原因（写入 cancel_reason，便于审计）
   * @return true=本次已处理；false=状态不满足（未支付定金/已有生效尾款单/尾款期未结束）
   */
  boolean handleExpiredDepositOrder(Long depositOrderId, String reason);

  /**
   * 分页查询订单。
   *
   * @param status           状态过滤，支持逗号分隔多状态
   * @param orderType        订单类型过滤（如 SUBSCRIPTION，订阅 tab 使用），为空不过滤
   * @param excludeOrderType 排除的订单类型（如 SUBSCRIPTION，待发货 tab 使用），为空不过滤
   */
  IPage<OrderEntity> listOrders(Long userId, int page, int size, String status,
      String orderType, String excludeOrderType);

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

  /**
   * 若订单的收件人/电话/详细地址三字段都为空，且存在 addressId，
   * 则按 addressId 回查 mo_address 并把快照字段补齐，便于详情页与列表展示。
   * 不会回写数据库（仅在内存对象上赋值）。
   * <p>
   * 用于在打印 / 燕文取号等"订单 receiver 字段可能缺失"的入口补齐数据。
   */
  void fillAddressIfAbsent(OrderEntity order);
}
