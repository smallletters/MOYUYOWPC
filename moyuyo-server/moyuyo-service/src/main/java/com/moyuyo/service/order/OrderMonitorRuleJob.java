package com.moyuyo.service.order;

import com.moyuyo.service.OrderService;
import com.moyuyo.service.admin.AdminOrderOpsService;
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
import java.util.Map;
import java.util.UUID;

/**
 * 订单监控规则执行任务
 * <p>
 * 读取 mo_order_monitor_rule 中启用且配置了结构化条件（scene + 阈值）的规则，
 * 按场景扫描命中订单并执行动作（FLAG 标记 / INTERCEPT 拦截 / CANCEL 取消）。
 * <p>
 * 设计要点：
 * 1) 幂等：mo_order_monitor_log 的 (rule_id, order_id) 唯一键 + INSERT IGNORE 抢占，
 *    保证同一规则对同一订单只执行一次，避免每轮扫描重复取消/拦截/追加备注。
 * 2) 多实例：Redis 分布式锁保证同一时刻只有一个节点执行；Redis 不可用时按单实例放行（与
 *    LogisticsTrackPollJob 一致），避免任务整体停摆。
 * 3) 隔离：单条规则、单个订单执行失败只记录日志，不影响其它规则与订单，下一轮继续。
 * 4) 开关：moyuyo.order.monitor.enabled 可整体关闭；每条规则自身还有 enabled 开关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMonitorRuleJob {

  private final JdbcTemplate jdbcTemplate;
  private final OrderService orderService;
  private final AdminOrderOpsService adminOrderOpsService;
  private final StringRedisTemplate redisTemplate;

  /** 多实例防重锁 */
  private static final String LOCK_KEY = "order:monitor:rule:lock";

  /** 锁过期时间：需覆盖单轮最长执行耗时 */
  private static final Duration LOCK_TTL = Duration.ofMinutes(10);

  /** 原子释放锁：仅删除自己写入的锁，避免误删其它实例的锁 */
  private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class);

  /** 场景与动作兼容性白名单：兼容历史脏数据，避免如"已支付订单被自动取消"这类误操作 */
  private static final Map<String, java.util.Set<String>> SCENE_ACTIONS = Map.of(
      "TIMEOUT_PAY", java.util.Set.of("FLAG", "INTERCEPT", "CANCEL"),
      "TIMEOUT_SHIP", java.util.Set.of("FLAG", "INTERCEPT"),
      "LOGISTICS_STALL", java.util.Set.of("FLAG"),
      "AMOUNT_DEVIATION", java.util.Set.of("FLAG", "INTERCEPT"));

  /** 总开关：默认开启，可通过 moyuyo.order.monitor.enabled=false 关闭 */
  @Value("${moyuyo.order.monitor.enabled:true}")
  private boolean enabled;

  /** 单条规则单轮最多处理的订单数，防止大表全扫 */
  @Value("${moyuyo.order.monitor.batch-size:100}")
  private int batchSize;

  /** 执行失败后的最大重试次数：避免因一次瞬时异常永久放弃该订单 */
  @Value("${moyuyo.order.monitor.max-retry:2}")
  private int maxRetry;

  /** 金额偏差场景的扫描窗口（天），避免对历史订单做全表计算 */
  @Value("${moyuyo.order.monitor.scan-window-days:30}")
  private int scanWindowDays;

  /**
   * 默认每 5 分钟执行一轮（fixedDelay：上一轮结束后再计时，避免任务叠加）。
   */
  @Scheduled(fixedDelayString = "${moyuyo.order.monitor.fixed-delay-ms:300000}", initialDelay = 60000)
  public void executeRules() {
    if (!enabled) {
      return;
    }
    String lockValue = UUID.randomUUID().toString();
    if (!tryLock(lockValue)) {
      return;
    }
    try {
      // 恢复上次执行中断（进程崩溃/重启）遗留的 PENDING 记录，转为 FAILED 走重试逻辑，
      // 否则这些订单会被"已执行"过滤条件永久跳过
      int recovered = jdbcTemplate.update(
          "UPDATE mo_order_monitor_log SET result = 'FAILED', retry_count = retry_count + 1, message = '执行中断，待重试' " +
          "WHERE result = 'PENDING' AND create_time < DATE_SUB(NOW(), INTERVAL 10 MINUTE)");
      if (recovered > 0) {
        log.warn("[order-monitor] 恢复 {} 条中断的执行记录，等待重试", recovered);
      }

      List<Map<String, Object>> rules = jdbcTemplate.queryForList(
          "SELECT id, name, scene, threshold_hours, threshold_percent, action_type, version " +
          "FROM mo_order_monitor_rule WHERE enabled = 1 ORDER BY priority ASC, id ASC");
      if (rules.isEmpty()) {
        return;
      }
      for (Map<String, Object> rule : rules) {
        try {
          executeRule(rule);
        } catch (Exception e) {
          log.error("[order-monitor] 规则执行异常 ruleId={}", rule.get("id"), e);
        }
      }
    } catch (Exception e) {
      log.error("[order-monitor] 定时任务异常", e);
    } finally {
      releaseLock(lockValue);
    }
  }

  /**
   * 执行单条规则：查候选订单 → 抢占执行权 → 执行动作 → 落执行结果。
   */
  private void executeRule(Map<String, Object> rule) {
    long ruleId = toLong(rule.get("id"), 0L);
    int ruleVersion = toInt(rule.get("version"), 0);
    String ruleName = rule.get("name") == null ? "" : rule.get("name").toString();
    String scene = rule.get("scene") == null ? "" : rule.get("scene").toString();
    String action = rule.get("action_type") == null ? "FLAG" : rule.get("action_type").toString();

    java.util.Set<String> allowedActions = SCENE_ACTIONS.get(scene);
    if (allowedActions == null) {
      // 未配置场景的历史规则不执行
      return;
    }
    if (!allowedActions.contains(action)) {
      log.warn("[order-monitor] 规则[{}]场景 {} 不支持动作 {}，已跳过", ruleName, scene, action);
      return;
    }

    List<Map<String, Object>> orders = findCandidates(scene, rule);
    if (orders.isEmpty()) {
      return;
    }
    log.info("[order-monitor] 规则[{}]命中 {} 笔订单，动作={}", ruleName, orders.size(), action);

    for (Map<String, Object> order : orders) {
      long orderId = toLong(order.get("id"), 0L);
      if (orderId <= 0) {
        continue;
      }
      String orderNo = order.get("order_no") == null ? null : order.get("order_no").toString();
      // 幂等抢占。注意不能依赖 INSERT ... ON DUPLICATE KEY UPDATE 的 affected-rows：
      //   MySQL Connector/J 默认 CLIENT_FOUND_ROWS，未变更的重复行也会返回 1，
      //   会把"已执行"误判为"抢占成功"导致重复执行。
      //   步骤 1：首次执行 INSERT IGNORE 成功（返回 1）
      //   步骤 2：上次执行失败且未达重试上限时，条件 UPDATE 抢占（返回 1/2）
      boolean claimed = jdbcTemplate.update(
          "INSERT IGNORE INTO mo_order_monitor_log" +
          "(rule_id, rule_version, order_id, order_no, scene, action_type, result, retry_count, create_time) " +
          "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, NOW())",
          ruleId, ruleVersion, orderId, orderNo, scene, action) > 0;
      if (!claimed) {
        claimed = jdbcTemplate.update(
            "UPDATE mo_order_monitor_log SET result = 'PENDING', message = NULL " +
            "WHERE rule_id = ? AND rule_version = ? AND order_id = ? " +
            "AND result = 'FAILED' AND retry_count < ?",
            ruleId, ruleVersion, orderId, maxRetry) > 0;
      }
      if (!claimed) {
        continue;
      }
      try {
        applyAction(action, orderId, ruleName);
        jdbcTemplate.update(
            "UPDATE mo_order_monitor_log SET result = 'SUCCESS', message = NULL " +
            "WHERE rule_id = ? AND rule_version = ? AND order_id = ?",
            ruleId, ruleVersion, orderId);
        log.info("[order-monitor] 规则[{}]执行成功 orderId={}, action={}", ruleName, orderId, action);
      } catch (Exception e) {
        jdbcTemplate.update(
            "UPDATE mo_order_monitor_log SET result = 'FAILED', retry_count = retry_count + 1, message = ? " +
            "WHERE rule_id = ? AND rule_version = ? AND order_id = ?",
            truncate(e.getMessage()), ruleId, ruleVersion, orderId);
        log.warn("[order-monitor] 规则[{}]执行失败 orderId={}, reason={}", ruleName, orderId, e.getMessage());
      }
    }
  }

  /**
   * 按场景查询候选订单（只取 id / order_no，避免大字段）。
   * <p>
   * 未配置 scene 的历史规则不执行，避免误操作。
   * <p>
   * 关键：排除该规则已处理过的订单。FLAG 类动作不改变订单状态，若不过滤，
   * 已处理的订单会一直占据 LIMIT 窗口，导致后续新订单永远扫不到（饥饿）。
   */
  private List<Map<String, Object>> findCandidates(String scene, Map<String, Object> rule) {
    long ruleId = toLong(rule.get("id"), 0L);
    int ruleVersion = toInt(rule.get("version"), 0);
    String action = rule.get("action_type") == null ? "FLAG" : rule.get("action_type").toString();
    int limit = Math.max(1, batchSize);
    int hours = Math.max(1, toInt(rule.get("threshold_hours"), 24));
    int percent = Math.min(100, Math.max(1, toInt(rule.get("threshold_percent"), 50)));
    LocalDateTime now = LocalDateTime.now();
    // 扫描窗口下界：mo_order 按 create_time 分区，限定窗口可走分区裁剪 + idx_create_time，
    // 避免每轮对历史全表做扫描
    LocalDateTime windowStart = now.minusDays(Math.max(1, scanWindowDays));
    // 已执行过滤：同一规则同一版本同一订单只处理一次；FAILED 且未达重试上限的允许重试
    String notProcessed = "AND NOT EXISTS (SELECT 1 FROM mo_order_monitor_log l " +
        "WHERE l.rule_id = ? AND l.rule_version = ? AND l.order_id = o.id " +
        "AND (l.result <> 'FAILED' OR l.retry_count >= ?)) ";

    return switch (scene) {
      case "TIMEOUT_PAY" -> jdbcTemplate.queryForList(
          "SELECT o.id, o.order_no FROM mo_order o " +
          "WHERE o.status = 'PENDING_PAY' AND o.delete_status = 0 " +
          "AND o.create_time < ? AND o.create_time >= ? " +
          notProcessed +
          "ORDER BY o.create_time ASC LIMIT ?",
          now.minusHours(hours), windowStart, ruleId, ruleVersion, maxRetry, limit);
      case "TIMEOUT_SHIP" -> jdbcTemplate.queryForList(
          "SELECT o.id, o.order_no FROM mo_order o " +
          "WHERE o.status = 'PENDING_SHIP' AND o.delete_status = 0 " +
          "AND COALESCE(o.paid_at, o.create_time) < ? AND o.create_time >= ? " +
          notProcessed +
          "ORDER BY COALESCE(o.paid_at, o.create_time) ASC LIMIT ?",
          now.minusHours(hours), windowStart, ruleId, ruleVersion, maxRetry, limit);
      case "LOGISTICS_STALL" -> jdbcTemplate.queryForList(
          "SELECT o.id, o.order_no FROM mo_order o " +
          "JOIN mo_logistics l ON l.order_id = o.id " +
          "WHERE o.status = 'SHIPPED' AND o.delete_status = 0 " +
          "AND l.received_at IS NULL " +
          "AND COALESCE(l.last_track_time, l.shipped_at) < ? " +
          notProcessed +
          "GROUP BY o.id, o.order_no ORDER BY MIN(COALESCE(l.last_track_time, l.shipped_at)) ASC LIMIT ?",
          now.minusHours(hours), ruleId, ruleVersion, maxRetry, limit);
      case "AMOUNT_DEVIATION" -> {
        // 拦截动作只允许在"未发货"订单上执行：HOLD 是发货前中间态，
        // 对已发货/已签收订单置 HOLD 会破坏状态机
        String statusFilter = "INTERCEPT".equals(action)
            ? "AND o.status IN ('PAID', 'PENDING_SHIP') "
            : "AND o.status IN ('PAID', 'PENDING_SHIP', 'SHIPPED', 'RECEIVED', 'COMPLETED') ";
        yield jdbcTemplate.queryForList(
            "SELECT o.id, o.order_no FROM mo_order o " +
            "WHERE o.delete_status = 0 " + statusFilter +
            "AND o.goods_amount > 0 AND o.create_time >= ? " +
            "AND ABS(o.pay_amount - o.goods_amount) * 100 > o.goods_amount * ? " +
            notProcessed +
            "ORDER BY o.create_time DESC LIMIT ?",
            now.minusDays(Math.max(1, scanWindowDays)), percent, ruleId, ruleVersion, maxRetry, limit);
      }
      default -> List.of();
    };
  }

  /**
   * 执行具体动作。
   */
  private void applyAction(String action, long orderId, String ruleName) {
    switch (action) {
      case "CANCEL" -> orderService.cancelOrder(orderId, null, "监控规则自动取消：" + ruleName);
      case "INTERCEPT" -> {
        // 已存在生效中的拦截记录时跳过，避免重复插入拦截单
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mo_order_intercept WHERE order_id = ? AND status = 'ACTIVE'",
            Integer.class, orderId);
        if (active != null && active > 0) {
          return;
        }
        adminOrderOpsService.createIntercept(orderId, "SYSTEM", "监控规则自动拦截：" + ruleName, null, "系统");
      }
      case "FLAG" -> {
        // 标记提醒：写入内部字段 delivery_note（发货备注），不污染买家下单时填写的 remark，
        // 同时执行结果记录在 mo_order_monitor_log 供规则页查看
        jdbcTemplate.update(
            "UPDATE mo_order SET delivery_note = LEFT(CONCAT(COALESCE(delivery_note, ''), ?), 255), " +
            "update_time = NOW() WHERE id = ?",
            "[监控提醒] " + ruleName + "；", orderId);
      }
      default -> throw new IllegalArgumentException("不支持的动作类型: " + action);
    }
  }

  /**
   * 尝试获取分布式锁；Redis 异常时返回 true（按单实例模式继续执行）。
   */
  private boolean tryLock(String lockValue) {
    try {
      return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL));
    } catch (Exception e) {
      log.warn("[order-monitor] Redis 分布式锁不可用，按单实例模式继续：{}", e.getMessage());
      return true;
    }
  }

  /**
   * 原子释放锁。
   */
  private void releaseLock(String lockValue) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(LOCK_KEY), lockValue);
    } catch (Exception e) {
      log.warn("[order-monitor] 释放分布式锁失败（将随 TTL 自动释放）：{}", e.getMessage());
    }
  }

  private static long toLong(Object value, long defaultValue) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return value == null ? defaultValue : Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static int toInt(Object value, int defaultValue) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return value == null ? defaultValue : Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
