package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.shipping.ShippingMethodVO;
import com.moyuyo.common.dto.shipping.ShippingQuoteRequest;
import com.moyuyo.common.util.CountryCodes;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingMethodMapper;
import com.moyuyo.dao.admin.mapper.ShippingRateMapper;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.ShippingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运费试算服务实现。
 * <p>
 * 计算流程（行业通用四要素）：
 * <ol>
 *   <li>country → zone（{@code mo_shipping_zone.country_codes} 匹配，归一化后比较）</li>
 *   <li>加载该 zone 下所有 ACTIVE rate × method（按 priority 升序）</li>
 *   <li>每个 method 计算 freight：first + continue × (CEIL(qty|weight|amt) - 1)</li>
 *   <li>套免邮门槛：subtotal ≥ freeThreshold → freight=0；否则 freeShortBy = freeThreshold - subtotal</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ShippingRateServiceImpl implements ShippingRateService {

    /** 默认单品重量（克）：当商品 weight 为空且请求未传 weightGrams 时的兜底 */
    private static final BigDecimal DEFAULT_ITEM_WEIGHT_G = new BigDecimal(500);

    private final ShippingZoneMapper shippingZoneMapper;
    private final ShippingMethodMapper shippingMethodMapper;
    private final ShippingRateMapper shippingRateMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ShippingMethodVO> quote(ShippingQuoteRequest req) {
        if (req == null || req.getCountry() == null || req.getCountry().isBlank()) {
            return List.of();
        }
        // 1) country → zone
        ShippingZoneEntity zone = resolveZone(req.getCountry());
        if (zone == null) {
            log.debug("[shipping.quote] country={} 不在可发货区域", req.getCountry());
            return List.of();
        }

        // 2) 加载该 zone 下所有 ACTIVE rate
        List<ShippingRateEntity> rates = shippingRateMapper.selectList(
                new LambdaQueryWrapper<ShippingRateEntity>()
                        .eq(ShippingRateEntity::getZoneId, zone.getId())
                        .eq(ShippingRateEntity::getStatus, "ACTIVE")
                        .orderByAsc(ShippingRateEntity::getPriority));
        if (rates.isEmpty()) {
            return List.of();
        }

        // 3) 加载对应 method 字典（避免 N+1）。使用 LambdaQueryWrapper.in 替代已废弃的 selectBatchIds
        List<Long> methodIds = rates.stream().map(ShippingRateEntity::getMethodId).distinct().toList();
        Map<Long, ShippingMethodEntity> methodMap = shippingMethodMapper.selectList(
                new LambdaQueryWrapper<ShippingMethodEntity>().in(ShippingMethodEntity::getId, methodIds))
                .stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .collect(Collectors.toMap(ShippingMethodEntity::getId, m -> m));

        // 4) 同 method 多条 ACTIVE rule 时只保留 priority 最小的一条（防御脏数据）
        Map<Long, ShippingRateEntity> bestRateByMethod = new java.util.HashMap<>();
        for (ShippingRateEntity r : rates) {
            ShippingRateEntity cur = bestRateByMethod.get(r.getMethodId());
            if (cur == null || r.getPriority() < cur.getPriority()) {
                bestRateByMethod.put(r.getMethodId(), r);
            }
        }

        // 5) subtotal：请求传了用请求的，否则服务端按 items 价格累加（MVP 用 quantity * 0 兜底，
        //    真实价格由 controller 层从购物车/订单快照补；此处只关心免邮门槛，不精确到分）
        BigDecimal subtotal = req.getSubtotal() != null
                ? req.getSubtotal()
                : BigDecimal.ZERO;

        // 6) 渲染 VO
        List<ShippingMethodVO> result = new ArrayList<>(bestRateByMethod.size());
        for (Map.Entry<Long, ShippingRateEntity> e : bestRateByMethod.entrySet()) {
            ShippingMethodEntity method = methodMap.get(e.getKey());
            if (method == null) continue;
            ShippingRateEntity rate = e.getValue();

            BigDecimal rawFreight = computeFreight(rate, req.getItems(), subtotal);
            boolean free = false;
            BigDecimal shortBy = BigDecimal.ZERO;
            if (rate.getFreeThreshold() != null && subtotal.compareTo(rate.getFreeThreshold()) >= 0) {
                rawFreight = BigDecimal.ZERO;
                free = true;
            } else if (rate.getFreeThreshold() != null) {
                shortBy = rate.getFreeThreshold().subtract(subtotal).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            ShippingMethodVO vo = new ShippingMethodVO();
            vo.setCode(method.getCode());
            vo.setNameEn(method.getNameEn());
            vo.setNameZh(method.getNameZh());
            vo.setEtaMinDays(method.getEtaMinDays());
            vo.setEtaMaxDays(method.getEtaMaxDays());
            vo.setSortOrder(method.getSortOrder());
            vo.setFreight(rawFreight.setScale(2, RoundingMode.HALF_UP));
            vo.setCurrency(rate.getCurrency() != null ? rate.getCurrency() : "USD");
            vo.setFree(free);
            vo.setFreeShortBy(shortBy);
            vo.setFreeThreshold(rate.getFreeThreshold());
            result.add(vo);
        }
        // Bug H 修复：按 method.sortOrder 升序排序（运营控制展示顺序：standard=10, express=20, priority=30, same_day=40）
        // 之前按 code 字母序排，会导致 priority 排在 same_day 之前，运营配置无意义
        result.sort(Comparator.comparing(ShippingMethodVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calcFreight(String country, String methodCode,
                                  List<ShippingQuoteRequest.Item> items, BigDecimal subtotal) {
        if (country == null || methodCode == null) return null;
        ShippingZoneEntity zone = resolveZone(country);
        if (zone == null) return null;

        // 找到 methodId
        ShippingMethodEntity method = shippingMethodMapper.selectOne(
                new LambdaQueryWrapper<ShippingMethodEntity>()
                        .eq(ShippingMethodEntity::getCode, methodCode)
                        .eq(ShippingMethodEntity::getStatus, "ACTIVE")
                        .last("LIMIT 1"));
        if (method == null) return null;

        // 加载该 (zone, method) 下 priority 最小的一条 ACTIVE rule
        ShippingRateEntity rate = shippingRateMapper.selectOne(
                new LambdaQueryWrapper<ShippingRateEntity>()
                        .eq(ShippingRateEntity::getZoneId, zone.getId())
                        .eq(ShippingRateEntity::getMethodId, method.getId())
                        .eq(ShippingRateEntity::getStatus, "ACTIVE")
                        .orderByAsc(ShippingRateEntity::getPriority)
                        .last("LIMIT 1"));
        if (rate == null) return null;

        BigDecimal freight = computeFreight(rate, items, subtotal);
        // 套免邮门槛
        if (rate.getFreeThreshold() != null && subtotal != null
                && subtotal.compareTo(rate.getFreeThreshold()) >= 0) {
            freight = BigDecimal.ZERO;
        }
        return freight.setScale(2, RoundingMode.HALF_UP);
    }

    // ========== 内部工具方法 ==========

    /**
     * 按 country 在所有 ACTIVE zone 中匹配第一个命中。
     * 复用 common 工具的归一化逻辑（去空格、转大写、去重）。
     */
    private ShippingZoneEntity resolveZone(String country) {
        String normalized = CountryCodes.normalizeSingle(country);
        if (normalized.isEmpty()) return null;
        List<ShippingZoneEntity> zones = shippingZoneMapper.selectList(
                new LambdaQueryWrapper<ShippingZoneEntity>().eq(ShippingZoneEntity::getStatus, "ACTIVE"));
        for (ShippingZoneEntity z : zones) {
            if (CountryCodes.toSet(z.getCountryCodes()).contains(normalized)) {
                return z;
            }
        }
        return null;
    }

    /**
     * 按规则计算运费（未应用免邮门槛）。
     * <p>
     * 公式：freight = first_charge + continue_charge * max(0, units - first_unit)
     * units：
     *   - charge_type=1(按件): CEIL(总件数 / continue_unit)
     *   - charge_type=2(按重): CEIL(总重量(g) / continue_unit)
     *   - charge_type=3(按金额): CEIL(subtotal / continue_unit)
     * 关键点：
     *   - 数量/重量向上取整，避免续费少收
     *   - 续费要减首件/首重（首件已含在首费里）
     */
    private BigDecimal computeFreight(ShippingRateEntity rate,
                                      List<ShippingQuoteRequest.Item> items,
                                      BigDecimal subtotal) {
        // P3 修复：先算 units，qty=0/weight=0/subtotal=0 时直接返 0，不收首费
        BigDecimal units;
        if (rate.getChargeType() == null) {
            units = BigDecimal.ZERO;
        } else if (rate.getChargeType() == 1) {
            // 按件
            int qty = items == null ? 0
                    : items.stream()
                            .filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                            .mapToInt(ShippingQuoteRequest.Item::getQuantity)
                            .sum();
            units = toUnits(qty, rate.getContinueUnit());
        } else if (rate.getChargeType() == 2) {
            // 按重
            int grams = 0;
            if (items != null) {
                for (ShippingQuoteRequest.Item it : items) {
                    int q = it.getQuantity() == null ? 0 : it.getQuantity();
                    int perItem = it.getWeightGrams() != null && it.getWeightGrams() > 0
                            ? it.getWeightGrams()
                            : DEFAULT_ITEM_WEIGHT_G.intValue();
                    grams += perItem * q;
                }
            }
            units = toUnits(grams, rate.getContinueUnit());
        } else {
            // 按金额（charge_type=3）
            BigDecimal amt = subtotal != null ? subtotal : BigDecimal.ZERO;
            units = toUnitsBig(amt, rate.getContinueUnit());
        }

        // P3 修复：units == 0（空购物车/无商品/0 金额）时直接返 0，避免空购物车误收首费
        if (units.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        int firstUnit = rate.getFirstUnit() == null || rate.getFirstUnit() <= 0 ? 1 : rate.getFirstUnit();
        // 续费单位数 = 总单位数 - 首件/首重所含单位数（行业惯例：首费已覆盖首件/首重）
        BigDecimal extraUnits = units.subtract(BigDecimal.valueOf(firstUnit)).max(BigDecimal.ZERO);
        BigDecimal continueCharge = rate.getContinueCharge() != null ? rate.getContinueCharge() : BigDecimal.ZERO;
        BigDecimal freight = rate.getFirstCharge().add(continueCharge.multiply(extraUnits));
        return freight.max(BigDecimal.ZERO);
    }

    /** 整数单位向上取整；continueUnit <= 0 时按 1 兜底，避免除零 */
    private BigDecimal toUnits(int total, Integer continueUnit) {
        int unit = continueUnit == null || continueUnit <= 0 ? 1 : continueUnit;
        long ceil = ((long) total + unit - 1) / unit;
        return BigDecimal.valueOf(Math.max(0, ceil));
    }

    /** BigDecimal 单位向上取整 */
    private BigDecimal toUnitsBig(BigDecimal total, Integer continueUnit) {
        int unit = continueUnit == null || continueUnit <= 0 ? 1 : continueUnit;
        BigDecimal u = BigDecimal.valueOf(unit);
        BigDecimal ceil = total.divide(u, 0, RoundingMode.CEILING);
        return ceil.max(BigDecimal.ZERO);
    }
}
