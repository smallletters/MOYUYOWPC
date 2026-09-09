package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.prime.PrimePlanVO;
import com.moyuyo.common.dto.prime.PrimeStatusVO;
import com.moyuyo.common.dto.prime.PrimeSubscribeVO;
import com.moyuyo.dao.entity.MemberPrimeEntity;
import com.moyuyo.dao.entity.MemberPrimeEntity.Plan;
import com.moyuyo.dao.entity.MemberPrimeEntity.Status;
import com.moyuyo.dao.entity.PrimePlanEntity;
import com.moyuyo.dao.mapper.MemberPrimeMapper;
import com.moyuyo.dao.mapper.PrimePlanMapper;
import com.moyuyo.service.PrimeService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrimeServiceImpl implements PrimeService {

  private final PrimePlanMapper planMapper;
  private final MemberPrimeMapper memberPrimeMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${payment.stripe.secret-key}")
  private String stripeSecretKey;

  @Value("${payment.stripe.currency:usd}")
  private String stripeCurrency;

  @Override
  public List<PrimePlanVO> listPlans() {
    List<PrimePlanEntity> list = planMapper.selectList(
        new LambdaQueryWrapper<PrimePlanEntity>()
            .eq(PrimePlanEntity::getActive, 1)
            .orderByAsc(PrimePlanEntity::getSortOrder));
    List<PrimePlanVO> result = new ArrayList<>();
    for (PrimePlanEntity p : list) {
      result.add(toPlanVO(p));
    }
    return result;
  }

  @Override
  public PrimeStatusVO getStatus(Long userId) {
    PrimeStatusVO vo = new PrimeStatusVO();
    if (userId == null) {
      vo.setActive(false);
      return vo;
    }
    MemberPrimeEntity entity = memberPrimeMapper.selectOne(
        new LambdaQueryWrapper<MemberPrimeEntity>().eq(MemberPrimeEntity::getUserId, userId));
    if (entity == null) {
      vo.setActive(false);
      return vo;
    }
    // 自动将到期过期的订阅标记为 EXPIRED（按需惰性清理，避免定时任务）
    boolean active = entity.getStatus() == Status.ACTIVE
        && entity.getExpireAt() != null
        && entity.getExpireAt().isAfter(LocalDateTime.now());
    if (entity.getStatus() == Status.ACTIVE && !active) {
      entity.setStatus(Status.EXPIRED);
      memberPrimeMapper.updateById(entity);
    }
    vo.setActive(active);
    vo.setPlan(entity.getPlan() != null ? entity.getPlan().name() : null);
    vo.setPlanName(planName(entity.getPlan()));
    vo.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
    vo.setExpireAt(entity.getExpireAt() != null ? entity.getExpireAt().toString() : null);
    vo.setAutoRenew(entity.getStatus() == Status.ACTIVE);
    vo.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().toString() : null);
    vo.setSavedThisMonth("¥0（待统计）");
    return vo;
  }

  @Override
  @Transactional
  public PrimeSubscribeVO subscribe(Long userId, String planCode, String payChannel,
                                    String clientType, String schemeBase, String returnUrl) {
    if (userId == null) {
      throw new IllegalArgumentException("未登录");
    }
    // 解析套餐
    PrimePlanEntity plan = findActivePlan(planCode);
    String channel = payChannel == null || payChannel.isBlank() ? "STRIPE" : payChannel;

    // 未配置真实 Stripe 密钥 → 模拟直开（本地/联调，前端按 simulated=true 直接显示已开通）
    if (isPlaceholderKey(stripeSecretKey)) {
      PrimeStatusVO status = activate(userId, plan, channel, "mock_" + System.currentTimeMillis());
      return PrimeSubscribeVO.builder()
          .simulated(true)
          .status(status)
          .payChannel(channel)
          .planCode(plan.getCode())
          .planName(plan.getName())
          .build();
    }
    if (!"STRIPE".equalsIgnoreCase(channel)) {
      throw new IllegalArgumentException("当前仅支持 Stripe 支付渠道");
    }

    // ===== 真实支付：创建 Stripe Checkout Session =====
    // 先落 PENDING 占位行（同一用户仅一行），支付成功后由 webhook 转 ACTIVE
    MemberPrimeEntity entity = ensurePendingEntity(userId, plan, channel);
    Session session = createStripeSession(userId, plan, clientType, schemeBase, returnUrl);
    entity.setPaySubscriptionId(session.getId());
    memberPrimeMapper.updateById(entity);

    log.info("Prime checkout created: userId={}, planCode={}, sessionId={}",
        userId, planCode, session.getId());
    return PrimeSubscribeVO.builder()
        .simulated(false)
        .sessionUrl(session.getUrl())
        .paymentId(session.getId())
        .payChannel("STRIPE")
        .planCode(plan.getCode())
        .planName(plan.getName())
        .build();
  }

  @Override
  @Transactional
  public void handleCheckoutCompleted(Long userId, String planCode, String payChannel, String transactionId) {
    if (userId == null || planCode == null) {
      log.warn("Prime checkout.completed 缺少必要 metadata: userId={}, planCode={}", userId, planCode);
      return;
    }
    PrimePlanEntity plan = findActivePlan(planCode);
    if (plan == null) {
      log.warn("Prime checkout.completed 对应套餐不存在/已下架: planCode={}", planCode);
      return;
    }
    String channel = payChannel == null || payChannel.isBlank() ? "STRIPE" : payChannel;
    activate(userId, plan, channel, transactionId);
    log.info("Prime activated by webhook: userId={}, planCode={}, sessionId={}", userId, planCode, transactionId);
  }

  /** 激活/续期订阅：已有效未到期则从原到期日顺延，否则从现在开始计算 */
  private PrimeStatusVO activate(Long userId, PrimePlanEntity plan, String channel, String transactionId) {
    MemberPrimeEntity entity = memberPrimeMapper.selectOne(
        new LambdaQueryWrapper<MemberPrimeEntity>().eq(MemberPrimeEntity::getUserId, userId));
    LocalDateTime now = LocalDateTime.now();
    int months = plan.getDurationMonths() == null ? 1 : plan.getDurationMonths();
    // 已有 ACTIVE 且未过期 → 续期在到期日后顺延；否则按当前时间重新开通
    LocalDateTime base = (entity != null && entity.getStatus() == Status.ACTIVE
        && entity.getExpireAt() != null && entity.getExpireAt().isAfter(now))
        ? entity.getExpireAt() : now;
    LocalDateTime expireAt = base.plusMonths(months);

    if (entity == null) {
      entity = new MemberPrimeEntity();
      entity.setUserId(userId);
    }
    entity.setPlan(parsePlan(plan.getCode()));
    entity.setStatus(Status.ACTIVE);
    entity.setExpireAt(expireAt);
    entity.setPayChannel(channel != null && !channel.isBlank() ? channel : "STRIPE");
    entity.setPaySubscriptionId(transactionId);
    if (entity.getId() == null) {
      memberPrimeMapper.insert(entity);
    } else {
      memberPrimeMapper.updateById(entity);
    }
    log.info("Prime activated: userId={}, plan={}, expireAt={}, tx={}", userId, plan.getCode(), expireAt, transactionId);
    return getStatus(userId);
  }

  /** 记录一笔待支付的订阅单（仅当用户当前无有效订阅时降级为 PENDING） */
  private MemberPrimeEntity ensurePendingEntity(Long userId, PrimePlanEntity plan, String channel) {
    MemberPrimeEntity entity = memberPrimeMapper.selectOne(
        new LambdaQueryWrapper<MemberPrimeEntity>().eq(MemberPrimeEntity::getUserId, userId));
    if (entity != null && entity.getStatus() == Status.ACTIVE
        && entity.getExpireAt() != null && entity.getExpireAt().isAfter(LocalDateTime.now())) {
      // 已有未过期订阅（续费/升配）：保持 ACTIVE 展示，仅在 webhook 成功后延长期限
      return entity;
    }
    if (entity == null) {
      entity = new MemberPrimeEntity();
      entity.setUserId(userId);
    }
    entity.setPlan(parsePlan(plan.getCode()));
    entity.setStatus(Status.PENDING);
    entity.setExpireAt(null);
    entity.setPayChannel(channel != null && !channel.isBlank() ? channel : "STRIPE");
    if (entity.getId() == null) {
      memberPrimeMapper.insert(entity);
    } else {
      memberPrimeMapper.updateById(entity);
    }
    return entity;
  }

  private PrimePlanEntity findActivePlan(String planCode) {
    PrimePlanEntity plan = planMapper.selectOne(
        new LambdaQueryWrapper<PrimePlanEntity>().eq(PrimePlanEntity::getCode, planCode));
    if (plan == null || plan.getActive() == null || plan.getActive() != 1) {
      throw new IllegalArgumentException("套餐不存在或已下架");
    }
    return plan;
  }

  /**
   * 创建 Stripe Checkout Session：金额 = 套餐现价，metadata 携带 biz=prime，
   * 成功/取消回跳 Prime 页（?prime_status=success|cancel）以便前端刷新状态。
   */
  private Session createStripeSession(Long userId, PrimePlanEntity plan,
                                      String clientType, String schemeBase, String returnUrl) {
    long amountCents = plan.getPrice()
        .multiply(BigDecimal.valueOf(100))
        .setScale(0, java.math.RoundingMode.HALF_UP)
        .longValueExact();
    if (amountCents <= 0) {
      throw new IllegalArgumentException("套餐金额必须大于 0");
    }
    boolean isApp = "APP".equalsIgnoreCase(clientType);
    String successUrl = buildReturnUrl(clientType, schemeBase, returnUrl, isApp, "success");
    String cancelUrl = buildReturnUrl(clientType, schemeBase, returnUrl, isApp, "cancel");

    SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setSuccessUrl(successUrl)
        .setCancelUrl(cancelUrl)
        .setClientReferenceId(String.valueOf(userId))
        .putMetadata("biz", "prime")
        .putMetadata("user_id", String.valueOf(userId))
        .putMetadata("plan_code", plan.getCode())
        .addLineItem(SessionCreateParams.LineItem.builder()
            .setQuantity(1L)
            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(stripeCurrency)
                .setUnitAmount(amountCents)
                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName("MOYUYO Prime " + plan.getName())
                    .setDescription("MOYUYO Prime " + plan.getName() + " subscription")
                    .build())
                .build())
            .build());

    try {
      return Session.create(paramsBuilder.build());
    } catch (StripeException e) {
      log.error("Stripe Checkout Session creation failed: code={}, message={}", e.getCode(), e.getMessage(), e);
      throw new RuntimeException("Stripe payment service unavailable");
    }
  }

  /** 拼接 Checkout 成功/取消回跳地址（与订单支付一致：APP 用自定义 scheme，其余用 H5 路由） */
  private String buildReturnUrl(String clientType, String schemeBase, String returnUrl,
                                boolean isApp, String status) {
    if (isApp && StringUtils.hasText(schemeBase)) {
      return appendQuery(schemeBase, "prime_status=" + status);
    }
    // H5 / 无 scheme 的 APP：回跳到 Prime 页所在站点，由 onLoad 重新拉状态
    String root = normalizeReturnRoot(returnUrl);
    return root + "/#/pages/user/prime-page?prime_status=" + status;
  }

  private String normalizeReturnRoot(String returnUrl) {
    if (StringUtils.hasText(returnUrl)) {
      try {
        URI uri = URI.create(returnUrl);
        if (uri.getScheme() != null && uri.getHost() != null) {
          return uri.getScheme() + "://" + uri.getHost();
        }
      } catch (Exception ignore) {
        // fall through
      }
    }
    return "https://moyuyoshop.com";
  }

  private String appendQuery(String base, String query) {
    if (base == null || base.isEmpty()) return base;
    String sep = base.contains("?") ? "&" : "?";
    return base + sep + query;
  }

  /** 与订单支付一致的占位密钥判定：未配置/占位密钥时进入模拟直开 */
  private boolean isPlaceholderKey(String key) {
    if (!StringUtils.hasText(key)) return true;
    String k = key.trim();
    if (k.toLowerCase().contains("placeholder")) return true;
    return k.startsWith("sk_test_placeholder") || k.startsWith("sk_live_placeholder");
  }

  @Override
  @Transactional
  public void cancel(Long userId) {
    MemberPrimeEntity entity = memberPrimeMapper.selectOne(
        new LambdaQueryWrapper<MemberPrimeEntity>().eq(MemberPrimeEntity::getUserId, userId));
    if (entity == null || entity.getStatus() == Status.CANCELLED) {
      return;
    }
    entity.setStatus(Status.CANCELLED);
    memberPrimeMapper.updateById(entity);
  }

  // ===== 工具方法 =====

  private PrimePlanVO toPlanVO(PrimePlanEntity p) {
    PrimePlanVO vo = new PrimePlanVO();
    vo.setId(p.getId());
    vo.setCode(p.getCode());
    vo.setName(p.getName());
    vo.setDurationMonths(p.getDurationMonths());
    vo.setPrice(p.getPrice());
    vo.setOriginalPrice(p.getOriginalPrice());
    vo.setRecommend(p.getRecommend());
    vo.setCreateTime(p.getCreateTime());
    vo.setBenefits(parseBenefits(p.getBenefits()));
    return vo;
  }

  /** 解析 mo_prime_plan.benefits（JSON 数组字符串）为 List<String>，解析失败兜底为空列表 */
  private List<String> parseBenefits(String json) {
    if (json == null || json.isBlank()) return Collections.emptyList();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      log.warn("Prime benefits JSON 解析失败：{}", e.getMessage());
      return Collections.emptyList();
    }
  }

  private static Plan parsePlan(String code) {
    if (code == null) return Plan.MONTHLY;
    switch (code.toUpperCase()) {
      case "YEARLY": return Plan.ANNUAL;
      case "MONTHLY":
      default:        return Plan.MONTHLY;
    }
  }

  private static String planName(Plan plan) {
    if (plan == null) return "";
    return plan == Plan.ANNUAL ? "年付" : "月付";
  }
}