package com.moyuyo.common.enums;

/**
 * 订单状态枚举 — 统一订单生命周期状态值
 *
 * 状态流转（典型电商）：
 *   PENDING_PAY → PAID → PENDING_SHIP → SHIPPED → RECEIVED → COMPLETED
 *                                     ↘ CANCELLED（任意可取消节点）
 *                                     ↘ REFUNDING / REFUNDED
 *                                     ↘ EXCHANGING / EXCHANGED
 *   HOLD（运营拦截中间态，由 AdminOrderOpsServiceImpl.createIntercept 维护）
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
  COMPLETED("已完成"),
  EXCHANGING("换货中"),
  EXCHANGED("已换货"),
  // HOLD 不在标准状态机里，由 AdminOrderOpsServiceImpl.createIntercept 写入
  // (order.setStatus("HOLD"))，作为运营拦截中间态。OrderStatusGuard.PRINT_FROM
  // 允许从 HOLD 补打，但 SHIP_FROM / CANCEL_FROM 不允许（HOLD 显式由 releaseIntercept
  // 恢复到 PENDING_SHIP）。
  HOLD("已拦截");

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
