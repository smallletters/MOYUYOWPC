package com.moyuyo.service.impl;

import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.service.LogisticsTrackProvider;
import com.moyuyo.service.config.LogisticsTrackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 快递100 物流轨迹实现（骨架）
 * <p>
 * 启用条件：{@code moyuyo.logistics.provider=kuaidi100}
 * 实际接入时需要：
 * <ol>
 *   <li>调用快递100 实时查询接口：POST https://poll.kuaidi100.com/poll/query.do</li>
 *   <li>请求参数：customer=${properties.customer}, sign=${MD5(apiKey + ...)}, com=${carrier}, num=${trackingNumber}</li>
 *   <li>响应解析：将 lastResult.data 映射为 {@link LogisticsVO.TraceItem}（ftime/location/context/status）</li>
 *   <li>签收判定：lastResult.state = "3"（已签收）</li>
 *   <li>异常兜底：API 失败时返回空列表，不抛异常阻塞业务</li>
 * </ol>
 *
 * @see <a href="https://www.kuaidi100.com/openapi/api_5_11.shtml">快递100 实时查询接口文档</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyuyo.logistics", name = "provider", havingValue = "kuaidi100")
public class Kuaidi100LogisticsTrackProvider implements LogisticsTrackProvider {

    private final LogisticsTrackProperties properties;

    @Override
    public List<LogisticsVO.TraceItem> queryTracks(String carrier, String trackingNumber) {
        // TODO 接入快递100 API
        //  1. 签名：sign = MD5(apiKey + customer + ...).toUpperCase()
        //  2. 构造请求参数：customer, sign, com(carrier 映射), num(trackingNumber)
        //  3. 使用 properties.connectTimeoutMs / readTimeoutMs 配置 HTTP 客户端
        //  4. 解析 lastResult.data -> List<LogisticsVO.TraceItem>
        //  5. 承运商代码映射：内部 carrier(SF/YTO) -> 快递100 com(sto/yto)
        log.warn("Kuaidi100LogisticsTrackProvider.queryTracks not implemented, carrier={}, tracking={}", carrier, trackingNumber);
        return Collections.emptyList();
    }

    @Override
    public boolean isDelivered(String carrier, String trackingNumber) {
        // TODO 解析 lastResult.state = "3" 视为已签收
        log.warn("Kuaidi100LogisticsTrackProvider.isDelivered not implemented, carrier={}, tracking={}", carrier, trackingNumber);
        return false;
    }

    @Override
    public String getProviderName() {
        return "kuaidi100";
    }
}
