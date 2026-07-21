package com.moyuyo.common.dto.admin.inventory;

import lombok.Data;

@Data
public class InventoryItemResponse {

  private Long id;
  private String name;
  private String sku;
  private int stock;
  private int alertThreshold;
}
