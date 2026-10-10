package com.moyuyo.service.audit;

import com.moyuyo.dao.admin.entity.AuditLogEntity;
import com.moyuyo.dao.admin.mapper.AuditLogMapper;
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
 * 管理后台操作审计日志（mo_audit_log）异步落库器。
 * <p>
 * 与 {@link OperationLogPersister} 行为一致：
 * <ol>
 *   <li>LinkedBlockingQueue 缓存 AuditLogEntity，flusher 单线程批量写入</li>
 *   <li>队列容量由 moyuyo.admin-audit.queue-capacity 控制（默认 10000）</li>
 *   <li>子批容错：单条 / 子批失败不影响后续子批</li>
 *   <li>Prometheus 指标：moyuyo_admin_audit_queue_size / _dropped_total / _persisted_total / _failed_total</li>
 *   <li>shutdown 钩子：drain 队列，确保已有审计不丢失</li>
 * </ol>
 */
@Slf4j
@Component
// 抑制 Micrometer Gauge.builder 接收 ToDoubleFunction / Counter.builder 等泛型方法引用
// 与 JDT 静态分析对 @Nonnull 推断的差异（ToDoubleFunction<T> 中 T 推断为 raw type）
@SuppressWarnings("null")
public class AdminAuditPersister {

    private final AuditLogMapper auditLogMapper;
    private final int queueCapacity;
    private final int batchSize;
    private final long flushIntervalMs;
    private final boolean blockOnQueueFull;

    /** 待落库的审计日志队列（业务线程生产，flusher 线程消费） */
    private final LinkedBlockingQueue<AuditLogEntity> queue;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger persistedCounter = new AtomicInteger(0);
    private final AtomicInteger droppedCounter = new AtomicInteger(0);
    private final AtomicInteger failedCounter = new AtomicInteger(0);

    private Thread flusher;

    private final Counter droppedTotal;
    private final Counter persistedTotal;
    private final Counter failedTotal;

    public AdminAuditPersister(AuditLogMapper auditLogMapper,
                               MeterRegistry meterRegistry,
                               @Value("${moyuyo.admin-audit.queue-capacity:10000}") int queueCapacity,
                               @Value("${moyuyo.admin-audit.batch-size:200}") int batchSize,
                               @Value("${moyuyo.admin-audit.flush-interval-ms:1000}") long flushIntervalMs,
                               @Value("${moyuyo.admin-audit.block-on-queue-full:false}") boolean blockOnQueueFull) {
        this.auditLogMapper = auditLogMapper;
        this.queueCapacity = (queueCapacity >= 1000 && queueCapacity <= 100000) ? queueCapacity : 10000;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.flushIntervalMs = Math.max(100, flushIntervalMs);
        this.blockOnQueueFull = blockOnQueueFull;
        this.queue = new LinkedBlockingQueue<>(this.queueCapacity);

        this.droppedTotal = Counter.builder("moyuyo_admin_audit_dropped_total")
                .description("管理后台审计日志因队列满而被丢弃的次数")
                .register(meterRegistry);
        this.persistedTotal = Counter.builder("moyuyo_admin_audit_persisted_total")
                .description("管理后台审计日志成功落库的条数")
                .register(meterRegistry);
        this.failedTotal = Counter.builder("moyuyo_admin_audit_failed_total")
                .description("管理后台审计日志落库失败的条数")
                .register(meterRegistry);
        Gauge.builder("moyuyo_admin_audit_queue_size", queue, LinkedBlockingQueue::size)
                .description("当前管理后台审计日志队列水位")
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        flusher = new Thread(this::flushLoop, "moyuyo-admin-audit-flusher");
        flusher.setDaemon(true);
        flusher.setUncaughtExceptionHandler((t, e) -> {
            log.error("[admin-audit] flusher 线程异常退出，审计日志将持续堆积至队列满", e);
        });
        flusher.start();
        log.info("[admin-audit] AdminAuditPersister 启动：queueCapacity={}, batchSize={}, flushInterval={}ms, blockOnQueueFull={}",
                queueCapacity, batchSize, flushIntervalMs, blockOnQueueFull);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (flusher != null) {
            flusher.interrupt();
            try {
                flusher.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!queue.isEmpty()) {
            drainRemaining();
        }
    }

    /**
     * 入队管理后台审计日志（非阻塞业务线程）。
     *
     * @return true=入队成功；false=队列满且 blockOnQueueFull=false 时丢弃
     */
    public boolean enqueue(AuditLogEntity entity) {
        if (entity == null) {
            return false;
        }
        boolean offered = queue.offer(entity);
        if (!offered) {
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
            int dropped = droppedCounter.get();
            if (dropped == 1 || dropped % 100 == 0) {
                log.warn("[admin-audit] 队列已满，丢弃 1 条（累计 {}），block-on-queue-full={}",
                        dropped, blockOnQueueFull);
            }
            return false;
        }
        return true;
    }

    private void flushLoop() {
        while (true) {
            try {
                List<AuditLogEntity> batch = new ArrayList<>(batchSize);
                long pollTimeout = running.get() ? flushIntervalMs : 0L;
                AuditLogEntity first = queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (!running.get()) {
                        break;
                    }
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                persistBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    break;
                }
            } catch (Exception e) {
                log.error("[admin-audit] flush 异常", e);
            }
        }
    }

    /** 子批写入：失败单条不影响后续 */
    private void persistBatch(List<AuditLogEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            for (AuditLogEntity entity : batch) {
                auditLogMapper.insert(entity);
            }
            persistedCounter.addAndGet(batch.size());
            persistedTotal.increment(batch.size());
        } catch (Exception e) {
            failedCounter.addAndGet(batch.size());
            failedTotal.increment(batch.size());
            log.warn("[admin-audit] 批量写入失败（{} 条），降级逐条写入", batch.size(), e);
            for (AuditLogEntity entity : batch) {
                try {
                    auditLogMapper.insert(entity);
                    persistedCounter.incrementAndGet();
                    persistedTotal.increment();
                } catch (Exception single) {
                    failedCounter.incrementAndGet();
                    failedTotal.increment();
                    log.error("[admin-audit] 单条写入失败：action={}, module={}, detail={}",
                            entity.getAction(), entity.getModule(), entity.getDetail(), single);
                }
            }
        }
    }

    private void drainRemaining() {
        if (queue.isEmpty()) {
            return;
        }
        log.info("[admin-audit] 关闭时 drain {} 条审计日志...", queue.size());
        List<AuditLogEntity> remaining = new ArrayList<>(queue.size());
        queue.drainTo(remaining);
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
