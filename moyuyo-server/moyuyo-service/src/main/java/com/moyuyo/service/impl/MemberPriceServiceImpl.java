package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.MemberPriceConfigEntity;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.MemberPriceConfigMapper;
import com.moyuyo.service.MemberPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员价（按会员等级统一折扣）配置服务实现
 *
 * <p>规则：折扣按商品销售价计算，与优惠券叠加，与秒杀不叠加。
 */
@Service
@RequiredArgsConstructor
// 抑制 JDT 对 MyBatis-Plus Lambda 方法引用的 @Nonnull 空值分析误报（项目内 MemberServiceImpl 同样处理）
@SuppressWarnings("null")
public class MemberPriceServiceImpl implements MemberPriceService {

  /** 折扣系数下限：5 折 */
  private static final BigDecimal MIN_RATE = new BigDecimal("0.50");
  /** 折扣系数上限：10 折（原价） */
  private static final BigDecimal MAX_RATE = BigDecimal.ONE;
  /** 折扣配置本地缓存有效期：60 秒（配置保存时主动失效） */
  private static final long CACHE_TTL_MS = 60_000L;

  private final MemberPriceConfigMapper memberPriceConfigMapper;
  private final MemberMapper memberMapper;

  /** 等级 → 折扣系数缓存（仅启用项），避免下单/商品详情每次查库 */
  private volatile Map<String, BigDecimal> discountCache = Collections.emptyMap();
  private volatile long cacheLoadedAt = 0L;

  @Override
  public List<MemberPriceConfigEntity> listConfigs() {
    List<MemberPriceConfigEntity> list = memberPriceConfigMapper.selectList(
        new LambdaQueryWrapper<MemberPriceConfigEntity>()
            .orderByAsc(MemberPriceConfigEntity::getId));
    // 按固定五档顺序返回，避免 id 顺序被运营误改后页面错位
    List<MemberPriceConfigEntity> ordered = new ArrayList<>();
    for (String level : LEVELS) {
      for (MemberPriceConfigEntity config : list) {
        if (level.equalsIgnoreCase(config.getLevel())) {
          ordered.add(config);
          break;
        }
      }
    }
    // 保留未识别的历史配置，避免数据“消失”
    for (MemberPriceConfigEntity config : list) {
      if (ordered.stream().noneMatch(c -> c.getId() != null && c.getId().equals(config.getId()))) {
        ordered.add(config);
      }
    }
    return ordered;
  }

  @Override
  @Transactional
  public List<MemberPriceConfigEntity> saveConfigs(List<MemberPriceConfigEntity> configs) {
    if (configs == null || configs.isEmpty()) {
      throw new IllegalArgumentException("配置不能为空");
    }
    for (MemberPriceConfigEntity incoming : configs) {
      String level = incoming.getLevel() == null ? "" : incoming.getLevel().trim().toUpperCase();
      if (!LEVELS.contains(level)) {
        throw new IllegalArgumentException("未知会员等级: " + incoming.getLevel());
      }
      BigDecimal rate = incoming.getDiscountRate();
      if (rate == null) {
        throw new IllegalArgumentException("等级 " + level + " 缺少折扣系数");
      }
      if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
        throw new IllegalArgumentException("等级 " + level + " 折扣系数需在 0.50 ~ 1.00 之间（5 折 ~ 10 折）");
      }
      if (rate.stripTrailingZeros().scale() > 2) {
        throw new IllegalArgumentException("等级 " + level + " 折扣系数最多两位小数");
      }

      MemberPriceConfigEntity existing = memberPriceConfigMapper.selectOne(
          new LambdaQueryWrapper<MemberPriceConfigEntity>()
              .eq(MemberPriceConfigEntity::getLevel, level));
      boolean enabled = incoming.getEnabled() == null || incoming.getEnabled();
      if (existing == null) {
        MemberPriceConfigEntity entity = new MemberPriceConfigEntity();
        entity.setLevel(level);
        entity.setDiscountRate(rate);
        entity.setDescription(incoming.getDescription());
        entity.setEnabled(enabled);
        memberPriceConfigMapper.insert(entity);
      } else {
        existing.setDiscountRate(rate);
        if (incoming.getDescription() != null) {
          existing.setDescription(incoming.getDescription());
        }
        existing.setEnabled(enabled);
        memberPriceConfigMapper.updateById(existing);
      }
    }
    // 配置变更后立即失效缓存，避免最长 60 秒的旧折扣生效
    invalidateCache();
    return listConfigs();
  }

  @Override
  public BigDecimal getDiscountByLevel(String level) {
    if (level == null || level.isBlank()) {
      return BigDecimal.ONE;
    }
    BigDecimal rate = enabledDiscountCache().get(level.trim().toUpperCase());
    if (rate == null) {
      return BigDecimal.ONE;
    }
    // 防御脏数据：超出 [0.50, 1.00] 视为无折扣
    if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
      return BigDecimal.ONE;
    }
    return rate;
  }

  @Override
  public BigDecimal getDiscountForUser(Long userId) {
    return getDiscountByLevel(getLevelForUser(userId));
  }

  @Override
  public String getLevelForUser(Long userId) {
    if (userId == null) {
      return "NORMAL";
    }
    MemberEntity member = memberMapper.selectOne(
        new LambdaQueryWrapper<MemberEntity>()
            .eq(MemberEntity::getUserId, userId));
    if (member == null || member.getLevel() == null) {
      return "NORMAL";
    }
    return member.getLevel().name();
  }

  /** 启用中的等级折扣缓存；过期后单线程刷新，其他线程继续读旧值 */
  private Map<String, BigDecimal> enabledDiscountCache() {
    long now = System.currentTimeMillis();
    if (cacheLoadedAt > 0L && now - cacheLoadedAt < CACHE_TTL_MS) {
      return discountCache;
    }
    synchronized (this) {
      if (cacheLoadedAt > 0L && now - cacheLoadedAt < CACHE_TTL_MS) {
        return discountCache;
      }
      Map<String, BigDecimal> fresh = new HashMap<>();
      List<MemberPriceConfigEntity> configs = memberPriceConfigMapper.selectList(
          new LambdaQueryWrapper<MemberPriceConfigEntity>()
              .eq(MemberPriceConfigEntity::getEnabled, true));
      for (MemberPriceConfigEntity config : configs) {
        if (config.getLevel() != null && config.getDiscountRate() != null) {
          fresh.put(config.getLevel().trim().toUpperCase(), config.getDiscountRate());
        }
      }
      discountCache = Collections.unmodifiableMap(fresh);
      cacheLoadedAt = now;
      return discountCache;
    }
  }

  /** 失效折扣缓存（配置保存后调用） */
  private void invalidateCache() {
    cacheLoadedAt = 0L;
    discountCache = Collections.emptyMap();
  }
}
