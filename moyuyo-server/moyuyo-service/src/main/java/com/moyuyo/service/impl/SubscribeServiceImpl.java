package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.entity.SubscribePlanEntity;
import com.moyuyo.dao.entity.SubscriptionHistoryEntity;
import com.moyuyo.dao.entity.UserSubscriptionEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.SubscribePlanMapper;
import com.moyuyo.dao.mapper.SubscriptionHistoryMapper;
import com.moyuyo.dao.mapper.UserSubscriptionMapper;
import com.moyuyo.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_EXPIRED = "EXPIRED";
  private static final String ORDER_TYPE_SUBSCRIPTION = "SUBSCRIPTION";
  /** 订阅下单用户级锁：同一用户并发下单时只允许一个请求进入创建流程 */
  private static final String SUBSCRIBE_CREATE_LOCK_PREFIX = "subscribe:order:lock:";
  /** 锁 TTL（秒）：只需覆盖一次下单耗时，避免异常时长期占锁 */
  private static final long SUBSCRIBE_CREATE_LOCK_TTL_SECONDS = 10;
  /** 释放锁：仅当 value 与持有者 token 一致才删除，避免误删他人锁 */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
      Long.class);

  private final SubscribePlanMapper subscribePlanMapper;
  private final UserSubscriptionMapper userSubscriptionMapper;
  private final SubscriptionHistoryMapper subscriptionHistoryMapper;
  private final OrderMapper orderMapper;
  private final PaymentMapper paymentMapper;
  private final StringRedisTemplate redisTemplate;

  @Override
  public List<SubscribePlanEntity> getPlans() {
    return subscribePlanMapper.selectList(
        new LambdaQueryWrapper<SubscribePlanEntity>()
            .eq(SubscribePlanEntity::getActive, true)
            .orderByAsc(SubscribePlanEntity::getSortOrder));
  }

  @Override
  @Transactional
  public OrderEntity createSubscribeOrder(Long userId, Long planId) {
    if (userId == null) {
      throw new IllegalArgumentException("未登录");
    }
    if (planId == null) {
      throw new IllegalArgumentException("请选择订阅方案");
    }
    SubscribePlanEntity plan = subscribePlanMapper.selectById(planId);
    if (plan == null || !Boolean.TRUE.equals(plan.getActive())) {
      throw new IllegalArgumentException("订阅方案不存在或已下架");
    }
    Integer durationMonths = plan.getDurationMonths();
    if (durationMonths == null || durationMonths <= 0) {
      throw new IllegalArgumentException("订阅方案时长配置异常");
    }
    // 订阅为收费服务：0 元/负价方案不允许创建订单
    if (plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("订阅方案金额必须大于 0");
    }
    // 允许已有有效订阅再次购买（作为续期），支付成功回调里按到期时间顺延，不再拦截

    // 用户级锁：同一用户并发下单时只有抢到锁的请求能继续，其余直接提示稍后再试（不自旋等待）
    String lockKey = SUBSCRIBE_CREATE_LOCK_PREFIX + userId;
    String lockToken = UUID.randomUUID().toString();
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockToken, Duration.ofSeconds(SUBSCRIBE_CREATE_LOCK_TTL_SECONDS));
    if (!Boolean.TRUE.equals(locked)) {
      throw new IllegalArgumentException("操作过于频繁，请稍后再试");
    }
    try {
      // 幂等：同一用户同时只允许一笔未支付订阅单
      // - 已存在同方案待支付单：直接复用，避免重复下单
      // - 存在其他方案的待支付单：置为 CANCELLED，避免切换方案后遗留多笔未支付单
      List<OrderEntity> pendingList = orderMapper.selectList(
          new LambdaQueryWrapper<OrderEntity>()
              .eq(OrderEntity::getUserId, userId)
              .eq(OrderEntity::getOrderType, ORDER_TYPE_SUBSCRIPTION)
              .eq(OrderEntity::getStatus, "PENDING_PAY")
              .eq(OrderEntity::getDeleteStatus, 0)
              .orderByDesc(OrderEntity::getCreateTime)
              .last("LIMIT 5"));
      for (OrderEntity pending : pendingList) {
        if (planId.equals(pending.getSubscribePlanId())) {
          // 回调延迟保护：订单已有成功支付流水但状态尚未流转时，复用会引导用户重复支付
          Long successPayments = paymentMapper.selectCount(
              new LambdaQueryWrapper<PaymentEntity>()
                  .eq(PaymentEntity::getOrderId, pending.getId())
                  .eq(PaymentEntity::getStatus, "SUCCESS"));
          if (successPayments != null && successPayments > 0) {
            throw new IllegalArgumentException("订单支付处理中，请稍后在订单列表查看");
          }
          log.info("Subscribe order reused: userId={}, planId={}, orderNo={}",
              userId, planId, pending.getOrderNo());
          return pending;
        }
        orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
            .eq(OrderEntity::getId, pending.getId())
            .eq(OrderEntity::getStatus, "PENDING_PAY")
            .set(OrderEntity::getStatus, "CANCELLED")
            .set(OrderEntity::getCancelTime, LocalDateTime.now())
            .set(OrderEntity::getCancelReason, "切换订阅方案，自动关闭旧的待支付订单"));
        log.info("Subscribe order cancelled by plan switch: userId={}, orderNo={}",
            userId, pending.getOrderNo());
      }

      String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      String snowId = String.valueOf(IdWorker.getId());

      OrderEntity order = new OrderEntity();
      // 订阅订单号与普通订单保持一致的长度规则：SUB + yyyyMMdd + 8 位雪花后缀
      order.setOrderNo("SUB" + datePart + snowId.substring(snowId.length() - 8));
      order.setUserId(userId);
      order.setStatus("PENDING_PAY");
      order.setOrderType(ORDER_TYPE_SUBSCRIPTION);
      order.setSubscribePlanId(planId);
      order.setGoodsAmount(plan.getPrice());
      order.setFreight(BigDecimal.ZERO);
      order.setTaxAmount(BigDecimal.ZERO);
      order.setCouponDiscount(BigDecimal.ZERO);
      order.setMemberDiscount(BigDecimal.ZERO);
      order.setCampaignDiscount(BigDecimal.ZERO);
      order.setPointsDiscount(BigDecimal.ZERO);
      order.setPointsUsed(0);
      order.setPayAmount(plan.getPrice());
      // 订阅是虚拟服务，不需要收货地址与物流
      order.setShippingMethod("virtual");
      order.setRemark("订阅：" + plan.getName());
      order.setDeleteStatus(0);
      orderMapper.insert(order);
      // 不创建 mo_order_item，也不写订阅记录：等支付成功回调再开通
      log.info("Subscribe order created: userId={}, planId={}, orderNo={}",
          userId, planId, order.getOrderNo());
      return order;
    } finally {
      // 关键：事务提交后再释放锁。若在方法内直接释放，"锁已释放但订单未提交"的窗口内
      // 并发请求查不到待支付单，会重复创建多笔未支付订阅单
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          releaseLock(lockKey, lockToken, userId);
        }
      });
    }
  }

  /** 释放订阅下单锁：token 一致才删除；失败只告警，不能覆盖业务异常，锁最迟在 TTL 到期后自动失效 */
  private void releaseLock(String lockKey, String lockToken, Long userId) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
    } catch (Exception e) {
      log.warn("Release subscribe order lock failed: userId={}, reason={}", userId, e.getMessage());
    }
  }

  @Override
  @Transactional
  public void activateByOrder(OrderEntity order) {
    // 非订阅订单直接跳过，不能影响普通/预售订单的支付回调
    if (order == null || !order.isSubscription() || order.getSubscribePlanId() == null) {
      return;
    }
    SubscribePlanEntity plan = subscribePlanMapper.selectById(order.getSubscribePlanId());
    if (plan == null || !Boolean.TRUE.equals(plan.getActive())) {
      // 抛异常让支付渠道重投并人工补单，避免"钱已到账但订阅未开通"被静默吞掉
      throw new IllegalStateException(
          "订阅方案不存在或已下架，需人工补单: orderNo=" + order.getOrderNo()
              + ", planId=" + order.getSubscribePlanId());
    }
    Integer durationMonths = plan.getDurationMonths();
    if (durationMonths == null || durationMonths <= 0) {
      throw new IllegalStateException(
          "订阅方案时长配置异常，需人工补单: orderNo=" + order.getOrderNo()
              + ", planId=" + plan.getId());
    }
    LocalDateTime now = LocalDateTime.now();
    UserSubscriptionEntity effective = findEffectiveSubscription(order.getUserId());
    boolean renew = effective != null;
    UserSubscriptionEntity sub;
    if (renew) {
      // 续期：从当前到期时间顺延；endTime 为空表示长期有效（与 findEffectiveSubscription 口径一致），
      // 续期时保持为空，避免把终身订阅降级为 N 个月
      sub = effective;
      if (sub.getEndTime() != null) {
        sub.setEndTime(sub.getEndTime().plusMonths(durationMonths));
      }
      sub.setPlanId(plan.getId());
      sub.setStatus(STATUS_ACTIVE);
      if (sub.getStartTime() == null) {
        sub.setStartTime(now);
      }
      if (sub.getAutoRenew() == null) {
        sub.setAutoRenew(true);
      }
      userSubscriptionMapper.updateById(sub);
    } else {
      // 非续期：复用该用户最新一条历史记录（已过期/已取消），保持一人一行
      UserSubscriptionEntity latest = userSubscriptionMapper.selectOne(
          new LambdaQueryWrapper<UserSubscriptionEntity>()
              .eq(UserSubscriptionEntity::getUserId, order.getUserId())
              .orderByDesc(UserSubscriptionEntity::getCreateTime)
              .last("LIMIT 1"));
      if (latest == null) {
        sub = new UserSubscriptionEntity();
        sub.setUserId(order.getUserId());
        sub.setPlanId(plan.getId());
        sub.setStatus(STATUS_ACTIVE);
        sub.setStartTime(now);
        sub.setEndTime(now.plusMonths(durationMonths));
        sub.setAutoRenew(true);
        userSubscriptionMapper.insert(sub);
      } else {
        sub = latest;
        sub.setPlanId(plan.getId());
        sub.setStatus(STATUS_ACTIVE);
        sub.setStartTime(now);
        sub.setEndTime(now.plusMonths(durationMonths));
        // 自动续费保持原值，历史数据为空时按开启处理
        if (sub.getAutoRenew() == null) {
          sub.setAutoRenew(true);
        }
        userSubscriptionMapper.updateById(sub);
      }
    }
    // 流水金额取订单实付金额（优惠后），不是方案标价
    SubscriptionHistoryEntity history = new SubscriptionHistoryEntity();
    history.setUserId(order.getUserId());
    history.setPlanId(plan.getId());
    history.setAction(renew ? "RENEW" : "SUBSCRIBE");
    history.setAmount(order.getPayAmount() == null ? plan.getPrice() : order.getPayAmount());
    subscriptionHistoryMapper.insert(history);
    log.info("Subscription activated by order: userId={}, orderNo={}, planId={}, renew={}, endTime={}",
        order.getUserId(), order.getOrderNo(), plan.getId(), renew, sub.getEndTime());
  }

  @Override
  public UserSubscriptionEntity getMySubscription(Long userId) {
    if (userId == null) {
      return null;
    }
    // 优先返回仍在有效期内的订阅；没有则回退到最新一条（可能是 EXPIRED/CANCELLED），供前端展示状态
    UserSubscriptionEntity effective = findEffectiveSubscription(userId);
    if (effective != null) {
      return effective;
    }
    return userSubscriptionMapper.selectOne(
        new LambdaQueryWrapper<UserSubscriptionEntity>()
            .eq(UserSubscriptionEntity::getUserId, userId)
            .orderByDesc(UserSubscriptionEntity::getCreateTime)
            .last("LIMIT 1"));
  }

  @Override
  @Transactional
  public void updateAutoRenew(Long userId, Boolean autoRenew) {
    if (userId == null) {
      throw new IllegalArgumentException("未登录");
    }
    if (autoRenew == null) {
      throw new IllegalArgumentException("请指定自动续费状态");
    }
    UserSubscriptionEntity sub = findEffectiveSubscription(userId);
    if (sub == null) {
      throw new IllegalArgumentException("没有有效的订阅");
    }
    sub.setAutoRenew(autoRenew);
    userSubscriptionMapper.updateById(sub);
    log.info("AutoRenew updated: userId={}, autoRenew={}", userId, autoRenew);
  }

  /**
   * 查询用户当前有效的订阅（status=ACTIVE 且 end_time 未过期）。
   * <p>
   * 历史缺陷修复：
   * 1. 原实现只按 status=ACTIVE 判断，end_time 到期后状态永远不会变成 EXPIRED，
   *    导致用户既无法再次订阅（被"已有有效订阅"拦住），也无法关闭自动续费；
   * 2. selectOne 在多条 ACTIVE 脏数据（并发重复订阅）时会抛 TooManyResultsException，
   *    这里统一加 LIMIT 1 兜底，避免接口 500。
   */
  private UserSubscriptionEntity findEffectiveSubscription(Long userId) {
    UserSubscriptionEntity sub = userSubscriptionMapper.selectOne(
        new LambdaQueryWrapper<UserSubscriptionEntity>()
            .eq(UserSubscriptionEntity::getUserId, userId)
            .eq(UserSubscriptionEntity::getStatus, STATUS_ACTIVE)
            .orderByDesc(UserSubscriptionEntity::getCreateTime)
            .last("LIMIT 1"));
    if (sub == null) {
      return null;
    }
    // end_time 为空视为长期有效（历史数据兜底），不为空且已过期则惰性置为 EXPIRED
    if (sub.getEndTime() != null && !sub.getEndTime().isAfter(LocalDateTime.now())) {
      sub.setStatus(STATUS_EXPIRED);
      userSubscriptionMapper.updateById(sub);
      log.info("Subscription expired: userId={}, subscriptionId={}", userId, sub.getId());
      return null;
    }
    return sub;
  }

  @Override
  public List<SubscriptionHistoryEntity> getHistory(Long userId) {
    return subscriptionHistoryMapper.selectList(
        new LambdaQueryWrapper<SubscriptionHistoryEntity>()
            .eq(SubscriptionHistoryEntity::getUserId, userId)
            .orderByDesc(SubscriptionHistoryEntity::getCreateTime));
  }
}
