package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.CarrierCreateRequest;
import com.moyuyo.common.dto.admin.logistics.CarrierUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.CarrierEntity;
import com.moyuyo.dao.admin.mapper.CarrierMapper;
import com.moyuyo.service.admin.AdminCarrierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCarrierServiceImpl implements AdminCarrierService {

  private final CarrierMapper carrierMapper;

  @Override
  @SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
  public List<CarrierEntity> listAll(String statusFilter) {
    LambdaQueryWrapper<CarrierEntity> wrapper =
        new LambdaQueryWrapper<CarrierEntity>().orderByAsc(CarrierEntity::getId);
    if (statusFilter != null && !statusFilter.isEmpty()) {
      wrapper.eq(CarrierEntity::getStatus, normalizeStatus(statusFilter));
    }
    return carrierMapper.selectList(wrapper);
  }

  @Override
  @Transactional
  public CarrierEntity create(CarrierCreateRequest req) {
    CarrierEntity e = new CarrierEntity();
    e.setName(req.getName());
    e.setTransportMode(req.getTransportMode());
    e.setAvgDeliveryDays(req.getAvgDeliveryDays());
    e.setFirstWeightPrice(req.getFirstWeightPrice());
    e.setRenewWeightPrice(req.getRenewWeightPrice());
    e.setPraiseRate(req.getPraiseRate());
    e.setStatus(normalizeStatus(req.getStatus()));
    // API 凭证与产品编码：承运商记录可独立覆盖 .env 全局默认（YanWenOrderCreator:144 优先取本字段）
    e.setCode(req.getCode());
    e.setApiUserId(req.getApiUserId());
    e.setApiToken(req.getApiToken());
    e.setChannelId(req.getChannelId());
    e.setApiBaseUrl(req.getApiBaseUrl());
    e.setLabelApiEnabled(req.getLabelApiEnabled());
    e.setApiRemark(req.getApiRemark());
    carrierMapper.insert(e);
    log.info("Carrier created: id={}, name={}, code={}", e.getId(), e.getName(), e.getCode());
    return e;
  }

  @Override
  @Transactional
  public CarrierEntity update(Long id, CarrierUpdateRequest req) {
    CarrierEntity e = carrierMapper.selectById(id);
    if (e == null) {
      throw new BusinessException(404, "承运商不存在");
    }
    if (req.getName() != null) e.setName(req.getName());
    if (req.getTransportMode() != null) e.setTransportMode(req.getTransportMode());
    if (req.getAvgDeliveryDays() != null) e.setAvgDeliveryDays(req.getAvgDeliveryDays());
    if (req.getFirstWeightPrice() != null) e.setFirstWeightPrice(req.getFirstWeightPrice());
    if (req.getRenewWeightPrice() != null) e.setRenewWeightPrice(req.getRenewWeightPrice());
    if (req.getPraiseRate() != null) e.setPraiseRate(req.getPraiseRate());
    if (req.getStatus() != null) e.setStatus(normalizeStatus(req.getStatus()));
    // partial update：API 凭证/产品编码字段保持与 create 一致，全部 nullable
    if (req.getCode() != null) e.setCode(req.getCode());
    if (req.getApiUserId() != null) e.setApiUserId(req.getApiUserId());
    if (req.getApiToken() != null) e.setApiToken(req.getApiToken());
    if (req.getChannelId() != null) e.setChannelId(req.getChannelId());
    if (req.getApiBaseUrl() != null) e.setApiBaseUrl(req.getApiBaseUrl());
    if (req.getLabelApiEnabled() != null) e.setLabelApiEnabled(req.getLabelApiEnabled());
    if (req.getApiRemark() != null) e.setApiRemark(req.getApiRemark());
    carrierMapper.updateById(e);
    log.info("Carrier updated: id={}, name={}", e.getId(), e.getName());
    return e;
  }

  @Override
  @Transactional
  public void delete(Long id) {
    int rows = carrierMapper.deleteById(id);
    if (rows == 0) {
      log.warn("Carrier delete: id={} not found (idempotent)", id);
    }
  }

  /** 状态归一化：中文 → ACTIVE/INACTIVE；null 视作 ACTIVE */
  static String normalizeStatus(String status) {
    if (status == null) return "ACTIVE";
    if ("启用".equals(status)) return "ACTIVE";
    if ("停用".equals(status)) return "INACTIVE";
    return status;
  }
}