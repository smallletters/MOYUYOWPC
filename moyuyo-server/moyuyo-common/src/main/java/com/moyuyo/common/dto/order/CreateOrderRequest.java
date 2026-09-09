package com.moyuyo.common.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {

  @NotEmpty(message = "订单商品不能为空")
  @Valid
  private List<OrderItemRequest> items;

  @NotNull(message = "收货地址不能为空")
  private Long addressId;

  private String remark;

  /** 优惠券 ID（用户优惠券编码）；空表示不使用优惠券 */
  private String couponId;

  /** 用户优惠券记录 ID（mo_user_coupon.id），服务端据此核验归属/状态并重算优惠金额 */
  private Long couponUserId;

  /** 优惠券减免金额（仅作展示参考，服务端会按 couponUserId 重算后覆盖） */
  private BigDecimal couponDiscount;

  /** 用户希望使用的积分数；后端校验余额 + 上限后落库 */
  private Integer pointsUsed;

  /** 积分抵扣金额（前端结算页计算后传入），与 pointsUsed 一并使用 */
  private BigDecimal pointsDiscount;

  /** 配送方式：standard / express；订单落库便于后续物流履约 */
  private String shippingMethod;

  /** 配送费用（前端按配送方式计算后传入） */
  @DecimalMin(value = "0", message = "运费不能为负数")
  private BigDecimal freight;
}
