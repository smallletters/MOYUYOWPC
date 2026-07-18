package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销活动实体
 */
@Data
@TableName("mo_marketing_campaign")
public class MarketingCampaignEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 活动名称 */
  private String name;

  /** 活动状态：ACTIVE / UPCOMING / ENDED */
  private String status;

  /** 活动描述 */
  private String description;

  /** 开始时间 */
  private LocalDateTime startDate;

  /** 结束时间 */
  private LocalDateTime endDate;

  /** 参与人数 */
  private Integer participants;

  /** GMV */
  private BigDecimal gmv;

  /** 预算 */
  private BigDecimal budget;

  /** 成本 */
  private BigDecimal cost;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
