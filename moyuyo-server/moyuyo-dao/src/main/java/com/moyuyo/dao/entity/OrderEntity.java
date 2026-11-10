package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.moyuyo.common.dto.refund.RefundVO;
import com.moyuyo.common.enums.OrderStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mo_order")
public class OrderEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String orderNo;

  private Long userId;

  private Long addressId;

  private BigDecimal goodsAmount;

  private BigDecimal freight;

  private BigDecimal taxAmount;

  private BigDecimal couponDiscount;

  private BigDecimal pointsDiscount;

  /** 会员等级优惠金额（按商品销售价 × (1 - 等级折扣系数)，秒杀商品不参与） */
  private BigDecimal memberDiscount;

  /** 营销活动优惠金额（活动价优惠 + 满减） */
  private BigDecimal campaignDiscount;

  private BigDecimal payAmount;

  private String currency;

  private String couponId;

  /** 下单使用的用户优惠券记录 ID（mo_user_coupon.id）；下单即核销，未支付取消时返还 */
  private Long userCouponId;

  private Integer pointsUsed;

  private String status;

  private String payChannel;

  private String payTransactionId;

  private Long wooOrderId;

  private Integer syncStatus;

  private Integer syncRetryCount;

  private LocalDateTime syncLastTime;

  private String trackingNumber;

  private String shippingCarrier;

  private String remark;

  private String senderName;

  private String senderPhone;

  private String senderAddress;

  private String receiverName;

  private String receiverPhone;

  private String receiverAddress;

  private String receiverZip;

  private String shippingMethod;

  private String deliveryNote;

  /** 购买须知（非表字段，来自订单商品 attributes.purchase_note，去重拼接） */
  @TableField(exist = false)
  private String purchaseNote;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  private LocalDateTime paidAt;

  private LocalDateTime cancelTime;

  private String cancelReason;

  private LocalDateTime deliverTime;

  private LocalDateTime receivedTime;

  private Integer deleteStatus;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  @TableField(exist = false)
  private List<OrderItemEntity> items;

  /** 用户昵称（非表字段，管理后台列表展示用） */
  @TableField(exist = false)
  private String userName;

  /**
   * 订单支付方式的友好名（非表字段，详情接口填充后返回给前端直接显示）。
   * 例：PayPal、Apple Pay、Google Pay、Venmo、Alipay、Stripe Card 等
   * 空值表示未支付（PENDING_PAY），显示"待支付"。
   */
  @TableField(exist = false)
  private String payMethodName;

  /** 已退款金额（非表字段，管理后台详情展示用；含退款中与已完成） */
  @TableField(exist = false)
  private BigDecimal refundedAmount;

  /** 剩余可退金额（非表字段，管理后台退款弹窗默认值） */
  @TableField(exist = false)
  private BigDecimal refundableAmount;

  /** 该订单的退款记录（非表字段，管理后台详情页退款历史展示用） */
  @TableField(exist = false)
  private List<RefundVO> refunds;

  /** 该订单最近一条售后记录 ID（非表字段，无售后为 null，APP 据此决定是否展示"售后记录"入口） */
  @TableField(exist = false)
  private Long afterSalesId;

  /** 最近一条售后记录状态（非表字段） */
  @TableField(exist = false)
  private String afterSalesStatus;

  // ===== 领域方法 =====

  /** 获取订单状态枚举 */
  public OrderStatusEnum getStatusEnum() {
    return OrderStatusEnum.fromValue(status);
  }

  /** 是否已支付 */
  public boolean isPaid() {
    OrderStatusEnum s = getStatusEnum();
    return s != null && (s == OrderStatusEnum.PAID || s == OrderStatusEnum.PENDING_SHIP
      || s == OrderStatusEnum.SHIPPED || s == OrderStatusEnum.RECEIVED
      || s == OrderStatusEnum.COMPLETED);
  }

  /** 是否可以取消（仅待支付和已支付状态可取消） */
  public boolean canCancel() {
    OrderStatusEnum s = getStatusEnum();
    return s == OrderStatusEnum.PENDING_PAY || s == OrderStatusEnum.PAID;
  }

  /** 是否可以申请退款（已支付（含待发货）/已发货/已收货/已完成可申请） */
  public boolean canRefund() {
    OrderStatusEnum s = getStatusEnum();
    return s == OrderStatusEnum.PAID || s == OrderStatusEnum.PENDING_SHIP
      || s == OrderStatusEnum.SHIPPED || s == OrderStatusEnum.RECEIVED
      || s == OrderStatusEnum.COMPLETED;
  }

  /** 是否可以发货（已支付或待发货） */
  public boolean canShip() {
    OrderStatusEnum s = getStatusEnum();
    return s == OrderStatusEnum.PAID || s == OrderStatusEnum.PENDING_SHIP;
  }

  /** 是否可以发货（仅待发货，排除已拦截等中间态） */
  public boolean isShippable() {
    return getStatusEnum() == OrderStatusEnum.PENDING_SHIP;
  }

  /** 确认收货 */
  public void confirmReceive() {
    this.status = OrderStatusEnum.RECEIVED.name();
    this.receivedTime = LocalDateTime.now();
  }

  /** 标记为已完成 */
  public void complete() {
    this.status = OrderStatusEnum.COMPLETED.name();
  }
}
