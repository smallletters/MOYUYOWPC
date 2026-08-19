package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 秒杀效果维度响应
 */
@Data
public class FlashEffectResponse {

  /** 参与率（%）：秒杀订单用户数 / 活动用户数 */
  private BigDecimal participationRate;

  /** 成交率（%）：已支付秒杀订单 / 秒杀下单总数 */
  private BigDecimal conversionRate;

  /** 平均售罄时长（分钟） */
  private BigDecimal avgSelloutMinutes;

  /** 秒杀总 GMV */
  private BigDecimal gmv;

  /** 秒杀场次明细 */
  private List<FlashEffectItem> items;
}
