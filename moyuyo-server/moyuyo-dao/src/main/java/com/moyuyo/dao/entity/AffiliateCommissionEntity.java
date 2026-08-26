package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_affiliate_commission")
public class AffiliateCommissionEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private Long orderId;

  private BigDecimal orderAmount;

  private BigDecimal rate;

  private BigDecimal amount;

  /** PENDING / SETTLED / WITHDRAWN */
  private String status;

  private LocalDateTime settleTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}