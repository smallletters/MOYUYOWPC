package com.moyuyo.common.exception;

import com.moyuyo.common.enums.OrderStatusEnum;

import java.util.EnumSet;
import java.util.Set;

/**
 * 订单状态机守卫：集中维护"订单状态 → 操作"的合法性，
 * 避免在每个 Controller / Service 里散落字符串判断，
 * 后续状态字段新增/调整只改此处即可。
 *
 * 状态流转（典型电商）：
 *   PENDING_PAY → PAID → PENDING_SHIP → SHIPPED → RECEIVED → COMPLETED
 *                                     ↘ CANCELLED（任意可取消节点）
 *                                     ↘ REFUNDING / REFUNDED
 *                                     ↘ EXCHANGING / EXCHANGED
 *   HOLD（运营拦截中间态，由 createIntercept/releaseIntercept 维护）
 */
public final class OrderStatusGuard {

  private OrderStatusGuard() {}

  /** 发货允许的源状态：PAID / PENDING_SHIP */
  public static final Set<OrderStatusEnum> SHIP_FROM = EnumSet.of(
      OrderStatusEnum.PAID,
      OrderStatusEnum.PENDING_SHIP);

  /** 取消订单允许的源状态：未发货且未完成的订单 */
  public static final Set<OrderStatusEnum> CANCEL_FROM = EnumSet.of(
      OrderStatusEnum.PENDING_PAY,
      OrderStatusEnum.PAID,
      OrderStatusEnum.PENDING_SHIP);

  /**
   * 订单打印允许的源状态：尚未终结的单子都能补打。
   * <p>
   * 包含 HOLD（被运营拦截）—— 库房可能需要在拦截前先打印交接单。
   * 已取消（CANCELLED）/已退款（REFUNDED）订单不允许打印。
   */
  public static final Set<OrderStatusEnum> PRINT_FROM = EnumSet.of(
      OrderStatusEnum.PAID,
      OrderStatusEnum.PENDING_SHIP,
      OrderStatusEnum.SHIPPED,
      OrderStatusEnum.RECEIVED,
      OrderStatusEnum.COMPLETED,
      OrderStatusEnum.REFUNDING,
      OrderStatusEnum.EXCHANGING,
      OrderStatusEnum.EXCHANGED,
      OrderStatusEnum.HOLD);

  /**
   * 校验"从某状态执行某操作"是否合法。
   *
   * @param current 当前订单状态（数据库值，可能为旧枚举别名）
   * @param allowed 允许的源状态集合
   * @throws BusinessException 非法时抛出（code=400，由 GlobalExceptionHandler 统一处理）
   */
  public static void assertAllowed(String current, Set<OrderStatusEnum> allowed, String op) {
    OrderStatusEnum cur = OrderStatusEnum.fromValue(current);
    if (cur == null) {
      throw new BusinessException(400, "订单状态未知: " + current + "，无法" + op);
    }
    if (!allowed.contains(cur)) {
      throw new BusinessException(400,
          "订单当前状态「" + cur.getDisplayName() + "」不允许" + op + "（允许的状态：" + displayNames(allowed) + "）");
    }
  }

  private static String displayNames(Set<OrderStatusEnum> set) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (OrderStatusEnum e : set) {
      if (i++ > 0) sb.append("/");
      sb.append(e.getDisplayName());
    }
    return sb.toString();
  }
}