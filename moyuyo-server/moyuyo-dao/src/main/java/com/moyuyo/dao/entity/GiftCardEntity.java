package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mo_gift_card")
public class GiftCardEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String cardNo;

  private BigDecimal balance;

  private BigDecimal totalAmount;

  private String pin;

  private String status;

  private LocalDate expiryDate;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
