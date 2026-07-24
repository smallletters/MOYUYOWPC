package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
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

  private BigDecimal payAmount;

  private String currency;

  private String couponId;

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

  /** 是否可以申请退款（已发货/已收货/已完成可申请） */
  public boolean canRefund() {
    OrderStatusEnum s = getStatusEnum();
    return s == OrderStatusEnum.SHIPPED || s == OrderStatusEnum.RECEIVED
      || s == OrderStatusEnum.COMPLETED;
  }

  /** 是否可以发货（已支付且待发货） */
  public boolean canShip() {
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
