package com.moyuyo.common.dto.marketing;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * APP 结算试算请求（购物车/立即购买共用）
 */
@Data
public class MarketingQuoteRequest {

  private List<Item> items;

  @Data
  public static class Item {
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal price;
  }
}
