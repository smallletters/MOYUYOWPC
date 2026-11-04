package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销活动用量记录（下单占用 -> 支付确认 / 取消释放）
 */
@Data
@TableName("mo_campaign_usage")
public class CampaignUsageEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long campaignId;

  private Long userId;

  private Long orderId;

  private String orderNo;

  /** 占用件数 */
  private Integer itemCount;

  /** 优惠金额 */
  private BigDecimal discountAmount;

  /** LOCKED / CONFIRMED / RELEASED */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
