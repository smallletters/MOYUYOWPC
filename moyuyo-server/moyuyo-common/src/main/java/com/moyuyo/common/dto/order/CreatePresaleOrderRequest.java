package com.moyuyo.common.dto.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 预售下单请求。
 * <p>
 * 预售商品必须单独成单（一个订单只包含一个预售商品），
 * 价格由服务端按 mo_product_presale 配置权威计算，前端传入的金额一律忽略。
 */
@Data
public class CreatePresaleOrderRequest {

  @NotNull(message = "商品不能为空")
  private Long productId;

  /** SKU ID（无变体商品可不传） */
  private Long skuId;

  @NotNull(message = "购买数量不能为空")
  @Min(value = 1, message = "购买数量必须大于 0")
  // 上限校验：避免超大数量绕过库存/限购配置，生成巨额订单或把 sold_count 刷爆
  @Max(value = 999, message = "单次购买数量不能超过 999")
  private Integer quantity;

  @NotNull(message = "收货地址不能为空")
  private Long addressId;

  @Size(max = 500, message = "订单备注不能超过 500 字")
  private String remark;

  /** 配送方式：standard / express */
  @Size(max = 32, message = "配送方式参数不合法")
  private String shippingMethod;
}
