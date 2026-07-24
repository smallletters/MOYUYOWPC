package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.InventoryTransferEntity;
import com.moyuyo.dao.admin.mapper.InventoryTransferMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.service.admin.AdminInventoryTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库存调拨服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminInventoryTransferServiceImpl implements AdminInventoryTransferService {

  private final InventoryTransferMapper inventoryTransferMapper;

  @Override
  public Map<String, Object> listAll(int page, int size, String status) {
    LambdaQueryWrapper<InventoryTransferEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(InventoryTransferEntity::getStatus, status);
    }
    wrapper.orderByDesc(InventoryTransferEntity::getCreateTime);

    Page<InventoryTransferEntity> pageObj = inventoryTransferMapper.selectPage(new Page<>(page, size), wrapper);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", pageObj.getRecords());
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    InventoryTransferEntity entity = new InventoryTransferEntity();
    if (data.get("transferNo") != null) entity.setTransferNo((String) data.get("transferNo"));
    if (data.get("fromWarehouseId") != null) entity.setFromWarehouseId(Long.valueOf(data.get("fromWarehouseId").toString()));
    if (data.get("toWarehouseId") != null) entity.setToWarehouseId(Long.valueOf(data.get("toWarehouseId").toString()));
    if (data.get("productId") != null) entity.setProductId(Long.valueOf(data.get("productId").toString()));
    if (data.get("skuId") != null) entity.setSkuId(Long.valueOf(data.get("skuId").toString()));
    if (data.get("quantity") != null) entity.setQuantity(Integer.valueOf(data.get("quantity").toString()));
    if (data.get("operatorId") != null) entity.setOperatorId(Long.valueOf(data.get("operatorId").toString()));
    entity.setStatus("PENDING");
    inventoryTransferMapper.insert(entity);
  }

  @Override
  @Transactional
  public void approve(Long id) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("PROCESSING");
      inventoryTransferMapper.updateById(entity);
    }
  }

  @Override
  @Transactional
  public void reject(Long id, String reason) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(OrderStatusEnum.CANCELLED.name());
      inventoryTransferMapper.updateById(entity);
    }
  }

  @Override
  @Transactional
  public void complete(Long id) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(OrderStatusEnum.COMPLETED.name());
      inventoryTransferMapper.updateById(entity);
    }
  }
}
