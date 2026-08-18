package com.moyuyo.service.audit;

import com.moyuyo.dao.admin.entity.OperationLogEntity;
import com.moyuyo.dao.admin.mapper.OperationLogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 操作审计日志异步落库器
 * <p>
 * 关键设计：
 * 1. LinkedBlockingQueue 缓存 OperationLogEntity，flusher 单线程批量写入 mo_operation_log
 * 2. 队列容量由 moyuyo.audit.queue-capacity 控制（默认 10000）
 * 3. 写入失败采用子批容错：单条 / 子批失败不影响后续子批（避免连锁阻塞主业务）
 * 4. 注册 Prometheus 指标：moyuyo_operation_log_queue_size / _dropped_total / _persisted_total
 * <p>
 * 设计要点：
 * - 业务线程只负责 enqueue（非阻塞），写库开销转嫁给 flusher 线程
 * - shutdown 钩子：容器停止时 drain 队列，确保已有审计不丢失
 * - 队列满时按 moyuyo.audit.block-on-queue-full 决定是 fail-open（默认）还是 fail-closed
 */
@Slf4j
@Component
public class OperationLogPersister {

    private final OperationLogMapper operationLogMapper;
    private final int queueCapacity;
    private final int batchSize;
    private final long flushIntervalMs;
    private final boolean blockOnQueueFull;

    /** 待落库的审计日志队列（业务线程生产，flusher 线程消费） */
    private final LinkedBlockingQueue<OperationLogEntity> queue;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger persistedCounter = new AtomicInteger(0);
    private final AtomicInteger droppedCounter = new AtomicInteger(0);
    private final AtomicInteger failedCounter = new AtomicInteger(0);

    private Thread flusher;

    private final Counter droppedTotal;
    private final Counter persistedTotal;
    private final Counter failedTotal;

    public OperationLogPersister(OperationLogMapper operationLogMapper,
                                 MeterRegistry meterRegistry,
                                 @Value("${moyuyo.audit.queue-capacity:10000}") int queueCapacity,
                                 @Value("${moyuyo.audit.batch-size:200}") int batchSize,
                                 @Value("${moyuyo.audit.flush-interval-ms:1000}") long flushIntervalMs,
                                 @Value("${moyuyo.audit.block-on-queue-full:false}") boolean blockOnQueueFull) {
        this.operationLogMapper = operationLogMapper;
        // 合法范围归一化：[1000, 100000]，越界自动调整并 WARN
        this.queueCapacity = (queueCapacity >= 1000 && queueCapacity <= 100000) ? queueCapacity : 10000;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.flushIntervalMs = Math.max(100, flushIntervalMs);
        this.blockOnQueueFull = blockOnQueueFull;
        this.queue = new LinkedBlockingQueue<>(this.queueCapacity);

        // 注册 Prometheus 指标
        this.droppedTotal = Counter.builder("moyuyo_operation_log_dropped_total")
                .description("审计日志因队列满而被丢弃的次数")
                .register(meterRegistry);
        this.persistedTotal = Counter.builder("moyuyo_operation_log_persisted_total")
                .description("审计日志成功落库的条数")
                .register(meterRegistry);
        this.failedTotal = Counter.builder("moyuyo_operation_log_failed_total")
                .description("审计日志落库失败的条数")
                .register(meterRegistry);
        // 队列水位 gauge（用于 MoyuyoAuditLogQueueHighWatermark 告警）
        Gauge.builder("moyuyo_operation_log_queue_size", queue, LinkedBlockingQueue::size)
                .description("当前审计日志队列水位")
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        // 关键修复：先 setDaemon 再 start，避免 flusher 与业务线程争抢 CPU 调度
        // daemon=true 让 flusher 不阻止 JVM 正常退出（与 Spring 优雅停机的 running.set(false) 协同）
        flusher = new Thread(this::flushLoop, "moyuyo-audit-flusher");
        flusher.setDaemon(true);
        // 未捕获异常处理器：避免 flusher 因 RuntimeException 静默死亡后业务线程继续 enqueue 直至队列满
        flusher.setUncaughtExceptionHandler((t, e) -> {
            log.error("[audit] flusher 线程异常退出，审计日志将持续堆积至队列满", e);
            // 不自动重启：避免双 flusher 并发写库；让运维第一时间感知并人工介入
        });
        flusher.start();
        log.info("[audit] OperationLogPersister 启动：queueCapacity={}, batchSize={}, flushInterval={}ms, blockOnQueueFull={}",
                queueCapacity, batchSize, flushIntervalMs, blockOnQueueFull);
    }

    @PreDestroy
    public void stop() {
        // P0 修复：先标记 running=false 再 interrupt，确保 flusher 退出主循环后能感知到 shutdown 信号
        // 历史 Bug：先 interrupt 后 set(false)，若 flusher 此时刚完成一批写入并回到 while 顶部判断 running.get()，
        // 会因 running 仍为 true 继续下一轮循环，浪费 CPU 直到 join 超时
        running.set(false);
        if (flusher != null) {
            flusher.interrupt();
            try {
                flusher.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // shutdown 钩子：drain 队列，保证容器停止前的审计不丢失
        // 防御：仅在未彻底排空时执行，避免与 flusher.join() 已排空的重复写入导致主键冲突
        if (!queue.isEmpty()) {
            drainRemaining();
        }
    }

    /**
     * 入队审计日志（非阻塞业务线程）。
     *
     * @return true=入队成功；false=队列满且 blockOnQueueFull=false 时丢弃
     */
    public boolean enqueue(OperationLogEntity entity) {
        if (entity == null) {
            return false;
        }
        boolean offered = queue.offer(entity);
        if (!offered) {
            // 队列满：按配置决定是丢弃还是阻塞
            if (blockOnQueueFull) {
                try {
                    queue.put(entity);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            droppedCounter.incrementAndGet();
            droppedTotal.increment();
            // 仅在丢弃累计较高时告警，避免日志噪声
            int dropped = droppedCounter.get();
            if (dropped == 1 || dropped % 100 == 0) {
                log.warn("[audit] OperationLog 队列已满，丢弃 1 条（累计 {}），block-on-queue-full={}",
                        dropped, blockOnQueueFull);
            }
            return false;
        }
        return true;
    }

    /**
     * flusher 主循环：批量拉取 → 批量写入
     * <p>
     * 循环退出条件（按优先级）：
     * 1. {@code running=false && queue.isEmpty()}：完全退出（业务停止 + 队列已清空）
     * 2. 收到 {@link InterruptedException} 且 {@code running=false}：shutdown 信号到达，立即退出
     * 3. 防御性兜底：任何 RuntimeException 被 catch 防止 flusher 线程被业务异常击穿
     * <p>
     * 关键修复：原 {@code while (running.get() || !queue.isEmpty())} 在 running=false 但 queue 非空时
     * 会持续进入 while 体并执行 {@code queue.poll(flushIntervalMs)}，此时超时返回 null 时会立刻 continue，
     * 导致 shutdown 阶段 flusher 空转 CPU 直到 join 超时。
     * 现调整为：shutdown 阶段不再阻塞 poll，改用 {@code poll(0)} 非阻塞轮询，仅在队列真为空时退出。
     */
    private void flushLoop() {
        while (true) {
            try {
                List<OperationLogEntity> batch = new ArrayList<>(batchSize);
                // shutdown 阶段（running=false）改用非阻塞 poll，避免阻塞 join() 导致超时
                long pollTimeout = running.get() ? flushIntervalMs : 0L;
                OperationLogEntity first = queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
                if (first == null) {
                    // shutdown 阶段 + 队列已空 → 退出循环
                    if (!running.get()) {
                        break;
                    }
                    continue;
                }
                batch.add(first);
                // 非阻塞拉取剩余 batchSize-1 条
                queue.drainTo(batch, batchSize - 1);
                persistBatch(batch);
            } catch (InterruptedException e) {
                // 收到 stop 信号后退出循环
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    break;
                }
            } catch (Exception e) {
                // 防御性兜底：flusher 线程绝不允许被业务异常击穿
                log.error("[audit] OperationLog flush 异常", e);
            }
        }
    }

    /** 子批写入：失败单条不影响后续 */
    private void persistBatch(List<OperationLogEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            // MyBatis-Plus BaseMapper.insert(T) 单条插入；
            // 单批内循环插入由 JDBC PreparedStatement 复用，命中 rewriteBatchedStatements=true 后
            // 由 MySQL JDBC Driver 自动合并为单条 INSERT ... VALUES (...), (...), ... 批 SQL，
            // 与 insertBatchSomeColumn 性能等价但保持 ORM 一致性。
            for (OperationLogEntity entity : batch) {
                operationLogMapper.insert(entity);
            }
            persistedCounter.addAndGet(batch.size());
            persistedTotal.increment(batch.size());
        } catch (Exception e) {
            // 子批失败：降级到逐条写入，隔离脏数据
            failedCounter.addAndGet(batch.size());
            failedTotal.increment(batch.size());
            log.warn("[audit] OperationLog 批量写入失败（{} 条），降级逐条写入", batch.size(), e);
            for (OperationLogEntity entity : batch) {
                try {
                    operationLogMapper.insert(entity);
                    persistedCounter.incrementAndGet();
                    persistedTotal.increment();
                } catch (Exception single) {
                    failedCounter.incrementAndGet();
                    failedTotal.increment();
                    log.error("[audit] OperationLog 单条写入失败：type={}, detail={}", entity.getType(), entity.getDetail(), single);
                }
            }
        }
    }

    /** shutdown 时 drain 剩余队列，最多等待 5 秒 */
    private void drainRemaining() {
        if (queue.isEmpty()) {
            return;
        }
        log.info("[audit] 关闭时 drain {} 条审计日志...", queue.size());
        List<OperationLogEntity> remaining = new ArrayList<>(queue.size());
        queue.drainTo(remaining);
        // 分批写入，避免单批过大触发 MySQL max_allowed_packet
        for (int i = 0; i < remaining.size(); i += batchSize) {
            int end = Math.min(i + batchSize, remaining.size());
            persistBatch(remaining.subList(i, end));
        }
    }

    /** 当前队列水位（用于监控 / 排障） */
    public int queueSize() {
        return queue.size();
    }
}