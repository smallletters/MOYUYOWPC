package com.moyuyo.common.dto.admin.finance;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SettlementDetailResponse {

  private Long id;
  private String period;
  private String status;
  private int orderCount;
  private BigDecimal totalAmount;
  private String createdAt;
}
