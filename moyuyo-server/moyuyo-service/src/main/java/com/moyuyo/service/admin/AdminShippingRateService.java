package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.logistics.ShippingRateCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingRateUpdateRequest;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;

import java.util.List;

/**
 * 管理后台 - 运费规则 CRUD。
 * <p>
 * 与 {@code AdminShippingStrategyService} 同构：
 *  1) 字段归一化（中文 ACTIVE/启用 → ACTIVE）；
 *  2) zoneId / methodId 校验（防错配）；
 *  3) (zoneId, methodId, ACTIVE) 唯一性检查：重复创建抛 409。
 */
public interface AdminShippingRateService {

    /** 列表（可按 zoneId 过滤）。 */
    List<ShippingRateEntity> listAll(Long zoneId, String statusFilter);

    /** 创建规则；同 (zoneId, methodId, ACTIVE) 重复时抛 409。 */
    ShippingRateEntity create(ShippingRateCreateRequest req);

    /** partial update。 */
    ShippingRateEntity update(Long id, ShippingRateUpdateRequest req);

    /** 幂等删除（不存在也成功）。 */
    void delete(Long id);
}
