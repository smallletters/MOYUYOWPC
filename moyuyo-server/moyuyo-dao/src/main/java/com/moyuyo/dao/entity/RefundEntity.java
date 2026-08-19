package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_refund")
public class RefundEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long orderId;

  private String refundNo;

  private String type;

  private BigDecimal amount;

  private String reason;

  private String description;

  private String images;

  // 拆单退款明细（V20260818_03 新增）：JSON 数组 [{skuId, quantity, amount, reason}]
  private String items;

  private String status;

  private Long wooRefundId;

  // 拒绝信息（V20260818_01 新增）
  private String rejectReason;

  private Long rejectOperatorId;

  private LocalDateTime rejectTime;

  // 完成信息（V20260818_01 新增）
  private Long completeOperatorId;

  private String transactionId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  private LocalDateTime completeTime;
}
