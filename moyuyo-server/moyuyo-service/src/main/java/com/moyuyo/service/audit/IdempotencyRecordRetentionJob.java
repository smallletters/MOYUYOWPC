package com.moyuyo.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 幂等记录清理任务
 * <p>
 * 每天凌晨 04:00 删除超过保留天数（默认 30 天）的 mo_idempotency_record 历史记录，
 * 与 OperationLogRetentionJob（03:30）错峰 30 分钟，避免 I/O 争抢。
 * <p>
 * 设计依据：幂等记录的目的是防止并发 / 重试导致的重复发货；30 天足够覆盖任何正常订单生命周期
 * （订单超时默认 30 分钟取消，超时发货 72 小时下单，回正常补发运单不超过一周）。
 * 30 天之后即使出现极罕见的重复发货，业务上也可接受人工核查。
 * <p>
 * 由 moyuyo.idempotency.retention-enabled 控制总开关（prod 默认 true，dev 默认 false）。
 * 注意：必须保留至少 7 天（订单退款 / 售后窗口的合理上限），小于 7 天拒绝清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moyuyo.idempotency.retention-enabled", havingValue = "true", matchIfMissing = false)
public class IdempotencyRecordRetentionJob {

  private final JdbcTemplate jdbcTemplate;

  @Value("${moyuyo.idempotency.retention-days:30}")
  private int retentionDays;

  @Value("${moyuyo.idempotency.retention-batch-size:5000}")
  private int batchSize;

  // 单次任务最大批次数：避免大表场景下任务耗分钟级阻塞后续定时任务。
  // 超过时由下一个 cron 周期续删（cutoff 时间不变），不会丢数据。
  @Value("${moyuyo.idempotency.retention-max-batches:100}")
  private int maxBatches;

  /**
   * 每天 04:00 执行清理。
   * 注意：mo_idempotency_record 表目前有 idx_created_at 索引（V20260929_05），清理走"索引范围扫描"。
   * 大表场景可加 LIMIT 批次删除，避免长事务。byV 分批用 lt + LIMIT 控制单次删除量。
   */
  @Scheduled(cron = "${moyuyo.idempotency.retention-cron:0 0 4 * * *}")
  public void purgeExpiredRecords() {
    if (retentionDays < 7) {
      // 防御：拒绝删除 7 天内的幂等记录（订单售后窗口需要兜底）
      log.warn("[idempotency] retention-days={} 小于 7 天，跳过清理（避免误删近端幂等）", retentionDays);
      return;
    }
    LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
    long start = System.currentTimeMillis();
    int totalDeleted = 0;
    int batchCount = 0;
    boolean truncated = false;
    try {
      // 分批删除：每次最多 batchSize 条，循环上限 maxBatches 批。
      // 超出 maxBatches 时由下次 cron 续删（cutoff 不变），不会丢数据。
      while (batchCount < Math.max(1, maxBatches)) {
        int deleted = jdbcTemplate.update(
            "DELETE FROM mo_idempotency_record WHERE created_at < ? LIMIT " + Math.max(1, batchSize),
            cutoff);
        totalDeleted += deleted;
        batchCount++;
        if (deleted < batchSize) {
          // 本批未满 → 没有更多记录可删
          break;
        }
        if (batchCount >= maxBatches) {
          truncated = true; // 已达上限，下次 cron 续删
        }
      }
      long cost = System.currentTimeMillis() - start;
      if (truncated) {
        log.warn("[idempotency] 清理 {} 天前幂等记录：已删 {} 条（达 maxBatches={} 上限，下个 cron 续删）, cutoff={}, cost={}ms",
            retentionDays, totalDeleted, maxBatches, cutoff, cost);
      } else {
        log.info("[idempotency] 清理 {} 天前幂等记录：cutoff={}, deleted={} 条, cost={}ms",
            retentionDays, cutoff, totalDeleted, cost);
      }
    } catch (Exception e) {
      log.error("[idempotency] 清理过期幂等记录异常：cutoff={}", cutoff, e);
    }
  }
}