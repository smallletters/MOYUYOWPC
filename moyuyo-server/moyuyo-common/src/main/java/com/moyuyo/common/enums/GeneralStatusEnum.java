package com.moyuyo.common.enums;

/**
 * 通用状态枚举 — 统一业务状态值
 */
public enum GeneralStatusEnum {
  PENDING,
  APPROVED,
  REJECTED,
  PROCESSING,
  ACTIVE,
  INACTIVE;

  public String getValue() {
    return name();
  }
}
