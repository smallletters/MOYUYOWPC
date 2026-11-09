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
import com.moyuyo.common.util.IdempotencyHelper;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.LogisticsTrackProvider;
import com.moyuyo.service.NotificationService;
import com.moyuyo.service.config.LogisticsTrackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsMapper logisticsMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final LogisticsTrackProperties logisticsTrackProperties;
    private final NotificationService notificationService;
    // P0：幂等表读写，避免并发 / 重试造成的重复发货
    private final JdbcTemplate jdbcTemplate;

    /**
     * 编程式事务模板：自动签收是同类私有方法调用，@Transactional 代理不生效，
     * 需用它把"订单状态 + 物流签收时间"两次写入包在同一事务里，避免出现
     * 订单已收货但物流 received_at 为空（轮询只扫 SHIPPED，将永远无法补写）的不一致状态。
     */
    private final TransactionTemplate transactionTemplate;

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
     * 签收关键字（忽略大小写）：覆盖国内外承运商的中英文事件描述
     * <p>
     * 注意不能包含 "received"：承运商常用 "Shipment received at facility" 表示到达中转场，
     * 并非签收；也不能单独匹配 "签收"，否则 "待签收" 会误判。
     */
    private static final Set<String> DELIVERED_KEYWORDS = Set.of(
            "delivered", "signed", "签收成功", "已签收", "妥投", "已妥投");

    /**
     * 非签收否定词：命中即判为未签收，避免 "not delivered" / "未签收" / "待签收" 等误判
     */
    private static final Set<String> NOT_DELIVERED_KEYWORDS = Set.of(
            "not delivered", "undelivered", "未签收", "待签收", "未妥投", "拒收");

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
        // P0：幂等守卫 —— 先用 INSERT IGNORE 抢锁，再走业务逻辑。
        // 设计要点：
        //   1) INSERT IGNORE 在事务内执行；唯一索引 (scope, biz_id, idem_key) 保证并发时只有一个事务插入成功。
        //   2) 插入成功 → 本事务拿到发货权，继续走下面的状态校验 + 写 logistics。
        //   3) 插入失败（被忽略）→ 另一事务已发货 → 直接返回已有物流记录，绝不重复写。
        //   4) 走完业务后不再 INSERT 幂等行（重复 INSERT 必抛 DataIntegrityViolationException），减少一次写入。
        String idemKey = IdempotencyHelper.shipFingerprint(orderId, carrier, trackingNumber);
        int inserted = jdbcTemplate.update(
            "INSERT IGNORE INTO mo_idempotency_record (scope, biz_id, idem_key, response_json) VALUES (?, ?, ?, ?)",
            "ship_order", orderId, idemKey, "{\"orderId\":" + orderId + "}");
        if (inserted == 0) {
            // 被 IGNORE 跳过 → 另一事务已抢到幂等键，本事务放弃发货
            log.info("shipOrder 幂等命中（并发抢锁失败），跳过重复发货：orderId={}, key={}", orderId, idemKey);
            return logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>().eq(LogisticsEntity::getOrderId, orderId));
        }

        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        // 已支付（PAID）或待发货（PENDING_SHIP）状态均可发货 —— 与 OrderStatusGuard.SHIP_FROM 一致。
        String status = order.getStatus();
        if (!OrderStatusEnum.PAID.name().equals(status) && !OrderStatusEnum.PENDING_SHIP.name().equals(status)) {
            throw new IllegalStateException("订单未支付或不在待发货状态，不能发货");
        }
        // 注意：这里暂不调用 OrderStatusGuard.assertAllowed() —— shipOrder 是
        //   service 层底层接口，被多处复用；保留 string 比对是为了不破坏现有错误消息。
        //   controller 层 AdminOrderController#ship 已先用 OrderStatusEnum 守卫，
        //   业务上游拒绝的状态根本走不到这里。

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
        try {
            // V20260929_07 后 mo_logistics.order_id 已加 UNIQUE 索引。
            //   配合上面的幂等键抢锁，正常情况不会冲突；
            //   catch 兜底以应对：不同运单号 hash 出巧合 idemKey 的极端情况。
            logisticsMapper.insert(logistics);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 唯一索引 (order_id) 拒绝 → 已有 logistics 行；返回已有记录。
            log.warn("shipOrder 物流行唯一索引冲突（已被另一事务写入）：orderId={}", orderId);
            return logisticsMapper.selectOne(
                new LambdaQueryWrapper<LogisticsEntity>().eq(LogisticsEntity::getOrderId, orderId));
        }

        order.setStatus(OrderStatusEnum.SHIPPED.name());
        order.setShippingCarrier(carrier);
        order.setTrackingNumber(trackingNumber);
        order.setDeliverTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order shipped: orderId={}, carrier={}, tracking={}", orderId, carrier, trackingNumber);
        // 发货通知：用户点击通知可直接进入订单物流
        try {
            notificationService.notifyUser(order.getUserId(), "ORDER", "ORDER",
                    "订单已发货",
                    "您的订单 " + order.getOrderNo() + " 已发货，承运商：" + carrier
                            + "，运单号：" + trackingNumber,
                    order.getId());
        } catch (Exception e) {
            log.warn("[logistics] 发货通知发送失败: orderId={}, reason={}", orderId, e.getMessage());
        }
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
            if (cached.isDelivered() && triggerConfirmReceived(orderId)) {
                // 同步内存对象，保证本次响应就体现签收状态
                entity.setReceivedAt(LocalDateTime.now());
            }
            return entity;
        }
        // 过期条目顺手清理，避免只增不删
        if (cached != null) {
            trackCache.remove(trackingNumber);
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
        boolean delivered = checkDelivered(freshTracks);

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

        // 更新缓存（超过容量上限时清理过期与最旧条目）
        trackCache.put(trackingNumber, new TrackCacheEntry(freshTracesJson, delivered));
        trimTrackCache();

        // 检测签收：触发自动确认收货（幂等）
        if (delivered && triggerConfirmReceived(orderId)) {
            // 同步内存对象：否则本次响应仍返回 receivedAt=null，
            // 前端第一次打开物流页会显示"运输中"，与刚写入的签收状态不一致
            entity.setReceivedAt(LocalDateTime.now());
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
    public boolean confirmReceived(Long orderId) {
        return doConfirmReceived(orderId);
    }

    /**
     * 确认收货核心逻辑（条件更新，幂等）
     * <p>
     * 外部调用（confirmReceived）经 Spring 代理走 @Transactional；
     * 自动签收由 {@link #triggerConfirmReceived} 用 TransactionTemplate 包裹，保证两次写入同事务。
     * 先更新订单、成功后再写物流：订单不在 SHIPPED（已取消/已完成）时不会误写物流签收时间。
     *
     * @return true=订单本次确实流转为已收货
     */
    private boolean doConfirmReceived(Long orderId) {
        LocalDateTime now = LocalDateTime.now();

        // 1) 先更新订单：仅在已发货状态时才变更为已收货，防止状态错乱
        int orderUpdated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderEntity>()
                        .eq(OrderEntity::getId, orderId)
                        .eq(OrderEntity::getStatus, OrderStatusEnum.SHIPPED.name())
                        .set(OrderEntity::getStatus, OrderStatusEnum.RECEIVED.name())
                        .set(OrderEntity::getReceivedTime, now));

        if (orderUpdated == 0) {
            log.warn("Delivery confirm skipped (already received, cancelled or not shipped): orderId={}", orderId);
            return false;
        }

        // 2) 订单流转成功后，再同步物流签收时间（仅当物流记录尚未签收时写入）
        int logisticsUpdated = logisticsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LogisticsEntity>()
                        .eq(LogisticsEntity::getOrderId, orderId)
                        .isNull(LogisticsEntity::getReceivedAt)
                        .set(LogisticsEntity::getReceivedAt, now));

        log.info("Delivery confirmed: orderId={}, logisticsUpdated={}", orderId, logisticsUpdated);
        return true;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 触发自动确认收货（幂等，重复调用不会产生副作用）
     * <p>
     * 同类内部调用无法经过 Spring 事务代理，这里用 TransactionTemplate 显式开启事务，
     * 保证"订单状态置 RECEIVED"和"物流 received_at 写签收时间"要么同时成功、要么同时回滚。
     *
     * @return true=确认收货成功（可用于同步内存对象的状态）
     */
    private boolean triggerConfirmReceived(Long orderId) {
        try {
            // 只有订单真正流转成功才返回 true，调用方据此同步内存中的签收状态
            Boolean confirmed = transactionTemplate.execute(status -> doConfirmReceived(orderId));
            return Boolean.TRUE.equals(confirmed);
        } catch (Exception e) {
            log.warn("Auto-confirm received failed (will retry on next query): orderId={}, msg={}",
                    orderId, e.getMessage());
            return false;
        }
    }

    /**
     * 签收判定：直接基于本次拉取的轨迹判断（最新一条）
     * <p>
     * 之前调用 Provider.isDelivered 会再请求一次第三方 API（17TRACK 限流 3 req/s），
     * 一次物流详情消耗 2 次配额；这里复用已拉取的轨迹，避免重复调用。
     */
    private boolean checkDelivered(List<LogisticsVO.TraceItem> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return false;
        }
        LogisticsVO.TraceItem latest = tracks.get(0);
        String desc = latest.getDesc();
        String status = latest.getStatus();
        // 否定词优先：未送达/待签收等表述一律不触发自动确认收货
        if (containsAnyKeyword(desc, NOT_DELIVERED_KEYWORDS)
                || containsAnyKeyword(status, NOT_DELIVERED_KEYWORDS)) {
            return false;
        }
        return containsAnyKeyword(desc, DELIVERED_KEYWORDS)
                || containsAnyKeyword(status, DELIVERED_KEYWORDS);
    }

    /**
     * 关键字匹配（忽略大小写）
     */
    private boolean containsAnyKeyword(String text, Set<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 缓存容量控制：超过上限时先清理过期条目，仍超限则按拉取时间淘汰最旧条目
     * <p>
     * 仅在写入时低频触发，避免每次查询都遍历。
     */
    private void trimTrackCache() {
        int maxSize = Math.max(1, logisticsTrackProperties.getCacheMaxSize());
        if (trackCache.size() <= maxSize) {
            return;
        }
        trackCache.entrySet().removeIf(e -> e.getValue().isExpired());
        int overflow = trackCache.size() - maxSize;
        if (overflow <= 0) {
            return;
        }
        trackCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getFetchedAt()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(trackCache::remove);
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

        long getFetchedAt() {
            return fetchedAt;
        }
    }
}
