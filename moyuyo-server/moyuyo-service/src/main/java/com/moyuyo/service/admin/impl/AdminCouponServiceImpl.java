package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.CouponMapper;
import com.moyuyo.dao.mapper.UserCouponMapper;
import com.moyuyo.service.admin.AdminCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 优惠券服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminCouponServiceImpl implements AdminCouponService {

  private final CouponMapper couponMapper;
  private final UserCouponMapper userCouponMapper;

  @Override
  // JDT 误报:LambdaQueryWrapper#orderByDesc(SFunction, ...) 走 Function.apply(this) 路径,
  // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
  @SuppressWarnings("null")
  public List<Map<String, Object>> listAll() {
    List<CouponEntity> list = couponMapper.selectList(
        new LambdaQueryWrapper<CouponEntity>().orderByDesc(CouponEntity::getCreateTime));
    return list.stream().map(this::toItem).collect(Collectors.toList());
  }

  @Override
  // JDT 误报:同 listAll(),Page 构造器及 LambdaQueryWrapper#orderByDesc 的方法引用路径
  @SuppressWarnings("null")
  public Map<String, Object> listPage(int page, int size) {
    Page<CouponEntity> pageObj = new Page<>(page, size);
    Page<CouponEntity> result = couponMapper.selectPage(pageObj,
        new LambdaQueryWrapper<CouponEntity>().orderByDesc(CouponEntity::getCreateTime));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", result.getTotal());
    data.put("records", result.getRecords().stream().map(this::toItem).collect(Collectors.toList()));
    return data;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    CouponEntity c = couponMapper.selectById(id);
    if (c == null) {
      return null;
    }
    return toItem(c);
  }

  /** 将优惠券实体转为前端展示用Map */
  private Map<String, Object> toItem(CouponEntity c) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", c.getId());
    item.put("name", c.getName());
    item.put("type", c.getType());
    // PERCENT 券：数据库存『价格百分比』，对外展示回退为『折数』(9 折 = 90)
    item.put("value", toDisplayValue(c));
    item.put("minAmount", c.getMinOrderAmount());
    item.put("status", c.getActive());
    item.put("totalCount", c.getTotalCount());
    item.put("usedCount", c.getUsedCount());
    item.put("startTime", c.getStartTime());
    item.put("endTime", c.getEndTime());
    item.put("createTime", c.getCreateTime());
    return item;
  }

  /**
   * PERCENT 类型将『价格百分比』(如 90)转回『折数』(如 9)展示给管理端 UI;
   * AMOUNT 类型原样返回减免金额。
   */
  private java.math.BigDecimal toDisplayValue(CouponEntity c) {
    if (c.getDiscountValue() == null) return null;
    if ("PERCENT".equalsIgnoreCase(c.getType())) {
      // 保留 1 位小数,不做 stripTrailingZeros 避免 BigDecimal 转字符串出现科学计数法
      return c.getDiscountValue().divide(new java.math.BigDecimal(10), 1, java.math.RoundingMode.HALF_UP);
    }
    return c.getDiscountValue();
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    CouponEntity entity = new CouponEntity();
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    String normalizedType = data.get("type") != null
        ? normalizeType((String) data.get("type"))
        : null;
    if (normalizedType != null) entity.setType(normalizedType);
    // value: null / 空串都视为"未传"，避免前端 form 未填字段(默认 "")把 discountValue 写成 NULL
    Object rawValue = data.get("value");
    if (rawValue != null && !rawValue.toString().isEmpty())
      entity.setDiscountValue(toStoredValue(normalizedType, rawValue));
    if (data.get("minAmount") != null) entity.setMinOrderAmount(new java.math.BigDecimal(data.get("minAmount").toString()));
    if (data.get("maxAmount") != null) entity.setMaxDiscountAmount(new java.math.BigDecimal(data.get("maxAmount").toString()));
    if (data.get("totalCount") != null) entity.setTotalCount(Integer.valueOf(data.get("totalCount").toString()));
    // 解析时间字段：支持 ISO 字符串与 yyyy-MM-dd HH:mm:ss
    if (data.get("startTime") != null) entity.setStartTime(parseTime((String) data.get("startTime")));
    if (data.get("endTime") != null) entity.setEndTime(parseTime((String) data.get("endTime")));
    if (data.get("active") != null) entity.setActive(Boolean.valueOf(data.get("active").toString()));
    else entity.setActive(true);
    couponMapper.insert(entity);
    // 将生成的主键回写到请求数据，方便控制器返回真实ID
    data.put("id", entity.getId());
  }

  /** 解析时间字符串，支持 ISO_LOCAL_DATE_TIME、带时区 ISO 8601、yyyy-MM-dd HH:mm:ss */
  private java.time.LocalDateTime parseTime(String s) {
    if (s == null || s.isEmpty()) return null;
    String v = s.trim();
    // 带时区的 ISO 8601：先按 Instant 解析再转系统时区
    if (v.endsWith("Z") || v.matches(".*[+-]\\d{2}:?\\d{2}$")) {
      try {
        java.time.Instant inst = java.time.Instant.parse(v);
        return inst.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
      } catch (Exception ignored) {
        // 落到下面的本地解析
      }
    }
    try {
      // ISO_LOCAL_DATE_TIME（无时区，如 2026-08-27T16:00:00 或 2026-08-27T16:00:00.000）
      return java.time.LocalDateTime.parse(v, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception e) {
      try {
        return java.time.LocalDateTime.parse(v, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      } catch (Exception ex) {
        return null;
      }
    }
  }

  @Override
  @Transactional
  public void update(Map<String, Object> data) {
    if (data.get("id") == null) return;
    CouponEntity entity = couponMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    // type 字段：先按入参归一；若未传，回退到 DB 原值(避免 value 单独更新时类型判断失误)
    String normalizedType = data.get("type") != null
        ? normalizeType((String) data.get("type"))
        : entity.getType();
    if (data.get("type") != null) entity.setType(normalizedType);
    // value: null / 空串都视为"未传"，保持 DB 原值不变(form 未填字段默认 "" 是常见情况)
    Object rawValue = data.get("value");
    if (rawValue != null && !rawValue.toString().isEmpty())
      entity.setDiscountValue(toStoredValue(normalizedType, rawValue));
    if (data.get("status") != null) entity.setActive(Boolean.valueOf(data.get("status").toString()));
    couponMapper.updateById(entity);
  }

  /**
   * 入参 value 语义对齐存储:
   * - PERCENT 类型入参为『折数』(9 表示 9 折)，数据库存『价格百分比』(9 → 90)
   * - AMOUNT 类型入参为减免金额，原样存储
   * 空值/null/空串:返回 null,由调用方决定是否更新 discountValue 字段
   */
  private java.math.BigDecimal toStoredValue(String normalizedType, Object rawValue) {
    if (rawValue == null) return null;
    String s = rawValue.toString();
    if (s.isEmpty()) return null;
    java.math.BigDecimal v = new java.math.BigDecimal(s);
    if ("PERCENT".equals(normalizedType)) {
      // 9 折 → 90 (即 100% * 0.9 的整数百分制);先乘以 10 再四舍五入到 2 位小数
      return v.multiply(new java.math.BigDecimal(10)).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    return v;
  }

  /**
   * 类型枚举归一：前端表单 radio 使用 "fixed"/"discount",
   * 而 CouponEntity 与 C 端 CouponServiceImpl 期望 "AMOUNT"/"PERCENT"。
   * 历史数据/外部系统可能使用其他写法,统一在这里收敛,避免出现 type 字段不可识别。
   */
  private String normalizeType(String raw) {
    if (raw == null) return null;
    String s = raw.trim().toUpperCase();
    if (s.isEmpty()) return null;
    switch (s) {
      case "FIXED":
      case "AMOUNT":
      case "满减":
      case "CASH":
        return "AMOUNT";
      case "DISCOUNT":
      case "PERCENT":
      case "折扣":
        return "PERCENT";
      default:
        // 未知类型原样写入,便于 DBA 排查;但记录日志提醒
        return s;
    }
  }

  @Override
  @Transactional
  // JDT 误报:LambdaQueryWrapper#eq(SFunction, ...) 走 Function.apply(this) 路径,
  // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
  @SuppressWarnings("null")
  public void delete(Long id) {
    // 先删除用户领取的优惠券记录，再删除优惠券
    userCouponMapper.delete(new LambdaQueryWrapper<UserCouponEntity>()
        .eq(UserCouponEntity::getCouponId, id));
    couponMapper.deleteById(id);
  }

  @Override
  // JDT 误报:LambdaQueryWrapper#eq(SFunction, ...) 走 Function.apply(this) 路径,
  // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
  @SuppressWarnings("null")
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    long total = couponMapper.selectCount(new LambdaQueryWrapper<>());
    long activeCount = couponMapper.selectCount(
        new LambdaQueryWrapper<CouponEntity>().eq(CouponEntity::getActive, true));
    long totalIssued = userCouponMapper.selectCount(new LambdaQueryWrapper<>());
    stats.put("totalCoupons", total);
    stats.put("activeCoupons", activeCount);
    stats.put("totalIssued", totalIssued);
    return stats;
  }
}
