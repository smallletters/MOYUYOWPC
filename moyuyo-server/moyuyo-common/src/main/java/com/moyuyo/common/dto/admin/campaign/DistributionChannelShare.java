package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

/**
 * 分销渠道占比
 */
@Data
public class DistributionChannelShare {

  /** 渠道名（自然流量 / 付费推广 / 分销渠道） */
  private String name;

  /** 占比（%），保留整数 */
  private int ratio;
}
