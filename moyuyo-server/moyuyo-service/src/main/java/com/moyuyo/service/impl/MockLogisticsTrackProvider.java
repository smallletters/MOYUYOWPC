package com.moyuyo.service.impl;

import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.service.LogisticsTrackProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 17TRACK 测试 Mock 实现
 * <p>
 * 启用条件：{@code moyuyo.logistics.provider=mock}
 * 用途：本地开发 / 联调测试，无需真实 17TRACK API 密钥，按运单号前缀返回不同场景数据
 * <p>
 * 支持场景（按运单号前缀路由）：
 * <ul>
 *   <li>{@code MOCK-DELIVERED-XXX}     → 已签收（多节点轨迹，最后一条含 delivered 关键字）</li>
 *   <li>{@code MOCK-INTRANSIT-XXX}     → 运输中（多节点轨迹，未签收）</li>
 *   <li>{@code MOCK-PENDING-XXX}       → 仅发货（1 条发货轨迹）</li>
 *   <li>{@code MOCK-EMPTY-XXX}         → 空轨迹（模拟暂无信息，触发 LogisticsServiceImpl 兜底 DB.traces）</li>
 *   <li>{@code MOCK-ERROR-XXX}         → 抛 RuntimeException（触发 LogisticsServiceImpl 异常兜底）</li>
 *   <li>{@code MOCK-REJECTED-XXX}      → 模拟 17TRACK rejected 单号未注册（返回空列表触发自动注册流程）</li>
 *   <li>其他单号                        → 默认运输中场景（多节点轨迹，未签收）</li>
 * </ul>
 * <p>
 * 注意：返回的轨迹列表已按时间倒序排列（最新置顶），与 17TRACK 真实响应格式一致
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "moyuyo.logistics", name = "provider", havingValue = "mock")
public class MockLogisticsTrackProvider implements LogisticsTrackProvider {

    /**
     * UTC ISO 时间格式：与 17TRACK 真实响应一致
     */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);

    /**
     * 签收关键字（与 Track17TrackProvider 一致，便于 isDelivered 判定）
     */
    private static final List<String> DELIVERED_KEYWORDS = Arrays.asList(
            "delivered", "signed", "received", "签收", "妥投", "已签收", "已妥投"
    );

    @Override
    public List<LogisticsVO.TraceItem> queryTracks(String carrier, String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Collections.emptyList();
        }

        String upperTracking = trackingNumber.toUpperCase(Locale.ROOT);
        log.info("MockLogisticsTrackProvider.queryTracks: carrier={}, tracking={}", carrier, trackingNumber);

        // 异常场景：抛 RuntimeException，触发 LogisticsServiceImpl 异常兜底
        if (upperTracking.startsWith("MOCK-ERROR-")) {
            log.warn("Mock scenario: ERROR (throws RuntimeException)");
            throw new RuntimeException("Mock error: simulated 17TRACK API failure");
        }

        // 空轨迹场景：返回空列表
        if (upperTracking.startsWith("MOCK-EMPTY-")) {
            log.info("Mock scenario: EMPTY (no tracking info)");
            return Collections.emptyList();
        }

        // 未注册场景：返回空列表（模拟 17TRACK rejected -18019902）
        if (upperTracking.startsWith("MOCK-REJECTED-")) {
            log.info("Mock scenario: REJECTED (not registered)");
            return Collections.emptyList();
        }

        // 已签收场景：多节点轨迹，最后一条含 delivered 关键字
        if (upperTracking.startsWith("MOCK-DELIVERED-")) {
            log.info("Mock scenario: DELIVERED");
            return buildDeliveredScenario(trackingNumber);
        }

        // 仅发货场景：1 条轨迹
        if (upperTracking.startsWith("MOCK-PENDING-")) {
            log.info("Mock scenario: PENDING (just shipped)");
            return buildPendingScenario(trackingNumber);
        }

        // 运输中场景：多节点轨迹，未签收
        if (upperTracking.startsWith("MOCK-INTRANSIT-")) {
            log.info("Mock scenario: IN-TRANSIT");
            return buildInTransitScenario(trackingNumber);
        }

        // 默认场景：通用运输中轨迹
        log.info("Mock scenario: DEFAULT (in-transit)");
        return buildInTransitScenario(trackingNumber);
    }

    @Override
    public boolean isDelivered(String carrier, String trackingNumber) {
        List<LogisticsVO.TraceItem> tracks = queryTracks(carrier, trackingNumber);
        if (tracks.isEmpty()) {
            return false;
        }
        // 取最新一条（已按时间倒序排列，index 0 即最新）
        LogisticsVO.TraceItem latest = tracks.get(0);
        return containsDeliveredKeyword(latest.getDesc())
                || containsDeliveredKeyword(latest.getStatus());
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    // ========== 场景数据构建 ==========

    /**
     * 已签收场景：5 条轨迹，最新一条为签收节点
     * <p>
     * 时间轴（倒序，最新置顶）：
     * <ol>
     *   <li>2026-08-25 14:30 - 收件人签收</li>
     *   <li>2026-08-25 09:00 - 派送中</li>
     *   <li>2026-08-24 18:00 - 到达派送网点</li>
     *   <li>2026-08-24 08:00 - 离开分拣中心</li>
     *   <li>2026-08-23 20:00 - 已揽收</li>
     * </ol>
     */
    private List<LogisticsVO.TraceItem> buildDeliveredScenario(String trackingNumber) {
        List<LogisticsVO.TraceItem> items = new ArrayList<>();
        // 注意：时间从最新到最旧排列（倒序，最新置顶）
        items.add(buildItem("2026-08-25T14:30:00+08:00", "上海市浦东新区", "已签收，收件人本人签收", "delivered"));
        items.add(buildItem("2026-08-25T09:00:00+08:00", "上海市浦东新区", "快件已派送中，派送员电话 138****1234", "out_for_delivery"));
        items.add(buildItem("2026-08-24T18:00:00+08:00", "上海市浦东新区转运中心", "快件已到达派送网点", "in_transit"));
        items.add(buildItem("2026-08-24T08:00:00+08:00", "上海分拣中心", "快件已离开分拣中心", "in_transit"));
        items.add(buildItem("2026-08-23T20:00:00+08:00", "广州市白云区", "已收取快件，运单号：" + trackingNumber, "accepted"));
        return items;
    }

    /**
     * 运输中场景：3 条轨迹，未签收
     */
    private List<LogisticsVO.TraceItem> buildInTransitScenario(String trackingNumber) {
        List<LogisticsVO.TraceItem> items = new ArrayList<>();
        items.add(buildItem("2026-08-28T10:00:00+08:00", "上海分拣中心", "快件已到达分拣中心，正在分拣中", "in_transit"));
        items.add(buildItem("2026-08-27T15:00:00+08:00", "广州转运中心", "快件已离开转运中心，下一站上海", "in_transit"));
        items.add(buildItem("2026-08-26T09:00:00+08:00", "广州市白云区", "已收取快件，运单号：" + trackingNumber, "accepted"));
        return items;
    }

    /**
     * 仅发货场景：1 条轨迹
     */
    private List<LogisticsVO.TraceItem> buildPendingScenario(String trackingNumber) {
        List<LogisticsVO.TraceItem> items = new ArrayList<>();
        items.add(buildItem(
                LocalDateTime.now().minusHours(2).format(ISO_FORMATTER),
                "发货仓库", "快件已揽收，运单号：" + trackingNumber, "accepted"));
        return items;
    }

    /**
     * 构建单个轨迹条目
     */
    private LogisticsVO.TraceItem buildItem(String time, String location, String desc, String status) {
        LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
        item.setTime(time);
        item.setLocation(location);
        item.setDesc(desc);
        item.setStatus(status);
        return item;
    }

    /**
     * 签收关键字匹配（忽略大小写），与 Track17TrackProvider 实现一致
     */
    private boolean containsDeliveredKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : DELIVERED_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
