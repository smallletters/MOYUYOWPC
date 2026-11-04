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

  /** 活动类型：满减/折扣/秒杀/拼团 */
  private String type;

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

  /** 适用商品范围：全部商品/指定品类/指定商品 */
  private String productScope;

  /** 适用品类（逗号分隔，productScope=指定品类 时有效） */
  private String productCategories;

  /** 折扣类型：直降/百分比折扣/满减 */
  private String discountType;

  /** 折扣值（如 30、30%） */
  private String discountValue;

  /** 每人限用次数 */
  private Integer perLimit;

  /** 是否启用库存限制 */
  private Boolean stockLimit;

  /** 发放总量 */
  private Integer totalCount;

  /** 审批人 */
  private String approver;

  /** 审批备注 */
  private String remark;

  /** 满减门槛金额 */
  private BigDecimal minOrderAmount;

  /** 指定商品 ID（逗号分隔） */
  private String productIds;

  /** 指定分类 ID（逗号分隔） */
  private String productCategoryIds;

  /** 已占用活动件数 */
  private Integer usedCount;

  /** 已占用活动优惠金额 */
  private BigDecimal usedAmount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
