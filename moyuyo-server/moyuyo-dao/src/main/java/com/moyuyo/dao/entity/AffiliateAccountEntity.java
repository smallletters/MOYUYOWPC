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
@TableName("mo_affiliate_account")
public class AffiliateAccountEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  /** BRONZE / SILVER / GOLD / PLATINUM */
  private String level;

  private Integer totalInvites;

  private Integer totalOrders;

  private BigDecimal totalCommission;

  private BigDecimal withdrawnAmount;

  private BigDecimal availableAmount;

  private String status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}