package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 营销活动详情响应(在 CampaignResponse 基础上附加效果统计)
 */
@Data
public class CampaignDetailResponse {

  /** 活动基础信息 */
  private CampaignResponse campaign;

  /** 效果统计 */
  private Effects effects;

  /**
   * 活动效果统计
   */
  @Data
  public static class Effects {

    /** 订单增长(预留,默认 0) */
    private int orderIncrease;

    /** 转化增长(预留,默认 0) */
    private int conversionIncrease;

    /** 客单价 */
    private BigDecimal avgOrderValue;

    /** ROI = gmv / budget,budget 为 0 时返回 0 */
    private Object roi;
  }
}
