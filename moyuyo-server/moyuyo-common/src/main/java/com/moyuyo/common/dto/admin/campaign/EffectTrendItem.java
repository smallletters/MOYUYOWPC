package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 营销效果趋势数据项(按天聚合)
 */
@Data
public class EffectTrendItem {

  /** 日期,格式 MM-dd */
  private String date;

  /** 当天 GMV */
  private BigDecimal gmv;

  /** 当天订单数 */
  private int orders;
}
