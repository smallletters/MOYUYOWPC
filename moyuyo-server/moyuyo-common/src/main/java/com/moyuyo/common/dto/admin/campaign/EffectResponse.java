package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EffectResponse {

  private int impressions;
  private int clicks;
  private int conversions;
  private BigDecimal revenue;
  private BigDecimal roi;
}
