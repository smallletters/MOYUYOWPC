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
@TableName("mo_newuser_gift")
public class NewuserGiftEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String name;

  private BigDecimal amount;

  private Long couponId;

  private Integer points;

  private Integer claimWindowDays;

  private Integer active;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}