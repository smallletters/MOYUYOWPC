package com.moyuyo.service.dm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 私信 / 客服消息保留期清理任务
 * <p>
 * 云端只保留固定天数（默认 30 天）的消息明细，超期数据每天凌晨 04:30 分批删除。
 * APP 端在本地 IndexedDB 保留同一周期的缓存，因此云端清理后用户仍可查看近 30 天记录。
 * <p>
 * 清理后的数据修正：
 * 1. 单聊未读数按剩余未读消息精确重算
 * 2. 群聊未读无逐条已读标记，收敛为不超过剩余消息数
 * 3. 最后一条消息已被清理的会话，清空摘要与未读，避免云端残留过期内容
 * <p>
 * 由 moyuyo.message.retention-enabled 控制开关（生产默认 true，dev 默认 false）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moyuyo.message.retention-enabled", havingValue = "true", matchIfMissing = false)
public class MessageRetentionJob {

  private final JdbcTemplate jdbcTemplate;
  private final StringRedisTemplate redisTemplate;

  /** 多实例防重锁：同一时刻只允许一个实例执行清理 */
  private static final String LOCK_KEY = "message:retention:lock";

  /** 锁过期时间：覆盖单轮最长执行耗时，实例异常未释放时自动恢复 */
  private static final Duration LOCK_TTL = Duration.ofHours(2);

  /** 原子释放锁：仅当锁值仍是自己写入的才删除，避免误删其他实例重新获取的锁 */
  private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class);

  @Value("${moyuyo.message.retention-days:30}")
  private int retentionDays;

  @Value("${moyuyo.message.retention-batch-size:1000}")
  private int batchSize;

  // 单次任务最大批次数：数据量大时由下次 cron 继续删除，避免长事务阻塞其他定时任务
  @Value("${moyuyo.message.retention-max-batches:100}")
  private int maxBatches;

  @Scheduled(cron = "${moyuyo.message.retention-cron:0 30 4 * * *}")
  public void purgeExpiredMessages() {
    if (retentionDays < 1) {
      log.warn("[message] retention-days={} 非法，跳过清理", retentionDays);
      return;
    }
    // 多实例部署：同一时刻只允许一个实例执行，避免重复 DELETE 与重复重算
    String lockValue = UUID.randomUUID().toString();
    if (!tryLock(lockValue)) {
      log.info("[message] 其他实例正在清理消息，跳过本轮");
      return;
    }
    LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
    long start = System.currentTimeMillis();
    try {
      // 1. 先记录受影响的会话：这些会话有消息被删，清理后需要重算未读数/消息数
      List<Long> affected = jdbcTemplate.queryForList(
          "SELECT DISTINCT conversation_id FROM mo_dm_message WHERE create_time < ?",
          Long.class, cutoff);
      List<Long> affectedCs = jdbcTemplate.queryForList(
          "SELECT DISTINCT session_id FROM mo_cs_message WHERE create_time < ?",
          Long.class, cutoff);

      int dmDeleted = purgeTable("mo_dm_message", cutoff);
      // 用户级删除标记属于消息附属数据，按创建时间同步清理
      int dmFlagsDeleted = purgeTable("mo_dm_message_delete", cutoff);
      int csDeleted = purgeTable("mo_cs_message", cutoff);

      // 2. 修正受影响会话的未读，避免已删消息继续占用角标
      resetUnread(affected);
      // 后台客服会话列表展示 message_count，按剩余消息重算保持一致
      recalcCsMessageCount(affectedCs);

      // 3. 最后一条消息已被清理的会话：摘要/未读一并清空
      int clearedConv = jdbcTemplate.update(
          "UPDATE mo_dm_conversation SET last_message_id = NULL, last_message_content = NULL,"
              + " last_message_type = NULL, last_message_at = NULL, unread_a = 0, unread_b = 0"
              + " WHERE last_message_at IS NOT NULL AND last_message_at < ?", cutoff);
      // 客服会话无内容摘要字段，仅清除最后消息时间，overview 查不到消息后卡片自然消失
      int clearedCs = jdbcTemplate.update(
          "UPDATE mo_cs_session SET last_message_at = NULL"
              + " WHERE last_message_at IS NOT NULL AND last_message_at < ?", cutoff);

      log.info("[message] 清理 {} 天前消息完成：dm={}, dmFlags={}, cs={}, 受影响会话={},"
              + " 清空会话摘要={}, 清空客服时间={}, cost={}ms",
          retentionDays, dmDeleted, dmFlagsDeleted, csDeleted, affected.size(), clearedConv,
          clearedCs, System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("[message] 清理过期消息异常：cutoff={}", cutoff, e);
    } finally {
      releaseLock(lockValue);
    }
  }

  /**
   * 尝试获取分布式锁
   * <p>
   * Redis 异常时返回 true（无锁继续执行），避免 Redis 故障导致清理彻底停摆。
   */
  private boolean tryLock(String lockValue) {
    try {
      return Boolean.TRUE.equals(
          redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL));
    } catch (Exception e) {
      log.warn("[message] Redis 分布式锁不可用，按单实例模式继续：{}", e.getMessage());
      return true;
    }
  }

  /** 释放锁：Lua 脚本原子校验并删除，避免竞态误删其他实例的锁 */
  private void releaseLock(String lockValue) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(LOCK_KEY), lockValue);
    } catch (Exception e) {
      log.warn("[message] 释放分布式锁失败（将随 TTL 自动释放）：{}", e.getMessage());
    }
  }

  /** 分批删除，避免大表长事务锁表；达到最大批次数后由下次 cron 续删 */
  private int purgeTable(String table, LocalDateTime cutoff) {
    int total = 0;
    int limit = Math.max(1, batchSize);
    int batches = 0;
    while (batches < Math.max(1, maxBatches)) {
      int deleted = jdbcTemplate.update(
          "DELETE FROM " + table + " WHERE create_time < ? LIMIT " + limit, cutoff);
      total += deleted;
      batches++;
      if (deleted < limit) {
        break;
      }
    }
    return total;
  }

  /**
   * 重算受影响会话的未读数：
   * - 单聊：按剩余未读消息精确重算
   * - 群聊：成员未读是计数字段且无逐条已读标记，只能收敛到不超过剩余消息数
   */
  private void resetUnread(List<Long> conversationIds) {
    if (conversationIds == null || conversationIds.isEmpty()) {
      return;
    }
    int chunkSize = 500;
    for (int start = 0; start < conversationIds.size(); start += chunkSize) {
      List<Long> batch = conversationIds.subList(
          start, Math.min(start + chunkSize, conversationIds.size()));
      String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
      Object[] args = batch.toArray();
      jdbcTemplate.update(
          "UPDATE mo_dm_conversation c SET"
              + " unread_a = (SELECT COUNT(*) FROM mo_dm_message m"
              + "   WHERE m.conversation_id = c.id AND m.receiver_id = c.user_a AND m.read_flag = 0),"
              + " unread_b = (SELECT COUNT(*) FROM mo_dm_message m"
              + "   WHERE m.conversation_id = c.id AND m.receiver_id = c.user_b AND m.read_flag = 0)"
              + " WHERE c.type = 'SINGLE' AND c.id IN (" + placeholders + ")", args);
      jdbcTemplate.update(
          "UPDATE mo_dm_group_member gm SET gm.unread = LEAST(gm.unread,"
              + " (SELECT COUNT(*) FROM mo_dm_message m WHERE m.conversation_id = gm.conversation_id))"
              + " WHERE gm.conversation_id IN (" + placeholders + ")", args);
    }
  }

  /** 重算客服会话消息数，避免后台列表的 message_count 与实际消息不一致 */
  private void recalcCsMessageCount(List<Long> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) {
      return;
    }
    int chunkSize = 500;
    for (int start = 0; start < sessionIds.size(); start += chunkSize) {
      List<Long> batch = sessionIds.subList(
          start, Math.min(start + chunkSize, sessionIds.size()));
      String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
      jdbcTemplate.update(
          "UPDATE mo_cs_session s SET s.message_count ="
              + " (SELECT COUNT(*) FROM mo_cs_message m WHERE m.session_id = s.id)"
              + " WHERE s.id IN (" + placeholders + ")", batch.toArray());
    }
  }
}
