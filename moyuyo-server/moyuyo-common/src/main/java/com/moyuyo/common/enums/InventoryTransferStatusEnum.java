package com.moyuyo.common.enums;

/**
 * 库存调拨状态枚举 — 统一数据库与前端的状态值
 * 数据库使用大写 name(),前端通过 getValue() 获取小写值
 */
public enum InventoryTransferStatusEnum {
  PENDING("pending"),
  IN_TRANSIT("approved"),
  COMPLETED("completed"),
  REJECTED("rejected");

  private final String frontendValue;

  InventoryTransferStatusEnum(String frontendValue) {
    this.frontendValue = frontendValue;
  }

  /** 前端展示值(小写) */
  public String getFrontendValue() {
    return frontendValue;
  }

  /** 数据库存储值(大写 name()) */
  public String getDbValue() {
    return name();
  }

  /** 从前端值转换为枚举,无法识别时返回 null */
  public static InventoryTransferStatusEnum fromFrontendValue(String value) {
    if (value == null) return null;
    for (InventoryTransferStatusEnum status : values()) {
      if (status.frontendValue.equalsIgnoreCase(value)) {
        return status;
      }
    }
    return null;
  }

  /** 从数据库值转换为枚举,无法识别时返回 null */
  public static InventoryTransferStatusEnum fromDbValue(String value) {
    if (value == null) return null;
    try {
      return InventoryTransferStatusEnum.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
