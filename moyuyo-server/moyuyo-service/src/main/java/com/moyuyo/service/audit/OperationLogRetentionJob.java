package com.moyuyo.service.audit;

import com.moyuyo.dao.admin.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作审计日志保留期清理任务
 * <p>
 * 每天凌晨 03:30 删除超过保留天数（默认 90 天）的历史审计日志，
 * 与数据库备份窗口（03:00）错峰 30 分钟，避免 I/O 争抢。
 * <p>
 * 由 moyuyo.audit.retention-enabled 控制总开关（prod 默认 true，dev 默认 false）。
 * 注意：mo_operation_log 表目前未分区（V20260804_03 仅有索引），清理走 DELETE WHERE。
 * 如未来走分区表，可改为 DROP PARTITION（O(1)）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moyuyo.audit.retention-enabled", havingValue = "true", matchIfMissing = false)
public class OperationLogRetentionJob {

    private final OperationLogMapper operationLogMapper;

    @Value("${moyuyo.audit.retention-days:90}")
    private int retentionDays;

    /**
     * 每天 03:30 执行清理。cron 由 spring.task.scheduling.pool.size 控制线程池。
     * 注意：@Scheduled 与 spring.task.scheduling.shutdown.await-termination 配合优雅停机，
     * 容器 stop 时允许当前清理完成后再退出（避免长 DELETE 被 SIGKILL 中断）。
     */
    @Scheduled(cron = "${moyuyo.audit.retention-cron:0 30 3 * * *}")
    public void purgeExpiredLogs() {
        if (retentionDays < 7) {
            // 防御：拒绝删除 7 天内的审计（合规审计通常要求 ≥ 30 天）
            log.warn("[audit] retention-days={} 小于 7 天，跳过清理（避免误删近端审计）", retentionDays);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long start = System.currentTimeMillis();
        try {
            int deleted = operationLogMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.moyuyo.dao.admin.entity.OperationLogEntity>()
                            .lt("create_time", cutoff));
            long cost = System.currentTimeMillis() - start;
            log.info("[audit] 清理 {} 天前审计日志：cutoff={}, deleted={} 条, cost={}ms",
                    retentionDays, cutoff, deleted, cost);
        } catch (Exception e) {
            log.error("[audit] 清理过期审计日志异常：cutoff={}", cutoff, e);
        }
    }
}