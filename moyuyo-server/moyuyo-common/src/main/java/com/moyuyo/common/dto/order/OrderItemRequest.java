package com.moyuyo.common.dto.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {

  private Long skuId;

  @NotNull(message = "商品ID不能为空")
  private Long productId;

  @NotNull(message = "购买数量不能为空")
  @Min(value = 1, message = "购买数量至少为1")
  @Max(value = 999, message = "单次购买数量不能超过999")
  private Integer quantity;
}
