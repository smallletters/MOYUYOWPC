package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CampaignRequest {

  private String name;
  private String type;
  private String startDate;
  private String endDate;
  private String description;
  private BigDecimal budget;
}
