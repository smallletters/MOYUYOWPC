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
    carrierMapper.insert(e);
    log.info("Carrier created: id={}, name={}", e.getId(), e.getName());
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