package com.moyuyo.common.dto.admin.finance;

import lombok.Data;

@Data
public class SettlementRequest {

  private String startDate;
  private String endDate;
  private int page;
  private int size;
}
