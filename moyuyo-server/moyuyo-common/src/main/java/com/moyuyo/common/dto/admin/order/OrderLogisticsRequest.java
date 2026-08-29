package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台：订单物流信息手动维护请求（"物流"弹窗）
 * - 用于在订单列表页点击「物流」按钮时：补录 / 修改 承运商、运单号
 * - 如订单尚未发货且运单号有效，也可一并触发发货动作（根据 forceShip 标志）
 */
@Data
public class OrderLogisticsRequest {

  /** 承运商名称（与 mo_carrier.name 或手动输入一致） */
  private String carrier;

  /** 运单号 */
  private String trackingNo;

  /**
   * 是否在订单未发货时直接发货
   * - true：若订单是待发货/已支付，则调用发货链路（创建物流记录+变更订单状态）
   * - false（默认）：只更新订单表中的 shippingCarrier / trackingNumber，以及 mo_logistics（如果有）
   */
  private Boolean forceShip;
}
