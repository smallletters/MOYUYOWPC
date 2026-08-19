package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 单张优惠券效果明细
 */
@Data
public class CouponEffectItem {

  /** 券 ID */
  private Long id;

  /** 券名称 */
  private String name;

  /** 面额 / 折扣值 */
  private BigDecimal amount;

  /** 核销率（%） */
  private BigDecimal usageRate;

  /** ROI（GMV / 优惠金额，未用优惠时为 0） */
  private BigDecimal roi;

  /** 发放量 */
  private int issued;

  /** 核销量 */
  private int used;
}
