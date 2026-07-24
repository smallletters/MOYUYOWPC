package com.moyuyo.common.enums;

/**
 * 支付状态枚举
 */
public enum PaymentStatusEnum {
  PENDING("待支付"),
  PROCESSING("处理中"),
  SUCCESS("支付成功"),
  FAILED("支付失败"),
  REFUNDED("已退款");

  private final String displayName;

  PaymentStatusEnum(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static PaymentStatusEnum fromValue(String value) {
    if (value == null) return null;
    String normalized = value.toUpperCase();
    for (PaymentStatusEnum status : values()) {
      if (status.name().equals(normalized)) {
        return status;
      }
    }
    return null;
  }
}
