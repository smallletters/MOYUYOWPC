package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingRateCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingRateUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.admin.AdminShippingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异
public class AdminShippingRateServiceImpl implements AdminShippingRateService {

    private final ShippingRateMapper shippingRateMapper;
    private final ShippingZoneMapper shippingZoneMapper;
    private final ShippingMethodMapper shippingMethodMapper;

    @Override
    public List<ShippingRateEntity> listAll(Long zoneId, String statusFilter) {
        LambdaQueryWrapper<ShippingRateEntity> wrapper =
                new LambdaQueryWrapper<ShippingRateEntity>().orderByAsc(ShippingRateEntity::getPriority);
        if (zoneId != null) {
            wrapper.eq(ShippingRateEntity::getZoneId, zoneId);
        }
        if (statusFilter != null && !statusFilter.isEmpty()) {
            wrapper.eq(ShippingRateEntity::getStatus, normalizeStatus(statusFilter));
        }
        return shippingRateMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ShippingRateEntity create(ShippingRateCreateRequest req) {
        // P2 修复：chargeType 必须在 1-3 范围内（DTO 已有 @Min(1)，这里补 max 校验兜底）
        validateChargeType(req.getChargeType());

        // zoneId / methodId 必须指向已存在的记录
        requireZoneExists(req.getZoneId());
        requireMethodExists(req.getMethodId());

        // P1 修复：应用层软检查 + DB 层 unique key 双保险。
        // 仅当新建为 ACTIVE 时检查冲突；新建 INACTIVE 历史记录允许多条共存。
        String status = normalizeStatus(req.getStatus());
        if ("ACTIVE".equals(status)) {
            Long sameActive = shippingRateMapper.selectCount(
                    new LambdaQueryWrapper<ShippingRateEntity>()
                            .eq(ShippingRateEntity::getZoneId, req.getZoneId())
                            .eq(ShippingRateEntity::getMethodId, req.getMethodId())
                            .eq(ShippingRateEntity::getStatus, "ACTIVE"));
            if (sameActive != null && sameActive > 0) {
                throw new BusinessException(409, "该 (zoneId, methodId) 下已存在 ACTIVE 运费规则，请先停用旧规则");
            }
        }

        ShippingRateEntity entity = new ShippingRateEntity();
        entity.setZoneId(req.getZoneId());
        entity.setMethodId(req.getMethodId());
        entity.setChargeType(req.getChargeType());
        entity.setFirstCharge(req.getFirstCharge());
        entity.setFirstUnit(req.getFirstUnit() != null ? req.getFirstUnit() : 1);
        entity.setContinueCharge(req.getContinueCharge());
        entity.setContinueUnit(req.getContinueUnit() != null ? req.getContinueUnit() : 1);
        entity.setFreeThreshold(req.getFreeThreshold());
        entity.setCurrency(req.getCurrency() != null && !req.getCurrency().isBlank() ? req.getCurrency() : "USD");
        entity.setPriority(req.getPriority() != null ? req.getPriority() : 10);
        entity.setStatus(status);
        entity.setRemark(req.getRemark());

        try {
            shippingRateMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            // uk_zone_method_active_flag 命中：同 (zoneId, methodId, ACTIVE) 已存在
            // 触发场景：两个并发请求都通过了 selectCount(ACTIVE)==0 后同时 insert
            throw new BusinessException(409, "该 (zoneId, methodId) 下已存在 ACTIVE 运费规则，请先停用旧规则");
        }
        log.info("ShippingRate created: id={}, zoneId={}, methodId={}, chargeType={}, status={}",
                entity.getId(), entity.getZoneId(), entity.getMethodId(), entity.getChargeType(), entity.getStatus());
        return entity;
    }

    @Override
    @Transactional
    public ShippingRateEntity update(Long id, ShippingRateUpdateRequest req) {
        ShippingRateEntity entity = shippingRateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "运费规则不存在");
        }
        // P2 修复：partial update 时也校验 chargeType 范围（DTO 用了 @Min(1) 但未约束 max，service 兜底）
        if (req.getChargeType() != null) {
            validateChargeType(req.getChargeType());
            entity.setChargeType(req.getChargeType());
        }
        if (req.getFirstCharge() != null) entity.setFirstCharge(req.getFirstCharge());
        if (req.getFirstUnit() != null) entity.setFirstUnit(req.getFirstUnit());
        if (req.getContinueCharge() != null) entity.setContinueCharge(req.getContinueCharge());
        if (req.getContinueUnit() != null) entity.setContinueUnit(req.getContinueUnit());
        // freeThreshold 优先级：freeThresholdClear=true → 显式设 null（清空）；否则按 freeThreshold 值更新
        if (Boolean.TRUE.equals(req.getFreeThresholdClear())) {
            entity.setFreeThreshold(null);
        } else if (req.getFreeThreshold() != null) {
            entity.setFreeThreshold(req.getFreeThreshold());
        }
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) entity.setCurrency(req.getCurrency());
        if (req.getPriority() != null) entity.setPriority(req.getPriority());
        if (req.getRemark() != null) entity.setRemark(req.getRemark());

        // P1 修复：update 时如果把 status 改为 ACTIVE，需要先检查同 (zone, method) 是否有别的 ACTIVE
        // 用 id != this.id 排除自己（避免自我冲突）；再交 unique key 双保险
        String newStatus = req.getStatus() != null ? normalizeStatus(req.getStatus()) : null;
        if (newStatus != null && "ACTIVE".equals(newStatus) && !"ACTIVE".equals(entity.getStatus())) {
            // 状态从 INACTIVE → ACTIVE 的场景
            Long sameActive = shippingRateMapper.selectCount(
                    new LambdaQueryWrapper<ShippingRateEntity>()
                            .eq(ShippingRateEntity::getZoneId, entity.getZoneId())
                            .eq(ShippingRateEntity::getMethodId, entity.getMethodId())
                            .eq(ShippingRateEntity::getStatus, "ACTIVE")
                            .ne(ShippingRateEntity::getId, id));  // 排除自己
            if (sameActive != null && sameActive > 0) {
                throw new BusinessException(409,
                        "该 (zoneId, methodId) 下已存在 ACTIVE 运费规则 id=" + sameActive
                                + "，请先停用其他规则再启用此条");
            }
        }
        if (newStatus != null) entity.setStatus(newStatus);

        // P1 修复：update 也可能触发 unique key 冲突（如把 INACTIVE 改成 ACTIVE 但库里有别的 ACTIVE）
        // MyBatis-Plus updateById 抛 DuplicateKeyException 时会冒泡为 500，捕获后转 409
        try {
            shippingRateMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "该 (zoneId, methodId) 下已存在 ACTIVE 运费规则，无法启用此条");
        }
        log.info("ShippingRate updated: id={}, freeThresholdClear={}", entity.getId(), req.getFreeThresholdClear());
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        int rows = shippingRateMapper.deleteById(id);
        if (rows == 0) {
            log.warn("ShippingRate delete: id={} not found (idempotent)", id);
        }
    }

    // ========== 内部校验 ==========

    /**
     * P2 修复：chargeType 必须在 1-3 范围内。
     * 1=按件 / 2=按重 / 3=按金额；超出范围拒绝（防脏数据落库）。
     */
    private static void validateChargeType(Integer chargeType) {
        if (chargeType == null || chargeType < 1 || chargeType > 3) {
            throw new BusinessException(400, "chargeType 必须在 1-3 之间（1=按件 2=按重 3=按金额）");
        }
    }

    private void requireZoneExists(Long zoneId) {
        if (zoneId == null) return;
        Long count = shippingZoneMapper.selectCount(
                new LambdaQueryWrapper<ShippingZoneEntity>().eq(ShippingZoneEntity::getId, zoneId));
        if (count == null || count == 0) {
            throw new BusinessException(400, "发货区域不存在：zoneId=" + zoneId);
        }
    }

    private void requireMethodExists(Long methodId) {
        if (methodId == null) return;
        Long count = shippingMethodMapper.selectCount(
                new LambdaQueryWrapper<ShippingMethodEntity>().eq(ShippingMethodEntity::getId, methodId));
        if (count == null || count == 0) {
            throw new BusinessException(400, "配送方式不存在：methodId=" + methodId);
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null) return "ACTIVE";
        if ("启用".equals(status)) return "ACTIVE";
        if ("停用".equals(status)) return "INACTIVE";
        return status;
    }
}
