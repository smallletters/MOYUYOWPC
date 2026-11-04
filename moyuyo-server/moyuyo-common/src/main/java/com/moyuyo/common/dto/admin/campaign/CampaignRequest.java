package com.moyuyo.common.dto.admin.campaign;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台营销活动创建/更新请求
 * 创建时 id 为空,更新时 id 由路径参数提供
 */
@Data
public class CampaignRequest {

  /** 活动名称(必填) */
  @NotBlank(message = "活动名称不能为空")
  private String name;

  /** 活动类型:满减/折扣/秒杀/拼团,默认 DISCOUNT */
  private String type;

  /** 活动描述 */
  private String description;

  /** 开始时间,格式 yyyy-MM-dd 或 ISO yyyy-MM-ddTHH:mm:ss */
  private String startDate;

  /** 结束时间,格式 yyyy-MM-dd 或 ISO yyyy-MM-ddTHH:mm:ss */
  private String endDate;

  /** 预算 */
  private BigDecimal budget;

  /** 状态(仅更新时使用):ACTIVE / UPCOMING / ENDED */
  private String status;

  /** 适用商品范围：全部商品/指定品类/指定商品 */
  private String productScope;

  /** 适用品类（逗号分隔） */
  private String productCategories;

  /** 折扣类型：直降/百分比折扣/满减 */
  private String discountType;

  /** 折扣值 */
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
}
