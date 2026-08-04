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
  public List<Map<String, Object>> listAll() {
    List<CouponEntity> list = couponMapper.selectList(
        new LambdaQueryWrapper<CouponEntity>().orderByDesc(CouponEntity::getCreateTime));
    return list.stream().map(this::toItem).collect(Collectors.toList());
  }

  @Override
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
    item.put("value", c.getDiscountValue());
    item.put("minAmount", c.getMinOrderAmount());
    item.put("status", c.getActive());
    item.put("totalCount", c.getTotalCount());
    item.put("usedCount", c.getUsedCount());
    item.put("startTime", c.getStartTime());
    item.put("endTime", c.getEndTime());
    item.put("createTime", c.getCreateTime());
    return item;
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    CouponEntity entity = new CouponEntity();
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("type") != null) entity.setType((String) data.get("type"));
    if (data.get("value") != null) entity.setDiscountValue(new java.math.BigDecimal(data.get("value").toString()));
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

  /** 解析时间字符串，支持 ISO_LOCAL_DATE_TIME 与 yyyy-MM-dd HH:mm:ss */
  private java.time.LocalDateTime parseTime(String s) {
    if (s == null || s.isEmpty()) return null;
    try {
      return java.time.LocalDateTime.parse(s);
    } catch (Exception e) {
      try {
        return java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
    if (data.get("type") != null) entity.setType((String) data.get("type"));
    if (data.get("value") != null) entity.setDiscountValue(new java.math.BigDecimal(data.get("value").toString()));
    if (data.get("status") != null) entity.setActive(Boolean.valueOf(data.get("status").toString()));
    couponMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    // 先删除用户领取的优惠券记录，再删除优惠券
    userCouponMapper.delete(new LambdaQueryWrapper<UserCouponEntity>()
        .eq(UserCouponEntity::getCouponId, id));
    couponMapper.deleteById(id);
  }

  @Override
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
