package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prime 会员套餐表（mo_prime_plan）。
 * 配套 mo_member_prime（M2P 实订阅关系）使用：
 * - PrimePlan.code = MONTHLY / YEARLY
 * - MemberPrime.plan = MONTHLY / ANNUAL（ANNUAL 对应 PrimePlan.YEARLY）
 */
@Data
@TableName("mo_prime_plan")
public class PrimePlanEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 套餐编码：MONTHLY / YEARLY */
  private String code;

  /** 套餐名称：月付 / 年付 */
  private String name;

  /** 有效期月数：1 / 12 */
  private Integer durationMonths;

  /** 现价（元） */
  private BigDecimal price;

  /** 原价（元），用于展示划线价 */
  private BigDecimal originalPrice;

  /** 权益列表（JSON 字符串） */
  private String benefits;

  /** 是否推荐：0否 1是 */
  private Integer recommend;

  /** 是否启用：0否 1是 */
  private Integer active;

  /** 排序（升序） */
  private Integer sortOrder;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}