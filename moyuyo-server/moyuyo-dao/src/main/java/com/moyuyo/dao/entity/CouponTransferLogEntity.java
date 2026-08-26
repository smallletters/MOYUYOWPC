package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_coupon_transfer_log")
public class CouponTransferLogEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userCouponId;

  private Long fromUserId;

  private Long toUserId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime transferTime;
}