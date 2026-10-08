package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.shipping.ShippingMethodVO;
import com.moyuyo.common.dto.shipping.ShippingQuoteRequest;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ShippingRateServiceImpl 单元测试。
 *
 * 重点覆盖（行业通用四要素）：
 *  1) country → zone 命中：返回该 zone 下所有 method
 *  2) country 未命中 zone：返回空列表
 *  3) 按件计费：首件 + 续件 × (CEIL(qty) - 1)，向上取整
 *  4) 按重计费：首费 + 续费 × (CEIL(weight) - 1)
 *  5) 免邮门槛：subtotal >= freeThreshold → freight=0 + free=true + freeShortBy=0
 *  6) 未达免邮门槛：freeShortBy = freeThreshold - subtotal
 *  7) calcFreight 返回 null：method 不存在/zone 不存在
 *  8) 同 method 多条 ACTIVE rule：取 priority 最小
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ShippingRateServiceImplTest {

    @Mock private ShippingZoneMapper shippingZoneMapper;
    @Mock private ShippingMethodMapper shippingMethodMapper;
    @Mock private ShippingRateMapper shippingRateMapper;

    @InjectMocks private ShippingRateServiceImpl service;

    private ShippingZoneEntity zoneUs;

    @BeforeEach
    void setUp() {
        zoneUs = new ShippingZoneEntity();
        zoneUs.setId(1L);
        zoneUs.setName("北美");
        zoneUs.setCountryCodes("US,CA");
        zoneUs.setStatus("ACTIVE");
    }

    private ShippingMethodEntity method(String code, String nameEn) {
        ShippingMethodEntity m = new ShippingMethodEntity();
        m.setId(switch (code) {
            case "standard" -> 100L;
            case "express" -> 101L;
            default -> 102L;
        });
        m.setCode(code);
        m.setNameEn(nameEn);
        m.setNameZh(nameEn);
        m.setEtaMinDays(3);
        m.setEtaMaxDays(7);
        m.setStatus("ACTIVE");
        m.setSortOrder(10);
        return m;
    }

    private ShippingRateEntity rate(Long zoneId, Long methodId, Integer chargeType,
                                    String first, String firstUnit,
                                    String cont, String contUnit,
                                    String threshold, Integer priority) {
        ShippingRateEntity r = new ShippingRateEntity();
        r.setId(System.nanoTime());
        r.setZoneId(zoneId);
        r.setMethodId(methodId);
        r.setChargeType(chargeType);
        r.setFirstCharge(new BigDecimal(first));
        r.setFirstUnit(Integer.valueOf(firstUnit));
        r.setContinueCharge(new BigDecimal(cont));
        r.setContinueUnit(Integer.valueOf(contUnit));
        r.setFreeThreshold(threshold == null ? null : new BigDecimal(threshold));
        r.setCurrency("USD");
        r.setPriority(priority);
        r.setStatus("ACTIVE");
        return r;
    }

    private ShippingQuoteRequest.Item item(int qty, Integer grams) {
        ShippingQuoteRequest.Item i = new ShippingQuoteRequest.Item();
        i.setProductId(1L);
        i.setQuantity(qty);
        i.setWeightGrams(grams);
        return i;
    }

    private ShippingQuoteRequest req(String country, BigDecimal subtotal,
                                     ShippingQuoteRequest.Item... items) {
        ShippingQuoteRequest r = new ShippingQuoteRequest();
        r.setCountry(country);
        r.setSubtotal(subtotal);
        r.setItems(List.of(items));
        return r;
    }

    // ==================== zone 匹配 ====================

    @Test
    void quote_countryNotInAnyZone_returnsEmpty() {
        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        List<ShippingMethodVO> vo = service.quote(req("XX", BigDecimal.ZERO, item(1, 500)));
        assertTrue(vo.isEmpty(), "country=XX 不在任何 zone 时应返回空");
    }

    @Test
    void quote_countryUs_returnsStandardAndExpress() {
        ShippingMethodEntity std = method("standard", "Standard");
        ShippingMethodEntity exp = method("express", "Express");
        ShippingRateEntity stdRate = rate(1L, std.getId(), 1, "5.99", "1", "2.50", "1", "59.00", 10);
        ShippingRateEntity expRate = rate(1L, exp.getId(), 1, "12.99", "1", "3.50", "1", null,    20);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stdRate, expRate));
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(std, exp));

        List<ShippingMethodVO> vos = service.quote(req("us", new BigDecimal("30.00"), item(2, 500)));
        assertEquals(2, vos.size());
        // standard: 2件 → 5.99 + 2.50*(2-1) = 8.49; free=false; freeShortBy=59-30=29.00
        ShippingMethodVO stdVo = vos.stream().filter(v -> "standard".equals(v.getCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("8.49"), stdVo.getFreight());
        assertFalse(stdVo.getFree());
        assertEquals(new BigDecimal("29.00"), stdVo.getFreeShortBy());
        // express: 12.99 + 3.50*(2-1) = 16.49; 无免邮门槛
        ShippingMethodVO expVo = vos.stream().filter(v -> "express".equals(v.getCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("16.49"), expVo.getFreight());
        assertNull(expVo.getFreeThreshold());
    }

    // ==================== 免邮门槛 ====================

    @Test
    void quote_subtotalMeetsFreeThreshold_returnsZeroFreight() {
        ShippingMethodEntity std = method("standard", "Standard");
        ShippingRateEntity stdRate = rate(1L, std.getId(), 1, "5.99", "1", "2.50", "1", "59.00", 10);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stdRate));
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(std));

        // subtotal=60 超过阈值 59
        List<ShippingMethodVO> vos = service.quote(req("US", new BigDecimal("60.00"), item(1, 500)));
        assertEquals(1, vos.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(vos.get(0).getFreight()));
        assertTrue(vos.get(0).getFree());
        assertEquals(0, BigDecimal.ZERO.compareTo(vos.get(0).getFreeShortBy()));
    }

    // ==================== 按重计费：向上取整 ====================

    @Test
    void quote_chargeByWeight_ceilUp() {
        ShippingMethodEntity std = method("standard", "Standard");
        // 按重：首重 1000g 收 5.99；续重 500g 收 2.50
        ShippingRateEntity stdRate = rate(1L, std.getId(), 2, "5.99", "1", "2.50", "500", null, 10);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stdRate));
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(std));

        // 单品 1200g × 2件 = 2400g；units = CEIL(2400/500) = 5；
        // freight = 5.99 + 2.50*(5-1) = 15.99
        List<ShippingMethodVO> vos = service.quote(req("US", null, item(2, 1200)));
        assertEquals(new BigDecimal("15.99"), vos.get(0).getFreight());
    }

    // ==================== 同 method 多条 ACTIVE 取 priority 最小 ====================

    @Test
    void quote_sameMethodMultipleRates_pickSmallestPriority() {
        ShippingMethodEntity std = method("standard", "Standard");
        ShippingRateEntity lowPri  = rate(1L, std.getId(), 1, "5.99", "1", "2.50", "1", null, 5);
        ShippingRateEntity highPri = rate(1L, std.getId(), 1, "9.99", "1", "1.00", "1", null, 99);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lowPri, highPri));
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(std));

        List<ShippingMethodVO> vos = service.quote(req("US", BigDecimal.ZERO, item(1, 500)));
        assertEquals(1, vos.size(), "同 method 两条 ACTIVE rule 应合并为一条");
        assertEquals(new BigDecimal("5.99"), vos.get(0).getFreight(),
                "priority 小的应胜出");
    }

    // ==================== calcFreight ====================

    @Test
    void calcFreight_zoneNotFound_returnsNull() {
        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        assertNull(service.calcFreight("ZZ", "standard", List.of(item(1, 500)), BigDecimal.ZERO));
    }

    @Test
    void calcFreight_methodNotFound_returnsNull() {
        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingMethodMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.calcFreight("US", "nonexistent", List.of(item(1, 500)), BigDecimal.ZERO));
    }

    @Test
    void calcFreight_happyPath() {
        ShippingMethodEntity std = method("standard", "Standard");
        ShippingRateEntity stdRate = rate(1L, std.getId(), 1, "5.99", "1", "2.50", "1", "59.00", 10);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingMethodMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(std);
        when(shippingRateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stdRate);

        // 1件：5.99；未达免邮门槛
        BigDecimal f = service.calcFreight("US", "standard", List.of(item(1, 500)), new BigDecimal("10"));
        assertEquals(new BigDecimal("5.99"), f);

        // 满减门槛：subtotal=60 → freight=0
        BigDecimal f2 = service.calcFreight("US", "standard", List.of(item(1, 500)), new BigDecimal("60"));
        assertEquals(0, f2.compareTo(BigDecimal.ZERO));
    }

    // ==================== 防御：quantity 为 0 或 null ====================

    @Test
    void quote_itemsWithZeroQuantity_returnsZeroFreight() {
        // P3 修复：quantity=0 时不应收首费，freight 必须为 0
        ShippingMethodEntity std = method("standard", "Standard");
        ShippingRateEntity stdRate = rate(1L, std.getId(), 1, "5.99", "1", "2.50", "1", null, 10);

        when(shippingZoneMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(zoneUs));
        when(shippingRateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stdRate));
        when(shippingMethodMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(std));

        // quantity=0 → units=0 → freight = 0（不再误收首费）
        List<ShippingMethodVO> vos = service.quote(req("US", BigDecimal.ZERO, item(0, 500)));
        assertEquals(0, vos.get(0).getFreight().compareTo(BigDecimal.ZERO),
                "P3 修复：qty=0 时运费必须为 0");
    }
}
