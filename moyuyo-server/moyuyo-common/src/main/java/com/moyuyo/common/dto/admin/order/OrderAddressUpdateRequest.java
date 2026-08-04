package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台修改订单收货地址请求
 * 字段命名与前端 shippingName/shippingPhone/shippingAddress 保持一致,
 * 服务端再映射到 OrderEntity.receiverName/receiverPhone/receiverAddress
 */
@Data
public class OrderAddressUpdateRequest {

  private String shippingName;

  private String shippingPhone;

  private String shippingAddress;
}
