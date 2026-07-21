package com.moyuyo.common.dto.admin.inventory;

import lombok.Data;

@Data
public class InventoryOverviewResponse {

  private int totalProducts;
  private int lowStockCount;
  private int outOfStockCount;
  private int alertCount;
}
