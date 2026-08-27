package com.moyuyo.service;

import com.moyuyo.common.dto.prime.PrimePlanVO;
import com.moyuyo.common.dto.prime.PrimeStatusVO;

import java.util.List;

/**
 * Prime 订阅服务接口。
 * <p>
 * 责任：
 *  1) 提供套餐列表给 C 端 Prime 页面；
 *  2) 查询/激活/取消用户的 Prime 订阅状态（mo_member_prime）。
 */
public interface PrimeService {

  /** 拉取所有启用的套餐，按 sortOrder 升序 */
  List<PrimePlanVO> listPlans();

  /** 当前用户的 Prime 状态（未登录返回 inactive） */
  PrimeStatusVO getStatus(Long userId);

  /**
   * 订阅 Prime（dev/mock：直接落库 ACTIVE，prod 应通过支付平台 webhook 异步激活）
   * - planCode：MONTHLY / YEARLY
   * - payChannel：STRIPE / PAYPAL / WECHAT / ALIPAY
   */
  PrimeStatusVO subscribe(Long userId, String planCode, String payChannel);

  /** 取消订阅（标记 CANCELLED，到期时间保留） */
  void cancel(Long userId);
}