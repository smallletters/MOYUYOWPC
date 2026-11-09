package com.moyuyo.service.logistics;

import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.LogisticsTrackProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 物流轨迹低频轮询任务
 * <p>
 * 与"打开物流详情时按需刷新"互补，作为后台兜底：
 * 没人查看物流详情的订单也能更新轨迹、并在签收后自动确认收货。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>只扫「订单状态 SHIPPED + 物流未签收 + 有运单号 + 发货在窗口期内」的候选，避免全表扫描</li>
 *   <li>复用 {@link LogisticsService#getLogisticsByOrderId}，共享拉取、落库、缓存与自动确认收货逻辑</li>
 *   <li>17TRACK 限制 3 req/s，单笔最多 2 次调用，逐笔间隔 350ms 限速，避免触发 429</li>
 *   <li>单笔失败只记日志，不中断整批；任务整体异常兜底，不影响后续调度</li>
 *   <li>provider=none（仅人工维护轨迹）时直接跳过，不产生无意义的 DB 扫描</li>
 *   <li>任务幂等：重复执行只是重复查询轨迹，签收判定走条件更新，不会错乱订单状态</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsTrackPollJob {

  private final JdbcTemplate jdbcTemplate;
  private final LogisticsService logisticsService;
  private final LogisticsTrackProvider trackProvider;
  private final StringRedisTemplate redisTemplate;

  /** 多实例防重锁：同一时刻只允许一个实例执行轮询 */
  private static final String POLL_LOCK_KEY = "logistics:poll:lock";

  /** 锁过期时间：覆盖单轮最长执行耗时，实例异常未释放时自动恢复 */
  private static final Duration POLL_LOCK_TTL = Duration.ofMinutes(30);

  /**
   * 原子释放锁：仅当锁值仍是自己写入的才删除，避免误删其他实例在 TTL 过期后重新获取的锁
   */
  private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class);

  /** 单批最大处理运单数（防止一次拉取撑爆第三方配额） */
  @Value("${moyuyo.logistics.poll-batch-size:100}")
  private int batchSize;

  /** 只轮询最近 N 天内发货的运单，超期运单交由其它兜底逻辑处理 */
  @Value("${moyuyo.logistics.poll-window-days:30}")
  private int windowDays;

  /**
   * 逐笔限速：单笔最多 2 次 17TRACK 调用，350ms 间隔约 2.8 req/s，低于平台 3 req/s 限制
   */
  private static final long THROTTLE_MS = 350;

  /**
   * 按 poll-interval-minutes（默认 60 分钟）固定间隔执行：
   * fixedDelay 从上一轮结束后开始计时，避免上一轮未跑完时任务叠加。
   * 启动后延迟 60 秒再首次执行，错开应用启动期的其它初始化任务。
   */
  @Scheduled(
      fixedDelayString = "#{${moyuyo.logistics.poll-interval-minutes:60} * 60000}",
      initialDelayString = "${moyuyo.logistics.poll-initial-delay-ms:60000}")
  public void pollTracks() {
    // 未启用第三方轨迹 API 时无需轮询
    if ("none".equalsIgnoreCase(trackProvider.getProviderName())) {
      return;
    }

    // 多实例部署：同一时刻只允许一个实例执行轮询，避免重复消耗第三方配额。
    // Redis 不可用时按单实例模式继续执行（fail-open），不影响核心业务。
    String lockValue = UUID.randomUUID().toString();
    if (!tryLock(lockValue)) {
      log.info("[logistics-poll] 其他实例正在执行轮询，跳过本轮");
      return;
    }

    try {
      List<Long> orderIds = findPendingOrderIds();
      if (orderIds.isEmpty()) {
        log.info("[logistics-poll] 无待更新运单，跳过本轮");
        return;
      }
      log.info("[logistics-poll] 扫描到 {} 笔未签收运单，开始拉取轨迹（provider={}）",
          orderIds.size(), trackProvider.getProviderName());

      int success = 0;
      int failed = 0;
      boolean interrupted = false;
      for (Long orderId : orderIds) {
        // 应用停机/任务被中断时立即停止，避免继续消耗第三方配额
        if (Thread.currentThread().isInterrupted()) {
          interrupted = true;
          break;
        }
        try {
          // 复用按需查询逻辑：拉取轨迹 + 落库 + 写缓存 + 签收自动确认收货
          logisticsService.getLogisticsByOrderId(orderId);
          success++;
        } catch (Exception e) {
          failed++;
          log.warn("[logistics-poll] 拉取失败 orderId={}, reason={}", orderId, e.getMessage());
        }
        sleepQuietly();
      }
      log.info("[logistics-poll] 本轮完成：处理 {} 笔（失败 {} 笔）", success, failed);

      // 任务被中断（如应用停机）时剩余运单下轮再处理
      if (interrupted) {
        log.warn("[logistics-poll] 任务被中断，提前结束本轮，剩余运单下轮继续");
      }
    } catch (Exception e) {
      log.error("[logistics-poll] 定时任务异常", e);
    } finally {
      releaseLock(lockValue);
    }
  }

  /**
   * 尝试获取分布式锁
   * <p>
   * Redis 异常时返回 true（无锁继续执行），避免因 Redis 故障导致轮询完全停摆。
   */
  private boolean tryLock(String lockValue) {
    try {
      return Boolean.TRUE.equals(redisTemplate.opsForValue()
          .setIfAbsent(POLL_LOCK_KEY, lockValue, POLL_LOCK_TTL));
    } catch (Exception e) {
      log.warn("[logistics-poll] Redis 分布式锁不可用，按单实例模式继续：{}", e.getMessage());
      return true;
    }
  }

  /**
   * 释放锁：通过 Lua 脚本原子校验并删除，避免 get + delete 之间的竞态误删他人锁
   */
  private void releaseLock(String lockValue) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(POLL_LOCK_KEY), lockValue);
    } catch (Exception e) {
      log.warn("[logistics-poll] 释放分布式锁失败（将随 TTL 自动释放）：{}", e.getMessage());
    }
  }

  /**
   * 查询候选订单：已发货但物流未签收、有运单号，且发货时间在窗口期内
   * <p>
   * 按随机顺序取批：若按发货时间升序，积压的丢件/异常运单会长期占满批次，
   * 导致新运单永远排不进来（饿死）；随机顺序保证每个候选都有机会被刷新。
   * 候选集已被状态与时间窗口过滤，RAND() 的排序开销可控。
   */
  private List<Long> findPendingOrderIds() {
    LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, windowDays));
    String sql = """
        SELECT l.order_id FROM mo_logistics l
        JOIN mo_order o ON o.id = l.order_id
        WHERE o.status = 'SHIPPED'
          AND o.delete_status = 0
          AND l.received_at IS NULL
          AND l.tracking_number IS NOT NULL AND l.tracking_number <> ''
          AND l.shipped_at IS NOT NULL AND l.shipped_at >= ?
        ORDER BY RAND()
        LIMIT ?
        """;
    return jdbcTemplate.queryForList(sql, Long.class, since, Math.max(1, batchSize));
  }

  /**
   * 限速等待；中断时恢复中断标记，由上层循环感知后退出
   */
  private void sleepQuietly() {
    try {
      Thread.sleep(THROTTLE_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
