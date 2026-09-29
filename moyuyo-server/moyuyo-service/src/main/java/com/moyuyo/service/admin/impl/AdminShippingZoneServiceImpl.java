package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingStrategyEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingStrategyMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.admin.AdminShippingZoneService;
import com.moyuyo.service.admin.ShippingZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
public class AdminShippingZoneServiceImpl implements AdminShippingZoneService {

  private final ShippingZoneMapper shippingZoneMapper;
  private final ShippingStrategyMapper shippingStrategyMapper;
  private final ShippingZoneService shippingZoneService;

  @Override
  public List<ShippingZoneEntity> listAll() {
    return shippingZoneMapper.selectList(
        new LambdaQueryWrapper<ShippingZoneEntity>()
            .orderByAsc(ShippingZoneEntity::getSortOrder)
            .orderByAsc(ShippingZoneEntity::getId));
  }

  @Override
  @Transactional
  public ShippingZoneEntity create(ShippingZoneCreateRequest req) {
    String codes = normalizeCountryCodes(req.getCountryCodes());
    if (codes.isEmpty()) {
      throw new BusinessException(400, "国家码不能为空");
    }
    // 业务查重（防御在 UNIQUE 索引加之前的并发）
    Long dup = shippingZoneMapper.selectCount(
        new LambdaQueryWrapper<ShippingZoneEntity>().eq(ShippingZoneEntity::getName, req.getName()));
    if (dup != null && dup > 0) {
      throw new BusinessException(409, "已存在同名区域：" + req.getName());
    }

    ShippingZoneEntity entity = new ShippingZoneEntity();
    entity.setName(req.getName());
    entity.setCountryCodes(codes);
    entity.setSortOrder(req.getSortOrder());
    entity.setRemark(req.getRemark());
    entity.setStatus(normalizeStatus(req.getStatus()));

    try {
      shippingZoneMapper.insert(entity);
    } catch (DuplicateKeyException e) {
      // UNIQUE 兜底：两个并发 create 都通过应用层查重，但 INSERT 时第二个撞唯一约束
      throw new BusinessException(409, "已存在同名区域：" + req.getName());
    }
    shippingZoneService.evictCache();
    log.info("ShippingZone created: id={}, name={}, countries={}", entity.getId(), entity.getName(), codes);
    return entity;
  }

  @Override
  @Transactional
  public ShippingZoneEntity update(Long id, ShippingZoneUpdateRequest req) {
    ShippingZoneEntity entity = shippingZoneMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(404, "发货区域不存在");
    }

    if (req.getName() != null && !req.getName().equals(entity.getName())) {
      // 业务查重
      Long dup = shippingZoneMapper.selectCount(
          new LambdaQueryWrapper<ShippingZoneEntity>()
              .eq(ShippingZoneEntity::getName, req.getName())
              .ne(ShippingZoneEntity::getId, id));
      if (dup != null && dup > 0) {
        throw new BusinessException(409, "已存在同名区域：" + req.getName());
      }
      entity.setName(req.getName());
    }

    if (req.getCountryCodes() != null) {
      String codes = normalizeCountryCodes(req.getCountryCodes());
      if (codes.isEmpty()) {
        throw new BusinessException(400, "国家码不能为空");
      }
      entity.setCountryCodes(codes);
    }
    if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
    if (req.getRemark() != null) entity.setRemark(req.getRemark());
    if (req.getStatus() != null) entity.setStatus(normalizeStatus(req.getStatus()));

    try {
      shippingZoneMapper.updateById(entity);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(409, "已存在同名区域：" + entity.getName());
    }
    shippingZoneService.evictCache();
    log.info("ShippingZone updated: id={}, name={}", entity.getId(), entity.getName());
    return entity;
  }

  @Override
  @Transactional
  public void delete(Long id) {
    // 防孤儿：被 mo_shipping_strategy 引用时拒绝删除
    Long refCount = shippingStrategyMapper.selectCount(
        new LambdaQueryWrapper<ShippingStrategyEntity>().eq(ShippingStrategyEntity::getZoneId, id));
    if (refCount != null && refCount > 0) {
      log.info("ShippingZone delete rejected (in use): id={}, refCount={}", id, refCount);
      throw new BusinessException(409, "该区域被 " + refCount
          + " 条发货策略引用，请先迁移这些策略到其他区域后再删除");
    }
    int rows = shippingZoneMapper.deleteById(id);
    if (rows > 0) shippingZoneService.evictCache();
  }

  /** 状态归一化：中文 启用/停用 → ACTIVE/INACTIVE，其他原样透传 */
  private String normalizeStatus(String status) {
    if (status == null) return "ACTIVE";
    if ("启用".equals(status)) return "ACTIVE";
    if ("停用".equals(status)) return "INACTIVE";
    return status;
  }

  /** 归一化国家码：去空格 + 转大写 + 去重 + 逗号拼接，"us, ca , gb" → "US,CA,GB" */
  static String normalizeCountryCodes(String raw) {
    if (raw == null || raw.isBlank()) return "";
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toUpperCase)
        .distinct()
        .collect(Collectors.joining(","));
  }
}