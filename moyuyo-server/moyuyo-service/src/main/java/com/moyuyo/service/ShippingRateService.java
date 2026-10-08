package com.moyuyo.service;

import com.moyuyo.common.dto.shipping.ShippingMethodVO;
import com.moyuyo.common.dto.shipping.ShippingQuoteRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运费试算服务（APP 端）。
 * <p>
 * 服务端权威：运费由后端按 zone × method × 计费维度实时计算，
 * 下单时由 {@code OrderServiceImpl.createOrder} 重算覆盖前端传入的 freight，
 * 防止客户端被改包刷价。
 */
public interface ShippingRateService {

    /**
     * 查询某国家所有可用配送方式 + 实时运费。
     * <p>
     * 命中规则：
     * <ol>
     *   <li>country 命中 zone（zone.country_codes 含该国家码）→ 加载该 zone 下所有 ACTIVE rate</li>
     *   <li>未命中 zone → 返回空列表（前端按"不可发货"处理）</li>
     *   <li>同 method 多条 ACTIVE rule → 取 priority 最小的</li>
     * </ol>
     *
     * @param req 包含 country、items、subtotal
     * @return 各配送方式的实时运费 VO 列表，按 method.sortOrder 升序
     */
    List<ShippingMethodVO> quote(ShippingQuoteRequest req);

    /**
     * 计算指定 (country, methodCode) 的运费。
     * 给 {@code OrderServiceImpl.createOrder} 服务端权威覆写用。
     *
     * @param country   国家码（alpha-2 大写）
     * @param methodCode 配送方式编码
     * @param items     结算商品列表
     * @param subtotal  满减前商品金额
     * @return 运费；返回 null 表示该 (country, methodCode) 不可用
     */
    BigDecimal calcFreight(String country, String methodCode,
                           List<ShippingQuoteRequest.Item> items, BigDecimal subtotal);
}
