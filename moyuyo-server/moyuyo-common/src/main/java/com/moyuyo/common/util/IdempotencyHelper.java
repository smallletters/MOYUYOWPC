package com.moyuyo.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 幂等键生成工具：在前端/服务端生成稳定可追溯的幂等键字符串。
 *
 * 用法：
 *   String key = IdempotencyHelper.shipFingerprint(orderId, carrier, trackingNo);
 *   后续 INSERT mo_idempotency_record (scope, biz_id, idem_key) 时若重复则跳过。
 *
 * 设计原则：
 *   - 同一笔订单同一承运商同一运单号 → 同一指纹 → 同一次"发货"语义 → 幂等命中
 *   - 同一笔订单更换运单号 → 不同指纹 → 不命中，按"补打运单"处理
 *   - 不同订单 → 不同指纹
 *   - 字段拼接顺序固定，确保 SHA-256 输入一致
 *
 * 注意：shipOrder 与 batchShip 必须共用同一前缀，
 * 否则运营从打印页触发 shipOrder 后，再走批量发货接口对同一 (order, carrier, trackingNo)
 * 重新发货会因 scope 不同 (business-check) / 幂等键不同而不命中幂等表，
 * 导致重复发货。统一用 "ship:" 前缀。
 */
public final class IdempotencyHelper {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyHelper.class);

  private IdempotencyHelper() {}

  /**
   * 生成"对订单发货"的幂等指纹：(orderId, carrier, trackingNo) 的 SHA-256 hex 前 32 字符。
   * <p>
   * shipOrder 与 batchShip 都必须调用此方法，保证：
   *   同一笔订单同一承运商同一运单号 → 同一指纹 → 幂等命中。
   * <p>
   * 数据库侧使用统一 scope='ship_order'。
   */
  public static String shipFingerprint(Long orderId, String carrier, String trackingNo) {
    return fingerprint("ship:", orderId, nullSafe(carrier), nullSafe(trackingNo));
  }

  private static String fingerprint(String prefix, Object... parts) {
    try {
        StringBuilder raw = new StringBuilder(prefix);
        for (Object p : parts) {
          raw.append('|').append(p);
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // 取前 16 字节（32 hex 字符），足够避免碰撞又便于索引
        return HexFormat.of().formatHex(digest).substring(0, 32);
      } catch (Exception e) {
        // SHA-256 在标准 JDK 永远可用；走到这里说明环境异常，降级为时间戳随机串。
        log.warn("生成幂等指纹失败，降级使用时间戳：{}", e.getMessage());
        return "fallback-" + System.nanoTime();
      }
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }
}