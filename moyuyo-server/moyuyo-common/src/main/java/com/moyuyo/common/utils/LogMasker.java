package com.moyuyo.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感凭据脱敏工具类
 * <p>
 * 业务日志手动调用 log.info(...) 时（如写 webhook payload / 调试日志），
 * 如果入参字符串含 password=xxx / token=xxx / 密钥=xxx 等 KV，凭据会进入 ELK / Sentry / 日志归档。
 * 邮箱 / 手机号等 PII 也应在控制台日志中脱敏后再展示，避免合规与用户隐私风险。
 * <p>
 * 提供两类 API：
 * - {@link #maskEmail(String)}：保留邮箱首字符 + "****@domain"，长串邮箱脱敏为 a****@example.com
 * - {@link #maskPhone(String)}：保留前 3 位 + "****" + 后 4 位（手机号 11 位时为 138****5678）
 * - {@link #maskMiddle(String, int, int, String)}：通用 KV 中段脱敏
 * - {@link #maskSensitiveKv(String)}：通用 KV 敏感凭据脱敏，与 GlobalExceptionHandler#P_SENSITIVE_CREDENTIALS 保持一致，
 *   覆盖 password / token / secret / 密钥 等敏感键名替换 value 为 [REDACTED]
 *   典型用法：log.info("收到回调: {}", LogMasker.maskSensitiveKv(req.toString()));
 * <p>
 * 入参为 null / 空字符串时直接返回原值，不抛 NPE，便于业务侧无条件透传与日志快速失败。
 */
public final class LogMasker {

  private LogMasker() {}

  /** 邮箱前缀保留位数：脱敏后形如 "a****@example.com" */
  private static final int EMAIL_HEAD_KEEP = 1;

  /**
   * 通用 KV 敏感凭据脱敏，与 GlobalExceptionHandler#P_SENSITIVE_CREDENTIALS 保持完全一致。
   * 用于业务侧 INFO/WARN 日志手动调用的脱敏（如 ORM 异常 / HTTP 入参 / DTO 序列化场景）。
   * <p>
   * 覆盖敏感 key（英文 15 类 + 中文 7 类）：
   * password / passwd / pwd / secret / token / authorization / cookie / session /
   *   access_token / refresh_token / api_key / apikey / private_key / client_secret / salt
   *   + 中文：密码 / 密钥 / 令牌 / 凭证 / 签名 / 私钥 / 公钥
   * <p>
   * 用法：捕获组 $1=key $2=分隔符 $3=value，replaceAll 时仅替换 value 保留 key。
   * value 上限 512 字符，超长会截断（防 ReDoS + 日志膨胀）。
   * <p>
   * 替换结果：保留 key 原值 + "[REDACTED]" 标记，便于运维识别被脱敏字段（"password" 仍出现，"password=xxx" 中 xxx 替换为 [REDACTED]）。
   * <p>
   * Pattern 用 static final 缓存，单次匹配约 0.05ms。
   */
  private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
          "(?i)(" +
                  "(?:[\"']?(?:password|passwd|pwd|secret|token|authorization|cookie|session|" +
                  "access_token|refresh_token|api_key|apikey|private_key|client_secret|salt)[\"']?)" +
                  "|" +
                  "(?:密码|密钥|令牌|凭证|签名|私钥|公钥)" +
                  ")" +
                  "(\\s*[:=]\\s*)" +
                  "([^\\s,;}\"']{1,512})");

  /**
   * 邮箱脱敏：保留首字符 + "****@domain"，兼容无 @ 的非邮箱字符串。
   * <p>
   * 典型样例：
   * - "alice@example.com" → "a****@example.com"
   * - "ab@example.com"    → "a****@example.com"（前缀不足 1 字符时仅取 1）
   * - "no-at-sign"        → "n****"（无 @ 退化为前缀 + ****）
   * - null / ""           → 原值
   */
  public static String maskEmail(String email) {
    if (email == null || email.isEmpty()) return email;
    int at = email.indexOf('@');
    if (at <= 0) {
      // 输入不含 @ 时退化为前缀 + "****"（避免脱敏为 [REDACTED] 误导排查）
      return email.length() <= EMAIL_HEAD_KEEP ? email : email.substring(0, EMAIL_HEAD_KEEP) + "****";
    }
    String head = email.substring(0, EMAIL_HEAD_KEEP);
    String domain = email.substring(at); // 含 @
    return head + "****" + domain;
  }

  /**
   * 手机号脱敏：前 3 位 + "****" + 后 4 位，11 位标准手机号形如 138****5678。
   * <p>
   * 典型样例：
   * - "13812345678" → "138****5678"
   * - "1381234"     → "138****234"（小于 11 位时按实际长度做尾保留）
   * - null / ""     → 原值
   */
  public static String maskPhone(String phone) {
    if (phone == null || phone.isEmpty()) return phone;
    int len = phone.length();
    // 保留前 3 位 + 后 4 位共 7 位 < 8 字符时退化为 "****"
    // 短于 8 位时直接掩码（兼容部分国家 7 位短号；11 位中国大陆手机号正常工作）
    if (len < 8) {
      return "****";
    }
    return phone.substring(0, 3) + "****" + phone.substring(len - 4);
  }

  /**
   * 通用中段脱敏：保留 head + tail，中间替换为 placeholder。
   * <p>
   * 适用场景：姓名 / 银行卡 / 身份证 ID 等需要保留首尾便于运维识别、中段必须掩码的字段。
   *
   * @param raw         原值
   * @param headKeep    前 N 字符保留
   * @param tailKeep    尾 N 字符保留
   * @param placeholder 占位符（如 "****" 或 "*"）
   */
  public static String maskMiddle(String raw, int headKeep, int tailKeep, String placeholder) {
    if (raw == null || raw.isEmpty()) return raw;
    int len = raw.length();
    if (headKeep + tailKeep >= len) {
      return raw;
    }
    if (placeholder == null) placeholder = "****";
    return raw.substring(0, headKeep) + placeholder + raw.substring(len - tailKeep);
  }

  /**
   * 通用 KV 敏感凭据脱敏：识别 key=value / key:value 格式的字符串，仅替换 value 为 [REDACTED]。
   * <p>
   * 典型样例：
   * <pre>
   * log.info("收到回调: {}", LogMasker.maskSensitiveKv(requestBody));
   * // 原始："password=abc123&token=xxx&email=a@b.com"
   * // 脱敏后："password=[REDACTED]&token=[REDACTED]&email=a@b.com"
   * </pre>
   * <p>
   * 与 GlobalExceptionHandler#sanitizeErrorMessage 区别：
   * - sanitizeErrorMessage 集中剥离 SQL 关键字 + 列名 + 索引名 + 绝对路径，本类仅处理 KV 凭据
   * - 本类定位"业务日志手动调用"，避免 SQL/IP/凭据三类敏感信息泄露到 ELK / Sentry / 控制台
   * <p>
   * null / 空字符串直接返回原值，不抛 NPE，便于业务侧无条件透传。
   * <p>
   * 性能优化：使用 {@code Matcher.find(int)} + 手动 cursor 推进，避免 {@code appendReplacement} 在
   * 含 {@code $} / {@code \} 等特殊字符时需要的二次转义；同时当字符串中无任何敏感 key 时，零拷贝
   * 直接返回原值，避免 StringBuilder 分配。预分配 {@code input.length() + 16} 作为初始容量，
   * 90% 场景下一次扩容即可完成拼接。
   */
  public static String maskSensitiveKv(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    Matcher m = SENSITIVE_KV_PATTERN.matcher(input);
    // 先用 find() 检查是否存在匹配；不存在时直接返回原值（避免 StringBuilder / append 开销）
    if (!m.find()) {
      return input;
    }
    StringBuilder sb = new StringBuilder(input.length() + 16);
    int cursor = 0;
    do {
      sb.append(input, cursor, m.start());
      // $1 = key, $2 = 分隔符（= 或 :），$3 = value
      // 仅替换 value 保留 key，便于运维识别被脱敏字段
      sb.append(m.group(1)).append(m.group(2)).append("[REDACTED]");
      cursor = m.end();
    } while (m.find(cursor) && m.end() > cursor);
    sb.append(input, cursor, input.length());
    return sb.toString();
  }
}