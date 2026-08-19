package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 分销佣金效果维度响应
 */
@Data
public class DistributionEffectResponse {

  /** 分销员总数（最近 days 天内有过推广的用户数） */
  private int distributorCount;

  /** 活跃占比（%）：有过订单的分销员 / 分销员总数 */
  private BigDecimal activeRate;

  /** 分销渠道 GMV */
  private BigDecimal gmv;

  /** 佣金支出 */
  private BigDecimal commission;

  /** 渠道占比明细（渠道名 -> 占比 %） */
  private List<DistributionChannelShare> channels;

  /** Top 分销员排行 */
  private List<DistributionTopItem> topList;
}
