package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.logistics.ShippingZoneCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneUpdateRequest;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;

import java.util.List;

/**
 * 管理后台 - 发货区域 CRUD 服务。
 * <p>
 * 收拢校验逻辑（countryCodes 归一化、引用计数检查、UNIQUE 兜底），
 * 让 Controller 只做 HTTP 适配；同时调用 ShippingZoneService.evictCache() 让 APP 立刻看到改动。
 */
public interface AdminShippingZoneService {

  /** 列表（按 sortOrder 升序） */
  List<ShippingZoneEntity> listAll();

  /**
   * 创建区域。
   * @throws com.moyuyo.common.exception.BusinessException 业务校验失败（name 重复、countryCodes 为空等）
   */
  ShippingZoneEntity create(ShippingZoneCreateRequest req);

  /**
   * 更新区域。所有字段以 partial-update 语义生效；name 改名受 DB UNIQUE 约束保护。
   * @throws com.moyuyo.common.exception.BusinessException 业务校验失败
   */
  ShippingZoneEntity update(Long id, ShippingZoneUpdateRequest req);

  /**
   * 删除区域。删除前检查 mo_shipping_strategy 引用计数。
   * @throws com.moyuyo.common.exception.BusinessException 存在引用时抛 409
   */
  void delete(Long id);
}