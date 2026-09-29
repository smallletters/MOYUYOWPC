package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.logistics.ShippingStrategyCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyUpdateRequest;
import com.moyuyo.dao.admin.entity.ShippingStrategyEntity;

import java.util.Map;

/**
 * 管理后台 - 发货策略 CRUD 服务。
 * <p>
 * 与 {@link AdminShippingZoneService} 同构：
 *  1) 收拢字段兼容（strategyName/name / shippingMethod/method / feeRule/ruleDesc）归一化；
 *  2) zoneId 校验 + 默认值兜底（必须关联到 mo_shipping_zone 已存在的 id）；
 *  3) zoneNameMap 一次性回填，省去前端二次拉 /shipping-zones。
 */
public interface AdminShippingStrategyService {

  /**
   * 列表（带 zoneNameMap）。
   * @param statusFilter 状态过滤（中文 ACTIVE/启用 / 英文 INACTIVE/停用）；null/空表示全部
   */
  java.util.List<ShippingStrategyEntity> listAll(String statusFilter);

  /**
   * 列出全部 zone（id → name 映射）。
   * service 内部使用，避免 service 调用方各自构造 where；
   * 也可以单独提供给 controller 暴露 zoneName。
   */
  Map<Long, String> listZoneNameMap();

  /** 创建策略 */
  ShippingStrategyEntity create(ShippingStrategyCreateRequest req);

  /** partial update */
  ShippingStrategyEntity update(Long id, ShippingStrategyUpdateRequest req);

  /** 删除策略 */
  void delete(Long id);
}