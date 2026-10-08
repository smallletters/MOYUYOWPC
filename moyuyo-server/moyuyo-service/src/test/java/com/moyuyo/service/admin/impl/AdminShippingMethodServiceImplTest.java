package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminShippingMethodServiceImpl 单元测试。
 * 重点：
 *  1) create: code / 名称必填 + 默认值兜底
 *  2) update: partial update
 *  3) delete: ACTIVE rate 引用检查（INACTIVE 不影响）
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AdminShippingMethodServiceImplTest {

    @Mock private ShippingMethodMapper shippingMethodMapper;
    @Mock private ShippingRateMapper shippingRateMapper;

    @InjectMocks private AdminShippingMethodServiceImpl service;

    private ShippingMethodEntity baseEntity() {
        ShippingMethodEntity e = new ShippingMethodEntity();
        e.setCode("standard");
        e.setNameZh("标准配送");
        e.setNameEn("Standard Shipping");
        return e;
    }

    // ==================== create ====================

    @Test
    void create_blankCode_throws400() {
        // Bug AA：code 空字符串应直接 400 拒绝（防止空 code 污染字典）
        ShippingMethodEntity e = baseEntity();
        e.setCode("");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertEquals(400, ex.getCode());
        verify(shippingMethodMapper, never()).insert(any(ShippingMethodEntity.class));
    }

    @Test
    void create_nullCode_throws400() {
        ShippingMethodEntity e = baseEntity();
        e.setCode(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertEquals(400, ex.getCode());
    }

    @Test
    void create_bothNamesBlank_throws400() {
        ShippingMethodEntity e = new ShippingMethodEntity();
        e.setCode("standard");
        e.setNameZh("");
        e.setNameEn("");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertEquals(400, ex.getCode());
        // 错误消息应该提到"中文名"或"英文名"
        assertTrue(ex.getMessage().contains("中文名") || ex.getMessage().contains("英文名"));
    }

    @Test
    void create_onlyNameZh_ok() {
        // 只有中文名 → 允许（APP i18n fallback 用）
        ShippingMethodEntity e = new ShippingMethodEntity();
        e.setCode("standard");
        e.setNameZh("标准配送");
        e.setNameEn(null);
        when(shippingMethodMapper.insert(any(ShippingMethodEntity.class))).thenReturn(1);

        ShippingMethodEntity out = service.create(e);
        assertEquals("标准配送", out.getNameZh());
    }

    @Test
    void create_duplicateCode_throws409() {
        ShippingMethodEntity e = baseEntity();
        doThrow(new DuplicateKeyException("uk_method_code"))
                .when(shippingMethodMapper).insert(any(ShippingMethodEntity.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("standard"));
    }

    @Test
    void create_defaultValuesApplied() {
        ShippingMethodEntity e = new ShippingMethodEntity();
        e.setCode("overnight");
        e.setNameEn("Overnight");
        // status / sortOrder / eta 都没传 → 用默认
        when(shippingMethodMapper.insert(any(ShippingMethodEntity.class))).thenReturn(1);

        ShippingMethodEntity out = service.create(e);
        assertEquals("ACTIVE", out.getStatus());
        assertEquals(10, out.getSortOrder());
        assertEquals(3, out.getEtaMinDays());
        assertEquals(7, out.getEtaMaxDays());
    }

    // ==================== update ====================

    @Test
    void update_idNotFound_throws404() {
        when(shippingMethodMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(999L, new ShippingMethodEntity()));
        assertEquals(404, ex.getCode());
    }

    @Test
    void update_partialUpdate() {
        ShippingMethodEntity existing = new ShippingMethodEntity();
        existing.setId(1L);
        existing.setCode("standard");
        existing.setNameZh("标准配送");
        when(shippingMethodMapper.selectById(1L)).thenReturn(existing);

        ShippingMethodEntity patch = new ShippingMethodEntity();
        patch.setNameZh("标准配送（更新）");
        patch.setEtaMinDays(2);
        patch.setStatus("INACTIVE");

        ShippingMethodEntity out = service.update(1L, patch);
        assertEquals("标准配送（更新）", out.getNameZh());
        assertEquals(2, out.getEtaMinDays());
        assertEquals("INACTIVE", out.getStatus());
        assertEquals("standard", out.getCode(), "code 未传应保持原值");
    }

    // ==================== delete ====================

    @Test
    void delete_referencedByActiveRate_throws409() {
        when(shippingRateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertEquals(409, ex.getCode());
        // 错误消息要明确"ACTIVE"
        assertTrue(ex.getMessage().contains("ACTIVE"));
        verify(shippingMethodMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_onlyInactiveReferences_ok() {
        // Bug AC 修复：只有 INACTIVE 引用时应允许删除
        when(shippingRateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(shippingMethodMapper.deleteById(any(Long.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(1L));
        verify(shippingMethodMapper, times(1)).deleteById(any(Long.class));
    }

    @Test
    void delete_notFound_isIdempotent() {
        when(shippingRateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(shippingMethodMapper.deleteById(any(Long.class))).thenReturn(0);
        assertDoesNotThrow(() -> service.delete(999L));
    }

    // ==================== listAll ====================

    @Test
    void listAll_withStatusFilter() {
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of());
        service.listAll("启用");  // 中文 ACTIVE → 应归一为 ACTIVE
        verify(shippingMethodMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }
}
