package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_after_sales")
public class AfterSalesEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 售后单号 */
  private String afterSaleNo;

  private Long orderId;

  private Long orderItemId;

  /** SKU 快照 */
  private Long skuId;

  private Long productId;

  private String productName;

  private String skuSpec;

  private String mainImage;

  /** 售后数量 */
  private Integer quantity;

  private Long userId;

  /** 售后类型：RETURN_REFUND(退货退款) / REFUND_ONLY(仅退款) / EXCHANGE(换货) */
  private String type;

  /** 退货方式：RETURN_SHIP(需寄回) / NO_RETURN(无需寄回) */
  private String deliveryType;

  private String reason;

  private BigDecimal amount;

  private String description;

  /** 凭证图片 JSON 数组 */
  private String images;

  /** 状态：PENDING/APPROVED/RETURNING/RECEIVED/COMPLETED/REJECTED/CANCELLED */
  private String status;

  private String returnCompany;

  private String returnTrackingNo;

  /** 审核通过时间：超时未寄回以该时间为基准 */
  private LocalDateTime approveTime;

  private LocalDateTime returnTime;

  private LocalDateTime receiveTime;

  private Long refundId;

  private String refundNo;

  private String rejectReason;

  private LocalDateTime rejectTime;

  private LocalDateTime completeTime;

  private Long operatorId;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
