package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferCreateRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferVO;
import com.moyuyo.common.enums.InventoryTransferStatusEnum;
import com.moyuyo.dao.admin.entity.InventoryTransferEntity;
import com.moyuyo.dao.admin.entity.WarehouseEntity;
import com.moyuyo.dao.admin.mapper.InventoryTransferMapper;
import com.moyuyo.dao.admin.mapper.WarehouseMapper;
import com.moyuyo.service.admin.AdminInventoryTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存调拨服务实现
 * 状态值统一通过 InventoryTransferStatusEnum 管理:
 *   数据库使用大写 name(),前端通过 getFrontendValue() 获取小写值
 */
@Service
@RequiredArgsConstructor
public class AdminInventoryTransferServiceImpl implements AdminInventoryTransferService {

  private final InventoryTransferMapper inventoryTransferMapper;
  private final WarehouseMapper warehouseMapper;

  @Override
  public PageResponse<InventoryTransferVO> listAll(int page, int size, String status) {
    LambdaQueryWrapper<InventoryTransferEntity> wrapper = new LambdaQueryWrapper<>();
    // 前端值 → 数据库值,通过枚举统一转换
    InventoryTransferStatusEnum statusEnum = InventoryTransferStatusEnum.fromFrontendValue(status);
    if (statusEnum != null) {
      wrapper.eq(InventoryTransferEntity::getStatus, statusEnum.getDbValue());
    }
    wrapper.orderByDesc(InventoryTransferEntity::getCreateTime);

    Page<InventoryTransferEntity> pageObj = inventoryTransferMapper.selectPage(new Page<>(page, size), wrapper);

    // DB 实体 → 前端 VO
    List<InventoryTransferVO> records = pageObj.getRecords().stream()
        .map(this::toVO)
        .toList();

    PageResponse<InventoryTransferVO> result = new PageResponse<>();
    result.setRecords(records);
    result.setTotal(pageObj.getTotal());
    result.setPage((int) pageObj.getCurrent());
    result.setSize((int) pageObj.getSize());
    return result;
  }

  /**
   * DB 实体 → 前端 VO,状态值通过枚举统一映射
   */
  private InventoryTransferVO toVO(InventoryTransferEntity entity) {
    InventoryTransferVO vo = new InventoryTransferVO();
    vo.setId(entity.getId());
    vo.setSkuId(entity.getSkuId());
    vo.setProductId(entity.getSkuId()); // 前端用 productId,与 skuId 同值
    vo.setFromWarehouseId(entity.getFromWarehouseId());
    vo.setToWarehouseId(entity.getToWarehouseId());
    vo.setFromWarehouse(lookupWarehouseName(entity.getFromWarehouseId()));
    vo.setToWarehouse(lookupWarehouseName(entity.getToWarehouseId()));
    vo.setProductName("SKU-" + entity.getSkuId());
    vo.setQuantity(entity.getQuantity());
    vo.setReason(entity.getReason());
    // 状态值通过枚举统一映射,数据库值若异常则降级为 PENDING
    InventoryTransferStatusEnum statusEnum = InventoryTransferStatusEnum.fromDbValue(entity.getStatus());
    if (statusEnum == null) {
      statusEnum = InventoryTransferStatusEnum.PENDING;
    }
    vo.setStatus(statusEnum.getFrontendValue());
    vo.setDbStatus(entity.getStatus());
    vo.setOperatorId(entity.getOperatorId());
    vo.setApproverId(entity.getApproverId());
    vo.setCreateTime(entity.getCreateTime());
    vo.setCreatedAt(entity.getCreateTime());
    vo.setUpdateTime(entity.getUpdateTime());
    vo.setCompleteTime(entity.getCompleteTime());
    return vo;
  }

  /** 查询仓库名称,失败时降级为 "仓库-{id}" */
  private String lookupWarehouseName(Long warehouseId) {
    if (warehouseId == null) return "";
    try {
      WarehouseEntity w = warehouseMapper.selectById(warehouseId);
      return w != null && w.getName() != null ? w.getName() : "仓库-" + warehouseId;
    } catch (Exception ex) {
      return "仓库-" + warehouseId;
    }
  }

  @Override
  @Transactional
  public void create(InventoryTransferCreateRequest request) {
    InventoryTransferEntity entity = new InventoryTransferEntity();
    entity.setSkuId(request.getSkuId());

    // 调出仓库:优先 ID,其次按名称查找
    if (request.getFromWarehouseId() != null) {
      entity.setFromWarehouseId(request.getFromWarehouseId());
    } else if (request.getFromWarehouse() != null && !request.getFromWarehouse().isEmpty()) {
      Long wid = lookupWarehouseIdByName(request.getFromWarehouse());
      if (wid != null) entity.setFromWarehouseId(wid);
    }

    // 调入仓库:优先 ID,其次按名称查找
    if (request.getToWarehouseId() != null) {
      entity.setToWarehouseId(request.getToWarehouseId());
    } else if (request.getToWarehouse() != null && !request.getToWarehouse().isEmpty()) {
      Long wid = lookupWarehouseIdByName(request.getToWarehouse());
      if (wid != null) entity.setToWarehouseId(wid);
    }

    entity.setQuantity(request.getQuantity());
    entity.setOperatorId(request.getOperatorId());
    entity.setReason(request.getReason());
    // 新建调拨单初始状态固定为 PENDING
    entity.setStatus(InventoryTransferStatusEnum.PENDING.getDbValue());
    inventoryTransferMapper.insert(entity);
  }

  /** 按名称查找仓库ID,失败时返回 null */
  private Long lookupWarehouseIdByName(String name) {
    if (name == null || name.isEmpty()) return null;
    try {
      WarehouseEntity w = warehouseMapper.selectOne(
          new LambdaQueryWrapper<WarehouseEntity>().eq(WarehouseEntity::getName, name));
      return w != null ? w.getId() : null;
    } catch (Exception ex) {
      return null;
    }
  }

  @Override
  @Transactional
  public void approve(Long id) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(InventoryTransferStatusEnum.IN_TRANSIT.getDbValue());
      inventoryTransferMapper.updateById(entity);
    }
  }

  @Override
  @Transactional
  public void reject(Long id, String reason) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(InventoryTransferStatusEnum.REJECTED.getDbValue());
      if (reason != null) entity.setReason(reason);
      inventoryTransferMapper.updateById(entity);
    }
  }

  @Override
  @Transactional
  public void complete(Long id) {
    InventoryTransferEntity entity = inventoryTransferMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(InventoryTransferStatusEnum.COMPLETED.getDbValue());
      entity.setCompleteTime(LocalDateTime.now());
      inventoryTransferMapper.updateById(entity);
    }
  }
}
