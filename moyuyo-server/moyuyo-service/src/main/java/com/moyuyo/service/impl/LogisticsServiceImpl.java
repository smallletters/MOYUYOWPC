package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.LogisticsTrackProvider;
import com.moyuyo.service.config.LogisticsTrackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsMapper logisticsMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final LogisticsTrackProperties logisticsTrackProperties;

    /**
     * 物流轨迹查询 Provider（按 moyuyo.logistics.provider 配置自动注入对应实现）
     * <p>
     * - provider=none 时注入 NoopLogisticsTrackProvider（返回空轨迹）
     * - provider=17track 时注入 Track17TrackProvider
     * - provider=kuaidi100 时注入 Kuaidi100LogisticsTrackProvider
     * <p>
     * 由于三个实现互斥（@ConditionalOnProperty），此处不会出现多候选冲突
     */
    private final LogisticsTrackProvider trackProvider;

    /**
     * 本地内存缓存：trackingNumber -> 轨迹快照
     * <p>
     * 设计要点：
     * <ul>
     *   <li>避免每次查询订单详情都调用第三方 API（17TRACK 限流 3 req/s）</li>
     *   <li>TTL 由 LogisticsTrackProperties.pollIntervalMinutes 控制（默认 30 分钟）</li>
     *   <li>仅缓存成功拉取的轨迹，空轨迹不缓存（下次查询会重试拉取）</li>
     *   <li>进程级缓存：多实例部署时各实例缓存独立，可接受（轨迹查询频率低）</li>
     * </ul>
     */
    private final Map<String, TrackCacheEntry> trackCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public LogisticsEntity shipOrder(Long orderId, String carrier, String trackingNumber) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        // 已支付（PAID）或待发货（PENDING_SHIP）状态均可发货
        String status = order.getStatus();
        if (!OrderStatusEnum.PAID.name().equals(status) && !OrderStatusEnum.PENDING_SHIP.name().equals(status)) {
            throw new IllegalStateException("订单未支付或不在待发货状态，不能发货");
        }

        LogisticsEntity existing = logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId));
        if (existing != null) throw new IllegalStateException("该订单已发货");

        LogisticsEntity logistics = new LogisticsEntity();
        logistics.setOrderId(orderId);
        logistics.setCarrier(carrier);
        logistics.setTrackingNumber(trackingNumber);
        logistics.setShippedAt(LocalDateTime.now());
        logistics.setTraces(toTracesJson("Shipped", carrier, trackingNumber));
        logisticsMapper.insert(logistics);

        order.setStatus(OrderStatusEnum.SHIPPED.name());
        order.setShippingCarrier(carrier);
        order.setTrackingNumber(trackingNumber);
        order.setDeliverTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order shipped: orderId={}, carrier={}, tracking={}", orderId, carrier, trackingNumber);
        return logistics;
    }

    /**
     * 查询订单物流信息
     * <p>
     * 流程：
     * <ol>
     *   <li>查 DB 获取 LogisticsEntity（含人工录入的 traces）</li>
     *   <li>未发货 / 无运单号 → 直接返回 DB 数据</li>
     *   <li>已签收（receivedAt 非空）→ 直接返回 DB 数据（轨迹已最终化）</li>
     *   <li>否则尝试从缓存读取最新轨迹</li>
     *   <li>缓存未命中 → 调用 Provider 拉取，成功则刷新 DB.traces + 缓存</li>
     *   <li>检测签收：Provider 判定已签收 → 自动触发 confirmReceived（幂等）</li>
     * </ol>
     * <p>
     * 异常兜底：Provider 调用失败（熔断/超时）返回空列表时，回落到 DB.traces 返回
     */
    @Override
    public LogisticsEntity getLogisticsByOrderId(Long orderId) {
        LogisticsEntity entity = logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId));
        if (entity == null) {
            return null;
        }

        // 无运单号或已签收，直接返回 DB 数据
        String trackingNumber = entity.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank() || entity.getReceivedAt() != null) {
            return entity;
        }

        // 尝试从缓存读取
        TrackCacheEntry cached = trackCache.get(trackingNumber);
        if (cached != null && !cached.isExpired()) {
            // 缓存命中：用缓存的轨迹覆盖 DB.traces 字段返回（不写库）
            entity.setTraces(cached.getTracesJson());
            if (cached.isDelivered()) {
                triggerConfirmReceived(orderId);
            }
            return entity;
        }

        // 缓存未命中或已过期：调用 Provider 拉取最新轨迹
        List<LogisticsVO.TraceItem> freshTracks;
        try {
            freshTracks = trackProvider.queryTracks(entity.getCarrier(), trackingNumber);
        } catch (Exception e) {
            // Provider 异常兜底：返回 DB.traces（人工录入兜底）
            log.warn("TrackProvider query failed, fallback to DB traces: orderId={}, tracking={}, msg={}",
                    orderId, trackingNumber, e.getMessage());
            return entity;
        }

        if (freshTracks == null || freshTracks.isEmpty()) {
            // Provider 返回空（未注册/暂无信息/熔断 fallback）：返回 DB.traces
            return entity;
        }

        // 拉取成功：序列化为 JSON 写库 + 更新缓存
        String freshTracesJson = serializeTraces(freshTracks);
        boolean delivered = checkDelivered(trackProvider, entity.getCarrier(), trackingNumber);

        // 写库（仅 traces 字段，避免覆盖 receivedAt 等）
        try {
            LogisticsEntity patch = new LogisticsEntity();
            patch.setId(entity.getId());
            patch.setTraces(freshTracesJson);
            logisticsMapper.updateById(patch);
            entity.setTraces(freshTracesJson);
        } catch (Exception e) {
            log.warn("Failed to persist fresh traces, returning in-memory only: orderId={}, msg={}",
                    orderId, e.getMessage());
        }

        // 更新缓存
        trackCache.put(trackingNumber, new TrackCacheEntry(freshTracesJson, delivered));

        // 检测签收：触发自动确认收货（幂等）
        if (delivered) {
            triggerConfirmReceived(orderId);
        }

        return entity;
    }

    @Override
    @Transactional
    public LogisticsEntity updateTracking(Long logisticsId, String traces) {
        LogisticsEntity entity = logisticsMapper.selectById(logisticsId);
        if (entity == null) throw new IllegalArgumentException("物流记录不存在");
        entity.setTraces(traces);
        logisticsMapper.updateById(entity);
        // 人工更新轨迹时失效缓存，下次查询会重新拉取
        if (entity.getTrackingNumber() != null) {
            trackCache.remove(entity.getTrackingNumber());
        }
        return entity;
    }

    @Override
    @Transactional
    public void confirmReceived(Long orderId) {
        LocalDateTime now = LocalDateTime.now();

        // 使用条件更新避免 TOCTOU 竞态条件：仅在未收货时才更新 receivedAt
        int logisticsUpdated = logisticsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId)
                        .isNull(LogisticsEntity::getReceivedAt)
                        .set(LogisticsEntity::getReceivedAt, now));

        // 使用条件更新：仅在已发货状态时才变更为已收货，防止状态错乱
        int orderUpdated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderEntity>()
                        .eq(OrderEntity::getId, orderId)
                        .eq(OrderEntity::getStatus, OrderStatusEnum.SHIPPED.name())
                        .set(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
                        .set(OrderEntity::getReceivedTime, now));

        // 只要任一表有更新就记录日志，重复调用不抛出异常（幂等）
        if (logisticsUpdated > 0 || orderUpdated > 0) {
            log.info("Delivery confirmed: orderId={}", orderId);
        } else {
            log.warn("Delivery confirm skipped (already received or not shipped): orderId={}", orderId);
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 触发自动确认收货（幂等，重复调用不会产生副作用）
     * <p>
     * 通过新事务方法调用避免 self-injection 问题：
     * confirmReceived 已使用条件更新实现幂等，此处直接调用即可
     */
    private void triggerConfirmReceived(Long orderId) {
        try {
            confirmReceived(orderId);
        } catch (Exception e) {
            log.warn("Auto-confirm received failed (will retry on next query): orderId={}, msg={}",
                    orderId, e.getMessage());
        }
    }

    /**
     * 签收判定：优先用 Provider 的 isDelivered（精确判定）
     * <p>
     * Provider 内部已实现关键字匹配（delivered/签收/妥投等）
     */
    private boolean checkDelivered(LogisticsTrackProvider provider, String carrier, String trackingNumber) {
        try {
            return provider.isDelivered(carrier, trackingNumber);
        } catch (Exception e) {
            // Provider.isDelivered 失败时不影响主流程（仅丢失自动签收触发）
            log.warn("Provider.isDelivered failed: tracking={}, msg={}", trackingNumber, e.getMessage());
            return false;
        }
    }

    /**
     * 序列化轨迹列表为 JSON 字符串
     */
    private String serializeTraces(List<LogisticsVO.TraceItem> tracks) {
        try {
            return objectMapper.writeValueAsString(tracks);
        } catch (JsonProcessingException e) {
            log.error("Serialize tracks failed", e);
            return "[]";
        }
    }

    /**
     * 反序列化轨迹 JSON（用于读取 DB 中的 traces 字段，目前未使用，预留给其他场景）
     */
    @SuppressWarnings("unused")
    private List<LogisticsVO.TraceItem> deserializeTraces(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<LogisticsVO.TraceItem>>() {});
        } catch (Exception e) {
            log.warn("Deserialize tracks failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toTracesJson(String event, String carrier, String tracking) {
        try {
            LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
            item.setTime(LocalDateTime.now().toString());
            item.setLocation("");
            item.setDesc("Package shipped via " + carrier + ", tracking: " + tracking);
            item.setStatus("shipped");
            return objectMapper.writeValueAsString(List.of(item));
        } catch (JsonProcessingException e) {
            log.error("JSON serialization error", e);
            return "[]";
        }
    }

    /**
     * 轨迹缓存条目
     * <p>
     * - tracesJson：序列化后的轨迹列表，避免每次反序列化
     * - delivered：签收标志，避免每次重新判定
     * - fetchedAt：拉取时间戳（毫秒），用于 TTL 判定
     */
    private class TrackCacheEntry {
        private final String tracesJson;
        private final boolean delivered;
        private final long fetchedAt;

        TrackCacheEntry(String tracesJson, boolean delivered) {
            this.tracesJson = tracesJson;
            this.delivered = delivered;
            this.fetchedAt = System.currentTimeMillis();
        }

        String getTracesJson() {
            return tracesJson;
        }

        boolean isDelivered() {
            return delivered;
        }

        boolean isExpired() {
            long ttlMs = logisticsTrackProperties.getPollIntervalMinutes() * 60_000L;
            return System.currentTimeMillis() - fetchedAt > ttlMs;
        }
    }
}
