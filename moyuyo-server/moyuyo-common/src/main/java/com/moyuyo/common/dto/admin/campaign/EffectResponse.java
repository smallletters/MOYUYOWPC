package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台营销效果统计响应
 */
@Data
public class EffectResponse {

  /** 总 GMV(全部已完成订单) */
  private BigDecimal totalGmv;

  /** 活动期间 GMV(最近 days 天) */
  private BigDecimal campaignGmv;

  /** 活动 GMV 占比(%) */
  private BigDecimal campaignRatio;

  /** 总订单数 */
  private int totalOrders;

  /** 活动期间订单数 */
  private int campaignOrders;

  /** 平均折扣(预留,默认 0) */
  private int avgDiscount;

  /** 每日趋势数据 */
  private List<EffectTrendItem> trend;
}
