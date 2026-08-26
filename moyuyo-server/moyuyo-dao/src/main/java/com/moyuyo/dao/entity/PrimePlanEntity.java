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
@TableName("mo_prime_plan")
public class PrimePlanEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String name;

  /** PLUS / PRO / ELITE */
  private String level;

  private Integer durationMonths;

  private BigDecimal price;

  private BigDecimal originalPrice;

  /** 权益列表 JSON */
  private String benefits;

  private Integer active;

  private Integer sortOrder;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}