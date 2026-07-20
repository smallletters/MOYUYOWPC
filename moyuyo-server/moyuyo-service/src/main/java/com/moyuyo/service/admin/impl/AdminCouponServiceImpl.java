package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.CouponMapper;
import com.moyuyo.dao.mapper.UserCouponMapper;
import com.moyuyo.service.admin.AdminCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    return list.stream().map(c -> {
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
    }).collect(Collectors.toList());
  }

  @Override
  public void create(Map<String, Object> data) {
    CouponEntity entity = new CouponEntity();
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("type") != null) entity.setType((String) data.get("type"));
    if (data.get("value") != null) entity.setDiscountValue(new java.math.BigDecimal(data.get("value").toString()));
    if (data.get("minAmount") != null) entity.setMinOrderAmount(new java.math.BigDecimal(data.get("minAmount").toString()));
    if (data.get("totalCount") != null) entity.setTotalCount(Integer.valueOf(data.get("totalCount").toString()));
    entity.setActive(true);
    couponMapper.insert(entity);
  }

  @Override
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
  public void delete(Long id) {
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
