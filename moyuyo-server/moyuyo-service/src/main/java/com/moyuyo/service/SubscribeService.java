package com.moyuyo.service;

import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.SubscribePlanEntity;
import com.moyuyo.dao.entity.SubscriptionHistoryEntity;
import com.moyuyo.dao.entity.UserSubscriptionEntity;

import java.util.List;

public interface SubscribeService {

  List<SubscribePlanEntity> getPlans();

  /** 创建订阅订单（待支付），支付成功回调后再开通/续期订阅 */
  OrderEntity createSubscribeOrder(Long userId, Long planId);

  /** 支付成功回调：按订阅订单开通或续期订阅 */
  void activateByOrder(OrderEntity order);

  UserSubscriptionEntity getMySubscription(Long userId);

  void updateAutoRenew(Long userId, Boolean autoRenew);

  List<SubscriptionHistoryEntity> getHistory(Long userId);
}
