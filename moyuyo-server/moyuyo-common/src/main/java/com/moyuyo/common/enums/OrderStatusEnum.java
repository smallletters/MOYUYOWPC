package com.moyuyo.common.enums;

/**
 * 订单状态枚举 — 统一订单生命周期状态值
 */
public enum OrderStatusEnum {
  PENDING_PAY("待支付"),
  PAID("已支付"),
  PENDING_SHIP("待发货"),
  SHIPPED("已发货"),
  RECEIVED("已收货"),
  CANCELLED("已取消"),
  REFUNDING("退款中"),
  REFUNDED("已退款"),
  COMPLETED("已完成");

  private final String displayName;

  OrderStatusEnum(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getValue() {
    return name();
  }

  /** 从字符串值转换，兼容之前可能存在的变体写法 */
  public static OrderStatusEnum fromValue(String value) {
    if (value == null) return null;
    // 统一转为下划线大写风格后匹配
    String normalized = value.toUpperCase()
      .replace(" ", "_")
      .replace("-", "_");
    for (OrderStatusEnum status : values()) {
      if (status.name().equals(normalized)) {
        return status;
      }
    }
    return null;
  }
}
