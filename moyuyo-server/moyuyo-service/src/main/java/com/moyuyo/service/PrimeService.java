package com.moyuyo.service;

import com.moyuyo.common.dto.prime.PrimePlanVO;
import com.moyuyo.common.dto.prime.PrimeStatusVO;
import com.moyuyo.common.dto.prime.PrimeSubscribeVO;

import java.util.List;

/**
 * Prime 订阅服务接口。
 * <p>
 * 责任：
 *  1) 提供套餐列表给 C 端 Prime 页面；
 *  2) 发起订阅支付（模拟直开 / Stripe Checkout，真实结果由 webhook 激活）；
 *  3) 查询/取消用户的 Prime 订阅状态（mo_member_prime）。
 */
public interface PrimeService {

  /** 拉取所有启用的套餐，按 sortOrder 升序 */
  List<PrimePlanVO> listPlans();

  /** 当前用户的 Prime 状态（未登录返回 inactive） */
  PrimeStatusVO getStatus(Long userId);

  /**
   * 发起 Prime 订阅：
   * - 支付密钥未配置（模拟开关）→ 直接落库 ACTIVE 返回 simulated=true；
   * - 已配置 Stripe 密钥 → 创建 Checkout Session 返回 sessionUrl，
   *   支付成功后由 {@link #handleCheckoutCompleted} 异步激活。
   */
  PrimeSubscribeVO subscribe(Long userId, String planCode, String payChannel,
                             String clientType, String schemeBase, String returnUrl);

  /**
   * Stripe webhook「checkout.session.completed」回调：按 metadata 激活/续期 Prime。
   *
   * @param planCode      套餐编码 MONTHLY / YEARLY
   * @param payChannel    支付渠道（STRIPE）
   * @param transactionId Stripe session id（写入 paySubscriptionId 供对账）
   */
  void handleCheckoutCompleted(Long userId, String planCode, String payChannel, String transactionId);

  /** 取消订阅（标记 CANCELLED，到期时间保留） */
  void cancel(Long userId);
}