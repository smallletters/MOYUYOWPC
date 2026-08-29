package com.moyuyo.service;

import com.moyuyo.common.dto.logistics.LogisticsVO;

import java.util.List;

/**
 * 物流轨迹查询 SPI
 * <p>
 * 不同第三方平台（17TRACK / 快递100 / AfterShip）各自实现，按 moyuyo.logistics.provider 配置注入：
 * <ul>
 *   <li>{@code none}      - {@code NoopLogisticsTrackProvider}，本地占位，仅返回空轨迹</li>
 *   <li>{@code 17track}   - {@code Track17TrackProvider}，调用 17TRACK API</li>
 *   <li>{@code kuaidi100} - {@code Kuaidi100LogisticsTrackProvider}，调用快递100 API</li>
 * </ul>
 * 业务侧通过 {@code LogisticsService} 软依赖注入，未配置时降级走 Noop 实现，不阻塞启动。
 */
public interface LogisticsTrackProvider {

    /**
     * 查询运单轨迹
     *
     * @param carrier        承运商代码（如 SF / YTO / USPS / FEDEX）
     * @param trackingNumber 运单号
     * @return 轨迹列表，按时间倒序排列（最新轨迹置顶）；无轨迹时返回空列表
     */
    List<LogisticsVO.TraceItem> queryTracks(String carrier, String trackingNumber);

    /**
     * 判断运单是否已签收
     * <p>
     * 用于定时任务触发订单自动确认收货。
     *
     * @param carrier        承运商代码
     * @param trackingNumber 运单号
     * @return 已签收返回 true；未签收或查询失败返回 false
     */
    boolean isDelivered(String carrier, String trackingNumber);

    /**
     * 提供商标识（none / 17track / kuaidi100 / aftership），用于日志与监控
     */
    String getProviderName();
}
