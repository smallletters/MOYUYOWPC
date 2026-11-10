package com.moyuyo.common.dto.admin.campaign;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台营销活动视图对象(列表/详情基础信息)
 */
@Data
public class CampaignResponse {

  private Long id;

  /** 活动名称 */
  private String name;

  /** 活动类型:满减/折扣/秒杀/拼团 */
  private String type;

  /** 活动状态:ACTIVE / UPCOMING / ENDED */
  private String status;

  /** 活动描述 */
  private String description;

  /** 开始时间(前端展示为 yyyy-MM-dd HH:mm:ss 字符串) */
  private String startDate;

  /** 结束时间(前端展示为 yyyy-MM-dd HH:mm:ss 字符串) */
  private String endDate;

  /** 参与人数 */
  private Integer participants;

  /** 关联订单数（按订单明细活动归因实时统计） */
  private Integer orderCount;

  /** GMV（按订单明细活动归因实时统计，非活动表静态值） */
  private BigDecimal gmv;

  /** ROI = 实际带动 GMV / 预算 */
  private BigDecimal roi;

  /** 预算 */
  private BigDecimal budget;

  /** 成本 */
  private BigDecimal cost;

  /** 适用商品范围 */
  private String productScope;

  /** 适用品类（逗号分隔） */
  private String productCategories;

  /** 折扣类型 */
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

  /** 已占用活动件数 */
  private Integer usedCount;

  /** 已占用活动优惠金额 */
  private BigDecimal usedAmount;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;
}
