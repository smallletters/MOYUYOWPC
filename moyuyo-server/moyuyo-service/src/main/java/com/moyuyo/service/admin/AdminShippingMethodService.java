package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.ShippingMethodEntity;

import java.util.List;

/**
 * 管理后台 - 配送方式字典 CRUD。
 * <p>
 * 与 {@code AdminShippingZoneService} 同构：
 *  1) 字段归一化（中文 ACTIVE/启用 → ACTIVE）；
 *  2) code 唯一性校验（防运营填重复编码）；
 *  3) 删除前检查是否被运费规则引用（防误删导致 APP 端 methodMap 缺失）。
 */
public interface AdminShippingMethodService {

    /** 列表（可按状态过滤） */
    List<ShippingMethodEntity> listAll(String statusFilter);

    /** 创建配送方式。code 重复时抛 409。 */
    ShippingMethodEntity create(ShippingMethodEntity entity);

    /** partial update */
    ShippingMethodEntity update(Long id, ShippingMethodEntity entity);

    /** 幂等删除（不存在也成功；被运费规则引用时抛 409） */
    void delete(Long id);
}
