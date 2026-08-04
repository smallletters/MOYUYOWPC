package com.moyuyo.common.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

  @NotEmpty(message = "订单商品不能为空")
  @Valid
  private List<OrderItemRequest> items;

  @NotNull(message = "收货地址不能为空")
  private Long addressId;

  private String remark;

  private String couponId;
}
