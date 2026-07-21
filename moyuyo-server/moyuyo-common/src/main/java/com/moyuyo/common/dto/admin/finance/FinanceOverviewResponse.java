package com.moyuyo.common.dto.admin.finance;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceOverviewResponse {

  private BigDecimal totalRevenue;
  private BigDecimal pendingSettlement;
  private int completedSettlements;
  private int pendingCount;
}
