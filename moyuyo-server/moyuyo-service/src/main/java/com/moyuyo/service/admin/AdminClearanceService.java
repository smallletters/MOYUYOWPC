package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.logistics.ClearanceCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ClearanceUpdateRequest;
import com.moyuyo.dao.admin.entity.ClearanceEntity;

import java.util.List;

/**
 * 管理后台 - 清关/海关 CRUD 服务。
 * <p>与 AdminShippingZoneService / AdminShippingStrategyService 同构：
 *  1) 收拢字段类型转换与状态归一化；
 *  2) partial update 语义；
 *  3) listAll(statusFilter, customsOnly) 支持两种视图（清关 / 海关库）。
 */
public interface AdminClearanceService {

  /**
   * 列表（带 status 过滤）。
   * @param customsOnly true 表示只查海关库视图（hsCode 非空）
   */
  List<ClearanceEntity> listAll(String statusFilter, boolean customsOnly);

  /** 创建清关/海关记录 */
  ClearanceEntity create(ClearanceCreateRequest req);

  /** partial update */
  ClearanceEntity update(Long id, ClearanceUpdateRequest req);

  /** 删除记录 */
  void delete(Long id);
}