package com.moyuyo.common.dto.marketing;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * APP 结算试算响应：展示活动价、满减与应付商品金额
 */
@Data
public class MarketingQuoteResponse {

  private List<Item> items;

  /** 活动后商品小计 */
  private BigDecimal goodsAmount;

  /** 活动优惠总额（活动价优惠 + 满减） */
  private BigDecimal campaignDiscount;

  /** 整单满减优惠 */
  private BigDecimal orderDiscount;

  /** 商品应付金额（goodsAmount - orderDiscount） */
  private BigDecimal payableAmount;

  @Data
  public static class Item {
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal originalPrice;
    private BigDecimal price;
    private BigDecimal campaignDiscount;
    private Long campaignId;
    private String campaignName;
    private String campaignTag;
  }
}
