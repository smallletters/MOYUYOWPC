package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingStrategyEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingStrategyMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.admin.AdminShippingStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
public class AdminShippingStrategyServiceImpl implements AdminShippingStrategyService {

  /** 当 strategy 未指定 zoneId 时，默认落到 1（北美），与 Flyway V20260928_03 迁移一致 */
  private static final long DEFAULT_ZONE_ID = 1L;

  private final ShippingStrategyMapper shippingStrategyMapper;
  private final ShippingZoneMapper shippingZoneMapper;

  @Override
  public List<ShippingStrategyEntity> listAll(String statusFilter) {
    LambdaQueryWrapper<ShippingStrategyEntity> wrapper =
        new LambdaQueryWrapper<ShippingStrategyEntity>().orderByAsc(ShippingStrategyEntity::getId);
    if (statusFilter != null && !statusFilter.isEmpty()) {
      wrapper.eq(ShippingStrategyEntity::getStatus, normalizeStatus(statusFilter));
    }
    return shippingStrategyMapper.selectList(wrapper);
  }

  @Override
  public Map<Long, String> listZoneNameMap() {
    List<ShippingZoneEntity> zones = shippingZoneMapper.selectList(null);
    Map<Long, String> map = new HashMap<>(zones.size());
    for (ShippingZoneEntity z : zones) {
      map.put(z.getId(), z.getName());
    }
    return map;
  }

  @Override
  @Transactional
  public ShippingStrategyEntity create(ShippingStrategyCreateRequest req) {
    ShippingStrategyEntity entity = new ShippingStrategyEntity();
    // 字段名兼容（前端新字段名 + 旧字段名）
    entity.setName(firstNonBlank(req.getStrategyName(), req.getName()));
    entity.setRegion(req.getRegion());
    // zoneId 兜底默认 1（北美）；并校验指向存在的 zone（防运营误填不存在的 id）
    Long zoneId = req.getZoneId() != null ? req.getZoneId() : DEFAULT_ZONE_ID;
    requireZoneExists(zoneId);
    entity.setZoneId(zoneId);
    entity.setMethod(firstNonBlank(req.getShippingMethod(), req.getMethod()));
    entity.setRuleDesc(firstNonBlank(req.getFeeRule(), req.getRuleDesc()));
    entity.setPriority(req.getPriority());
    entity.setStatus(normalizeStatus(req.getStatus()));
    shippingStrategyMapper.insert(entity);
    log.info("ShippingStrategy created: id={}, name={}, zoneId={}", entity.getId(), entity.getName(), zoneId);
    return entity;
  }

  @Override
  @Transactional
  public ShippingStrategyEntity update(Long id, ShippingStrategyUpdateRequest req) {
    ShippingStrategyEntity entity = shippingStrategyMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(404, "发货策略不存在");
    }

    String name = firstNonBlank(req.getStrategyName(), req.getName());
    if (name != null) entity.setName(name);
    if (req.getRegion() != null) entity.setRegion(req.getRegion());
    if (req.getZoneId() != null && !req.getZoneId().equals(entity.getZoneId())) {
      requireZoneExists(req.getZoneId());
      entity.setZoneId(req.getZoneId());
    }
    String method = firstNonBlank(req.getShippingMethod(), req.getMethod());
    if (method != null) entity.setMethod(method);
    String rule = firstNonBlank(req.getFeeRule(), req.getRuleDesc());
    if (rule != null) entity.setRuleDesc(rule);
    if (req.getPriority() != null) entity.setPriority(req.getPriority());
    if (req.getStatus() != null) entity.setStatus(normalizeStatus(req.getStatus()));

    shippingStrategyMapper.updateById(entity);
    log.info("ShippingStrategy updated: id={}, name={}", entity.getId(), entity.getName());
    return entity;
  }

  @Override
  @Transactional
  public void delete(Long id) {
    int rows = shippingStrategyMapper.deleteById(id);
    if (rows == 0) {
      // id 不存在视作幂等成功，不抛 404（避免前端删除按钮在并发场景下报错）
      log.warn("ShippingStrategy delete: id={} not found (idempotent)", id);
    }
  }

  /** zoneId 必须指向已存在的 zone（防错配） */
  private void requireZoneExists(Long zoneId) {
    if (zoneId == null) return;
    Long count = shippingZoneMapper.selectCount(
        new LambdaQueryWrapper<ShippingZoneEntity>().eq(ShippingZoneEntity::getId, zoneId));
    if (count == null || count == 0) {
      throw new BusinessException(400, "发货区域不存在：zoneId=" + zoneId);
    }
  }

  /** 取多个字符串中第一个非 null 非空；都为空则返回 null */
  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  /** 状态归一化：中文 启用/停用 → ACTIVE/INACTIVE，其他原样透传；null 视作 ACTIVE */
  private static String normalizeStatus(String status) {
    if (status == null) return "ACTIVE";
    if ("启用".equals(status)) return "ACTIVE";
    if ("停用".equals(status)) return "INACTIVE";
    return status;
  }
}