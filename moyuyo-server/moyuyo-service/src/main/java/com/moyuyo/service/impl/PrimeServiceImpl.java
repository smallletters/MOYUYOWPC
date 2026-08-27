package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.prime.PrimePlanVO;
import com.moyuyo.common.dto.prime.PrimeStatusVO;
import com.moyuyo.dao.entity.MemberPrimeEntity;
import com.moyuyo.dao.entity.MemberPrimeEntity.Plan;
import com.moyuyo.dao.entity.MemberPrimeEntity.Status;
import com.moyuyo.dao.entity.PrimePlanEntity;
import com.moyuyo.dao.mapper.MemberPrimeMapper;
import com.moyuyo.dao.mapper.PrimePlanMapper;
import com.moyuyo.service.PrimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  public PrimeStatusVO subscribe(Long userId, String planCode, String payChannel) {
    if (userId == null) {
      throw new IllegalArgumentException("未登录");
    }
    // 解析套餐
    PrimePlanEntity plan = planMapper.selectOne(
        new LambdaQueryWrapper<PrimePlanEntity>().eq(PrimePlanEntity::getCode, planCode));
    if (plan == null || plan.getActive() == null || plan.getActive() != 1) {
      throw new IllegalArgumentException("套餐不存在或已下架");
    }
    Plan primeEnum = parsePlan(planCode);

    // upsert（每个 user 只允许一条有效订阅）
    MemberPrimeEntity entity = memberPrimeMapper.selectOne(
        new LambdaQueryWrapper<MemberPrimeEntity>().eq(MemberPrimeEntity::getUserId, userId));
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expireAt = now.plusMonths(plan.getDurationMonths() == null ? 1 : plan.getDurationMonths());

    if (entity == null) {
      entity = new MemberPrimeEntity();
      entity.setUserId(userId);
    }
    entity.setPlan(primeEnum);
    entity.setStatus(Status.ACTIVE);
    entity.setExpireAt(expireAt);
    entity.setPayChannel(payChannel != null && !payChannel.isBlank() ? payChannel : "STRIPE");
    entity.setPaySubscriptionId("mock_" + System.currentTimeMillis());
    if (entity.getId() == null) {
      memberPrimeMapper.insert(entity);
    } else {
      memberPrimeMapper.updateById(entity);
    }
    log.info("Prime subscribed: userId={}, plan={}, expireAt={}", userId, planCode, expireAt);
    return getStatus(userId);
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