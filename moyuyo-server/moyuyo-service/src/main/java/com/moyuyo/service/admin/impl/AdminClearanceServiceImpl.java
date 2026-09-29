package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ClearanceCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ClearanceUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ClearanceEntity;
import com.moyuyo.dao.admin.mapper.ClearanceMapper;
import com.moyuyo.service.admin.AdminClearanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminClearanceServiceImpl implements AdminClearanceService {

  private final ClearanceMapper clearanceMapper;

  @Override
  @SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
  public List<ClearanceEntity> listAll(String statusFilter, boolean customsOnly) {
    LambdaQueryWrapper<ClearanceEntity> wrapper =
        new LambdaQueryWrapper<ClearanceEntity>().orderByAsc(ClearanceEntity::getId);
    if (customsOnly) {
      // 海关库视图：仅 hsCode 非空的
      wrapper.isNotNull(ClearanceEntity::getHsCode).ne(ClearanceEntity::getHsCode, "");
    }
    if (statusFilter != null && !statusFilter.isEmpty()) {
      wrapper.eq(ClearanceEntity::getStatus, normalizeStatus(statusFilter));
    }
    return clearanceMapper.selectList(wrapper);
  }

  @Override
  @Transactional
  public ClearanceEntity create(ClearanceCreateRequest req) {
    ClearanceEntity e = new ClearanceEntity();
    e.setDeclarationNo(req.getDeclarationNo());
    e.setOrderNo(req.getOrderNo());
    e.setProductName(req.getProductName());
    e.setHsCode(req.getHsCode());
    e.setTaxRate(req.getTaxRate());
    e.setStatus(normalizeStatus(req.getStatus()));
    clearanceMapper.insert(e);
    log.info("Clearance created: id={}, declNo={}", e.getId(), e.getDeclarationNo());
    return e;
  }

  @Override
  @Transactional
  public ClearanceEntity update(Long id, ClearanceUpdateRequest req) {
    ClearanceEntity e = clearanceMapper.selectById(id);
    if (e == null) {
      throw new BusinessException(404, "清关记录不存在");
    }
    if (req.getDeclarationNo() != null) e.setDeclarationNo(req.getDeclarationNo());
    if (req.getOrderNo() != null) e.setOrderNo(req.getOrderNo());
    if (req.getProductName() != null) e.setProductName(req.getProductName());
    if (req.getHsCode() != null) e.setHsCode(req.getHsCode());
    if (req.getTaxRate() != null) e.setTaxRate(req.getTaxRate());
    if (req.getStatus() != null) e.setStatus(normalizeStatus(req.getStatus()));
    clearanceMapper.updateById(e);
    log.info("Clearance updated: id={}", e.getId());
    return e;
  }

  @Override
  @Transactional
  public void delete(Long id) {
    int rows = clearanceMapper.deleteById(id);
    if (rows == 0) {
      log.warn("Clearance delete: id={} not found (idempotent)", id);
    }
  }

  /**
   * 状态归一化：与 Carrier / Zone / Strategy 一致——本表 status 集合更大
   * （PENDING/INSPECTING/CLEARED/REJECTED），未识别值原样透传，由 DB 决定是否命中。
   */
  static String normalizeStatus(String status) {
    if (status == null) return "PENDING";
    if ("启用".equals(status)) return "ACTIVE";
    if ("停用".equals(status)) return "INACTIVE";
    return status;
  }
}