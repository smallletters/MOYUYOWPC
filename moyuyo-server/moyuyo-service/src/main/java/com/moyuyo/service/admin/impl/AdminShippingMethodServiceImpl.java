package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import com.moyuyo.service.admin.AdminShippingMethodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminShippingMethodServiceImpl implements AdminShippingMethodService {

    private final ShippingMethodMapper shippingMethodMapper;
    private final ShippingRateMapper shippingRateMapper;

    @Override
    public List<ShippingMethodEntity> listAll(String statusFilter) {
        LambdaQueryWrapper<ShippingMethodEntity> wrapper =
                new LambdaQueryWrapper<ShippingMethodEntity>().orderByAsc(ShippingMethodEntity::getSortOrder);
        if (statusFilter != null && !statusFilter.isEmpty()) {
            wrapper.eq(ShippingMethodEntity::getStatus, normalizeStatus(statusFilter));
        }
        return shippingMethodMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ShippingMethodEntity create(ShippingMethodEntity entity) {
        // Bug AA 修复：code 是必填字段，不能为空（uk_method_code 允许多条空字符串共存会导致字典污染）
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            throw new BusinessException(400, "配送方式编码（code）不能为空");
        }
        // nameZh / nameEn 至少要有一个（APP 端 i18n fallback 依赖）
        if ((entity.getNameZh() == null || entity.getNameZh().isBlank())
                && (entity.getNameEn() == null || entity.getNameEn().isBlank())) {
            throw new BusinessException(400, "中文名和英文名至少填写一个");
        }
        // 默认值兜底
        if (entity.getStatus() == null || entity.getStatus().isBlank()) entity.setStatus("ACTIVE");
        if (entity.getSortOrder() == null) entity.setSortOrder(10);
        if (entity.getEtaMinDays() == null) entity.setEtaMinDays(3);
        if (entity.getEtaMaxDays() == null) entity.setEtaMaxDays(7);
        try {
            shippingMethodMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            // uk_method_code 命中：code 已存在
            throw new BusinessException(409, "配送方式编码已存在：" + entity.getCode());
        }
        log.info("ShippingMethod created: id={}, code={}", entity.getId(), entity.getCode());
        return entity;
    }

    @Override
    @Transactional
    public ShippingMethodEntity update(Long id, ShippingMethodEntity patch) {
        ShippingMethodEntity entity = shippingMethodMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "配送方式不存在");
        }
        if (patch.getCode() != null && !patch.getCode().isBlank()) entity.setCode(patch.getCode());
        if (patch.getNameZh() != null) entity.setNameZh(patch.getNameZh());
        if (patch.getNameEn() != null) entity.setNameEn(patch.getNameEn());
        if (patch.getEtaMinDays() != null) entity.setEtaMinDays(patch.getEtaMinDays());
        if (patch.getEtaMaxDays() != null) entity.setEtaMaxDays(patch.getEtaMaxDays());
        if (patch.getSortOrder() != null) entity.setSortOrder(patch.getSortOrder());
        if (patch.getStatus() != null) entity.setStatus(normalizeStatus(patch.getStatus()));
        if (patch.getRemark() != null) entity.setRemark(patch.getRemark());

        try {
            shippingMethodMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "配送方式编码已存在：" + entity.getCode());
        }
        log.info("ShippingMethod updated: id={}", entity.getId());
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Bug AC 修复：只检查 ACTIVE 状态的 rate 引用（INACTIVE 是历史记录，不影响 APP）
        Long activeReferenced = shippingRateMapper.selectCount(
                new LambdaQueryWrapper<ShippingRateEntity>()
                        .eq(ShippingRateEntity::getMethodId, id)
                        .eq(ShippingRateEntity::getStatus, "ACTIVE"));
        if (activeReferenced != null && activeReferenced > 0) {
            throw new BusinessException(409,
                    "该配送方式被 " + activeReferenced + " 条 ACTIVE 运费规则引用，无法删除。请先停用相关规则。");
        }
        int rows = shippingMethodMapper.deleteById(id);
        if (rows == 0) {
            log.warn("ShippingMethod delete: id={} not found (idempotent)", id);
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null) return "ACTIVE";
        if ("启用".equals(status)) return "ACTIVE";
        if ("停用".equals(status)) return "INACTIVE";
        return status;
    }
}
