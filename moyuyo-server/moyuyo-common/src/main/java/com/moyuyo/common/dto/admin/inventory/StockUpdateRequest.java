package com.moyuyo.common.dto.admin.inventory;

import lombok.Data;

@Data
public class StockUpdateRequest {

  private int stock;
  private String reason;
}
