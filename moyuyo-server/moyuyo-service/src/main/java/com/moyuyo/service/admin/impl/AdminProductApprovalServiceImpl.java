package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.ProductApprovalEntity;
import com.moyuyo.dao.admin.mapper.ProductApprovalMapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.admin.AdminProductApprovalService;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品审核服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminProductApprovalServiceImpl implements AdminProductApprovalService {

  private final ProductApprovalMapper productApprovalMapper;
  private final ProductMapper productMapper;

  @Override
  public Map<String, Object> listAll(int page, int size, String status) {
    LambdaQueryWrapper<ProductApprovalEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(ProductApprovalEntity::getStatus, status);
    }
    wrapper.orderByDesc(ProductApprovalEntity::getCreateTime);

    Page<ProductApprovalEntity> pageObj = productApprovalMapper.selectPage(new Page<>(page, size), wrapper);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", pageObj.getRecords());
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    ProductApprovalEntity entity = productApprovalMapper.selectById(id);
    if (entity == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("productId", entity.getProductId());
    result.put("submitterId", entity.getSubmitterId());
    result.put("type", entity.getType());
    result.put("status", entity.getStatus());
    result.put("reason", entity.getReason());
    result.put("reviewerId", entity.getReviewerId());
    result.put("reviewTime", entity.getReviewTime());
    result.put("urgentFlag", entity.getUrgentFlag());
    result.put("createTime", entity.getCreateTime());
    result.put("updateTime", entity.getUpdateTime());
    return result;
  }

  @Override
  @Transactional
  public void approve(Long id, Long reviewerId) {
    ProductApprovalEntity entity = productApprovalMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(APPROVED.name());
      entity.setReviewerId(reviewerId);
      entity.setReviewTime(LocalDateTime.now());
      productApprovalMapper.updateById(entity);

      // 同步更新商品上架状态
      if (entity.getProductId() != null) {
        ProductEntity product = productMapper.selectById(entity.getProductId());
        if (product != null) {
          product.setOnSale(true);
          productMapper.updateById(product);
        }
      }
    }
  }

  @Override
  @Transactional
  public void reject(Long id, Long reviewerId, String reason) {
    ProductApprovalEntity entity = productApprovalMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(REJECTED.name());
      entity.setReviewerId(reviewerId);
      entity.setReason(reason);
      entity.setReviewTime(LocalDateTime.now());
      productApprovalMapper.updateById(entity);

      // 同步更新商品为下架状态
      if (entity.getProductId() != null) {
        ProductEntity product = productMapper.selectById(entity.getProductId());
        if (product != null) {
          product.setOnSale(false);
          productMapper.updateById(product);
        }
      }
    }
  }

  @Override
  @Transactional
  public void setUrgent(Long id) {
    ProductApprovalEntity entity = productApprovalMapper.selectById(id);
    if (entity != null) {
      entity.setUrgentFlag(1);
      productApprovalMapper.updateById(entity);
    }
  }
}
