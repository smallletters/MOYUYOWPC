package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Prime订阅 状态表（mo_member_prime）。
 * <p>
 * 字段与 V7__init_extra_tables.sql 保持一致，避免 Flyway 校验失败。
 * - plan：MONTHLY（兼容 PrimePlan.code）/ ANNUAL（对应 PrimePlan.YEARLY）
 * - status：ACTIVE / CANCELLED / EXPIRED
 */
@Data
@TableName("mo_member_prime")
public class MemberPrimeEntity {

  public enum Plan {
    MONTHLY, ANNUAL
  }

  public enum Status {
    ACTIVE, CANCELLED, EXPIRED
  }

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private Plan plan;

  /** 支付渠道：STRIPE / PAYPAL / WECHAT / ALIPAY */
  private String payChannel;

  /** 支付平台返回的订阅 ID（用于取消/续费） */
  private String paySubscriptionId;

  private Status status;

  /** 到期时间；CANCELLED/EXPIRED 后此字段保留为历史到期日 */
  private LocalDateTime expireAt;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}