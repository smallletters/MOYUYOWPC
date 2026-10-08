package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ShippingRateCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingRateUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminShippingRateServiceImpl 单元测试。
 * 重点：
 *  1) create：zoneId/methodId 校验 → 400；同 (zoneId, methodId, ACTIVE) 重复 → 409
 *  2) update：id 不存在 → 404；partial update
 *  3) delete：幂等
 *  4) status 归一化
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AdminShippingRateServiceImplTest {

    @Mock private ShippingRateMapper shippingRateMapper;
    @Mock private ShippingZoneMapper shippingZoneMapper;
    @Mock private ShippingMethodMapper shippingMethodMapper;

    @InjectMocks private AdminShippingRateServiceImpl service;

    private ShippingRateCreateRequest baseReq() {
        ShippingRateCreateRequest r = new ShippingRateCreateRequest();
        r.setZoneId(1L);
        r.setMethodId(100L);
        r.setChargeType(1);
        r.setFirstCharge(new BigDecimal("5.99"));
        r.setFirstUnit(1);
        r.setContinueCharge(new BigDecimal("2.50"));
        r.setContinueUnit(1);
        r.setFreeThreshold(new BigDecimal("59.00"));
        r.setCurrency("USD");
        r.setPriority(10);
        r.setStatus("ACTIVE");
        return r;
    }

    // ==================== create ====================

    @Test
    void create_zoneNotFound_throws400() {
        ShippingRateCreateRequest req = baseReq();
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(400, ex.getCode());
        verify(shippingRateMapper, never()).insert(any(ShippingRateEntity.class));
    }

    @Test
    void create_methodNotFound_throws400() {
        ShippingRateCreateRequest req = baseReq();
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void create_duplicateActive_throws409() {
        ShippingRateCreateRequest req = baseReq();
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // 显式指定 (ShippingRateEntity) 消除 insert(T) vs insert(Collection<T>) 二义性
        doThrow(new DuplicateKeyException("uk_zone_method_active"))
                .when(shippingRateMapper).insert(any(ShippingRateEntity.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    void create_happyPath_defaultValues() {
        ShippingRateCreateRequest req = baseReq();
        req.setFirstUnit(null);
        req.setContinueUnit(null);
        req.setCurrency(null);
        req.setPriority(null);
        req.setStatus(null);
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        ShippingRateEntity out = service.create(req);

        assertEquals(1, out.getFirstUnit(), "firstUnit null → 1");
        assertEquals(1, out.getContinueUnit(), "continueUnit null → 1");
        assertEquals("USD", out.getCurrency(), "currency null → USD");
        assertEquals(10, out.getPriority(), "priority null → 10");
        assertEquals("ACTIVE", out.getStatus(), "status null → ACTIVE");
    }

    // ==================== update ====================

    @Test
    void update_idNotFound_throws404() {
        when(shippingRateMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(999L, new ShippingRateUpdateRequest()));
        assertEquals(404, ex.getCode());
    }

    @Test
    void update_partialUpdate() {
        ShippingRateEntity existing = new ShippingRateEntity();
        existing.setId(1L);
        existing.setFirstCharge(new BigDecimal("5.99"));
        existing.setStatus("ACTIVE");
        when(shippingRateMapper.selectById(1L)).thenReturn(existing);

        ShippingRateUpdateRequest req = new ShippingRateUpdateRequest();
        req.setFirstCharge(new BigDecimal("9.99"));
        req.setStatus("INACTIVE");

        ShippingRateEntity out = service.update(1L, req);
        assertEquals(0, new BigDecimal("9.99").compareTo(out.getFirstCharge()));
        assertEquals("INACTIVE", out.getStatus());
        verify(shippingRateMapper, times(1)).updateById(any(ShippingRateEntity.class));
    }

    // ==================== P1 修复：freeThresholdClear 显式清空 ====================

    @Test
    void update_freeThresholdClear_true_setsNull() {
        // 已有 freeThreshold=59.00，运营点"清除免邮门槛" → service 必须把 freeThreshold 设为 null
        ShippingRateEntity existing = new ShippingRateEntity();
        existing.setId(1L);
        existing.setFreeThreshold(new BigDecimal("59.00"));
        existing.setStatus("ACTIVE");
        when(shippingRateMapper.selectById(1L)).thenReturn(existing);

        ShippingRateUpdateRequest req = new ShippingRateUpdateRequest();
        req.setFreeThresholdClear(Boolean.TRUE);
        // 显式传 freeThreshold=null（前端清空按钮的常规行为）
        req.setFreeThreshold(null);

        ShippingRateEntity out = service.update(1L, req);
        assertNull(out.getFreeThreshold(), "freeThresholdClear=true 应清空 freeThreshold");
    }

    @Test
    void update_freeThresholdClear_false_orNull_keepsBehavior() {
        // 默认场景：freeThresholdClear 没传（或 false），按 freeThreshold 字段更新
        ShippingRateEntity existing = new ShippingRateEntity();
        existing.setId(1L);
        existing.setFreeThreshold(new BigDecimal("30.00"));
        existing.setStatus("ACTIVE");
        when(shippingRateMapper.selectById(1L)).thenReturn(existing);

        ShippingRateUpdateRequest req = new ShippingRateUpdateRequest();
        req.setFreeThreshold(new BigDecimal("99.00"));
        // freeThresholdClear 不设置（默认 null）

        ShippingRateEntity out = service.update(1L, req);
        assertEquals(0, new BigDecimal("99.00").compareTo(out.getFreeThreshold()),
                "freeThresholdClear 没传时按 freeThreshold 值更新");
    }

    // ==================== P2: chargeType 范围校验 ====================

    @Test
    void create_chargeTypeTooSmall_throws400() {
        ShippingRateCreateRequest req = baseReq();
        req.setChargeType(0);
        // chargeType 校验在 zone/method 校验之前，不会触发到 mapper
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("chargeType"));
    }

    @Test
    void create_chargeTypeTooLarge_throws400() {
        ShippingRateCreateRequest req = baseReq();
        req.setChargeType(4);
        // 同上：chargeType 校验先于 zone/method
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void update_chargeTypeTooLarge_throws400() {
        ShippingRateEntity existing = new ShippingRateEntity();
        existing.setId(1L);
        existing.setStatus("ACTIVE");
        when(shippingRateMapper.selectById(1L)).thenReturn(existing);

        ShippingRateUpdateRequest req = new ShippingRateUpdateRequest();
        req.setChargeType(99);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("chargeType"));
    }

    // ==================== P1: 应用层软检查 + DB 兜底 ====================

    @Test
    void create_activeConflict_throws409_softCheck() {
        // 应用层 selectCount(ACTIVE) > 0 → 直接抛 409（不进入 insert）
        ShippingRateCreateRequest req = baseReq();
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingRateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(409, ex.getCode());
        verify(shippingRateMapper, never()).insert(any(ShippingRateEntity.class));
    }

    @Test
    void create_activeConflict_throws409_concurrentInsert() {
        // 并发场景：两个请求都通过了 selectCount(ACTIVE)==0，同时 insert
        // 后到的被 unique key 拦下 → 抛 409
        ShippingRateCreateRequest req = baseReq();
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingRateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_zone_method_active_flag"))
                .when(shippingRateMapper).insert(any(ShippingRateEntity.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(409, ex.getCode());
    }

    @Test
    void create_inactiveAllowed_evenIfActiveExists() {
        // P1 修复：新建 INACTIVE 记录允许存在多条历史（不再受唯一索引约束）
        ShippingRateCreateRequest req = baseReq();
        req.setStatus("INACTIVE");
        when(shippingZoneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(shippingMethodMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // 注意：INACTIVE 时不调用 selectCount(ACTIVE) 检查
        when(shippingRateMapper.insert(any(ShippingRateEntity.class))).thenReturn(1);

        ShippingRateEntity out = service.create(req);
        assertEquals("INACTIVE", out.getStatus());
        verify(shippingRateMapper, never()).selectCount(any(LambdaQueryWrapper.class));
    }

    // ==================== delete ====================

    @Test
    void delete_existing_returnsSuccess() {
        when(shippingRateMapper.deleteById(1L)).thenReturn(1);
        service.delete(1L);
        verify(shippingRateMapper, times(1)).deleteById(1L);
    }

    @Test
    void delete_notFound_isIdempotent() {
        when(shippingRateMapper.deleteById(999L)).thenReturn(0);
        // 不抛异常
        assertDoesNotThrow(() -> service.delete(999L));
    }

    // ==================== listAll ====================

    @Test
    void listAll_filterByZoneAndStatus() {
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of());
        service.listAll(1L, "启用");
        // 中文 "启用" → ACTIVE 归一化
        verify(shippingRateMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }
}
