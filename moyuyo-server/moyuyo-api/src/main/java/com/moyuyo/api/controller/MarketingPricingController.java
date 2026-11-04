package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.marketing.MarketingQuoteRequest;
import com.moyuyo.common.dto.marketing.MarketingQuoteResponse;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.impl.CampaignPricingServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * APP 端营销活动试算：活动价、满减、每人限购/总量/预算预校验。
 */
@Tag(name = "营销活动计价")
@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MarketingPricingController {

  private final CampaignPricingServiceImpl campaignPricingService;
  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;

  @Operation(summary = "购物车/结算试算")
  @PostMapping("/quote")
  public Result<MarketingQuoteResponse> quote(@RequestBody MarketingQuoteRequest request) {
    Long userId = UserContextHolder.getUserId();
    List<MarketingQuoteResponse.Item> responseItems = new ArrayList<>();
    List<OrderItemEntity> orderItems = new ArrayList<>();

    if (request != null && request.getItems() != null) {
      for (MarketingQuoteRequest.Item req : request.getItems()) {
        if (req == null || req.getProductId() == null) continue;
        ProductEntity product = productMapper.selectById(req.getProductId());
        if (product == null) continue;
        int quantity = req.getQuantity() == null || req.getQuantity() <= 0 ? 1 : req.getQuantity();

        BigDecimal basePrice = req.getPrice();
        ProductSkuEntity sku = req.getSkuId() == null ? null : productSkuMapper.selectById(req.getSkuId());
        if (sku != null && sku.getPrice() != null) {
          basePrice = sku.getPrice();
        }
        if (basePrice == null) basePrice = product.getPrice();
        if (basePrice == null) continue;

        OrderItemEntity item = new OrderItemEntity();
        item.setProductId(product.getId());
        item.setSkuId(req.getSkuId());
        item.setQuantity(quantity);
        item.setPrice(basePrice);
        orderItems.add(item);
      }
    }

    CampaignPricingServiceImpl.OrderPricing pricing =
        campaignPricingService.applyToOrder(userId, orderItems);

    BigDecimal goodsAmount = BigDecimal.ZERO;
    for (OrderItemEntity item : orderItems) {
      goodsAmount = goodsAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

      MarketingQuoteResponse.Item vo = new MarketingQuoteResponse.Item();
      vo.setProductId(item.getProductId());
      vo.setSkuId(item.getSkuId());
      vo.setQuantity(item.getQuantity());
      vo.setOriginalPrice(item.getOriginalPrice() != null ? item.getOriginalPrice() : item.getPrice());
      vo.setPrice(item.getPrice());
      vo.setCampaignDiscount(item.getCampaignDiscount() != null
          ? item.getCampaignDiscount() : BigDecimal.ZERO);
      vo.setCampaignId(item.getCampaignId());
      responseItems.add(vo);
    }

    // 补齐活动名称/标签（避免前端为每个商品再查活动）
    for (MarketingQuoteResponse.Item vo : responseItems) {
      if (vo.getCampaignId() == null) continue;
      OrderItemEntity matched = orderItems.stream()
          .filter(i -> i.getCampaignId() != null && i.getCampaignId().equals(vo.getCampaignId())
              && i.getProductId().equals(vo.getProductId()))
          .findFirst().orElse(null);
      if (matched == null) continue;
      ProductEntity product = productMapper.selectById(vo.getProductId());
      CampaignPricingServiceImpl.Context ctx = campaignPricingService.buildContext(userId);
      CampaignPricingServiceImpl.ItemPrice price = campaignPricingService.priceItem(
          product, matched.getOriginalPrice() != null ? matched.getOriginalPrice() : matched.getPrice(),
          vo.getQuantity(), userId, ctx);
      if (price != null && price.campaign != null) {
        vo.setCampaignName(price.campaign.getName());
        vo.setCampaignTag(price.tag);
      }
    }

    MarketingQuoteResponse response = new MarketingQuoteResponse();
    response.setItems(responseItems);
    response.setGoodsAmount(goodsAmount.setScale(2, RoundingMode.HALF_UP));
    response.setCampaignDiscount(pricing.totalDiscount.setScale(2, RoundingMode.HALF_UP));
    response.setOrderDiscount(pricing.orderDiscount.setScale(2, RoundingMode.HALF_UP));
    response.setPayableAmount(goodsAmount.subtract(pricing.orderDiscount)
        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    return Result.success(response);
  }
}
