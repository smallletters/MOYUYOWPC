package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.logistics.CarrierCreateRequest;
import com.moyuyo.common.dto.admin.logistics.CarrierUpdateRequest;
import com.moyuyo.dao.admin.entity.CarrierEntity;

import java.util.List;

/**
 * 管理后台 - 承运商 CRUD 服务。
 * <p>与 AdminShippingZoneService / AdminShippingStrategyService 同构：
 *  1) 收拢字段类型转换（BigDecimal 字符串 → BigDecimal）与状态归一化；
 *  2) partial update 语义。
 */
public interface AdminCarrierService {

  /** 列表（带 status 过滤，null/空 = 全部） */
  List<CarrierEntity> listAll(String statusFilter);

  /** 创建承运商 */
  CarrierEntity create(CarrierCreateRequest req);

  /** partial update */
  CarrierEntity update(Long id, CarrierUpdateRequest req);

  /** 删除承运商 */
  void delete(Long id);
}