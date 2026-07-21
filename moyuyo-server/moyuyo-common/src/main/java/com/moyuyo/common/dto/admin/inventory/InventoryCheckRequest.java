package com.moyuyo.common.dto.admin.inventory;

import lombok.Data;

@Data
public class InventoryCheckRequest {

  private Long productId;
  private int actualStock;
  private String note;
}
