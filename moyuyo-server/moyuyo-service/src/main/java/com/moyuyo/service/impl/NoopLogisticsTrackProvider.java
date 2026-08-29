package com.moyuyo.service.impl;

import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.service.LogisticsTrackProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 默认物流轨迹占位实现
 * <p>
 * 当 {@code moyuyo.logistics.provider=none} 或未配置时注入，仅返回空轨迹，
 * 物流弹窗完全依赖人工录入的 traces 字段。不调用任何外部 API。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "moyuyo.logistics", name = "provider", havingValue = "none", matchIfMissing = true)
public class NoopLogisticsTrackProvider implements LogisticsTrackProvider {

    @Override
    public List<LogisticsVO.TraceItem> queryTracks(String carrier, String trackingNumber) {
        log.debug("NoopLogisticsTrackProvider.queryTracks called: carrier={}, tracking={}", carrier, trackingNumber);
        return Collections.emptyList();
    }

    @Override
    public boolean isDelivered(String carrier, String trackingNumber) {
        return false;
    }

    @Override
    public String getProviderName() {
        return "none";
    }
}
