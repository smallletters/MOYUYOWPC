package com.moyuyo.common.enums;

/**
 * 商品评价状态（mo_product_review.status）统一枚举。
 * 历史脏值（待审核/已审核/已驳回/REPLIED）由迁移脚本归一为本枚举。
 */
public enum ReviewStatusEnum {
  /** 待审核（用户提交后） */
  PENDING,
  /** 审核通过（C 端可见；含系统默认好评） */
  APPROVED,
  /** 审核驳回 */
  REJECTED
}
