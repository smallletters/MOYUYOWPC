package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 优惠券效果维度响应（按天聚合 + 单券明细）
 */
@Data
public class CouponEffectResponse {

  /** 总发放量 */
  private int totalIssued;

  /** 总核销量 */
  private int totalUsed;

  /** 综合核销率（%），保留 1 位小数 */
  private BigDecimal usageRate;

  /** 带动 GMV */
  private BigDecimal gmv;

  /** 单券效果明细（按发放量倒序，取前 N 条） */
  private List<CouponEffectItem> items;
}
