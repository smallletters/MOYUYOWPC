package com.moyuyo.service.config;

import com.moyuyo.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 客服配置读取服务
 * <p>
 * 配置来源优先级：mo_system_config（后台可改） > yml / 环境变量（兜底）。
 * <p>
 * 缓存策略：缓存 30 秒，而不是永久缓存 + 保存时刷新。
 * 因为服务可能是多实例部署，A 实例改了配置无法通知 B 实例；
 * 30 秒的最终一致对「改完马上试一条消息」的体验已经足够，且改动成本最低。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCsConfigService {

  /** 配置缓存时长（毫秒） */
  private static final long CACHE_TTL_MS = 30_000L;

  // 后台配置项 key（统一前缀，避免与其它系统配置冲突）
  private static final String KEY_ENABLED = "ai_cs_enabled";
  private static final String KEY_PROVIDER = "ai_cs_provider";
  private static final String KEY_BASE_URL = "ai_cs_base_url";
  private static final String KEY_BOT_ID = "ai_cs_bot_id";
  private static final String KEY_PROJECT_ID = "ai_cs_project_id";
  private static final String KEY_TOKEN = "ai_cs_token";
  private static final String KEY_TIMEOUT_MS = "ai_cs_timeout_ms";
  private static final String KEY_HUMAN_MARKER = "ai_cs_human_marker";
  private static final String KEY_RATE_LIMIT_ENABLED = "ai_cs_rate_limit_enabled";
  private static final String KEY_RATE_LIMIT_PER_MINUTE = "ai_cs_rate_limit_per_minute";

  private final SystemConfigService systemConfigService;
  private final AiCsProperties defaults;

  private volatile AiCsConfig cached;
  private volatile long expireAt;

  /**
   * 当前生效配置（带缓存）
   */
  public AiCsConfig current() {
    AiCsConfig local = cached;
    if (local != null && System.currentTimeMillis() < expireAt) {
      return local;
    }
    synchronized (this) {
      if (cached == null || System.currentTimeMillis() >= expireAt) {
        cached = load();
        expireAt = System.currentTimeMillis() + CACHE_TTL_MS;
      }
      return cached;
    }
  }

  /**
   * 使缓存立即失效（保存配置后由调用方触发，本实例即时生效）
   */
  public void refresh() {
    expireAt = 0L;
  }

  /**
   * 读取并合并配置：数据库为空则回退 yml 默认值
   */
  private AiCsConfig load() {
    Map<String, Object> map;
    try {
      map = systemConfigService.getConfig("ai_cs");
    } catch (Exception e) {
      // 读库失败不能让客服不可用：直接用 yml 配置，并记录告警
      log.warn("读取 AI 客服配置失败，回退 yml 默认值: {}", e.getMessage());
      return fromDefaults();
    }
    return new AiCsConfig(
        bool(map, KEY_ENABLED, defaults.isEnabled()),
        str(map, KEY_PROVIDER, defaults.getProvider()),
        str(map, KEY_BASE_URL, defaults.getBaseUrl()),
        str(map, KEY_BOT_ID, defaults.getBotId()),
        str(map, KEY_PROJECT_ID, ""),
        str(map, KEY_TOKEN, defaults.getToken()),
        intVal(map, KEY_TIMEOUT_MS, defaults.getTimeoutMs()),
        str(map, KEY_HUMAN_MARKER, defaults.getHumanMarker()),
        bool(map, KEY_RATE_LIMIT_ENABLED, defaults.isRateLimitEnabled()),
        intVal(map, KEY_RATE_LIMIT_PER_MINUTE, defaults.getRateLimitPerMinute()));
  }

  private AiCsConfig fromDefaults() {
    return new AiCsConfig(defaults.isEnabled(), defaults.getProvider(), defaults.getBaseUrl(),
        defaults.getBotId(), "", defaults.getToken(), defaults.getTimeoutMs(),
        defaults.getHumanMarker(), defaults.isRateLimitEnabled(), defaults.getRateLimitPerMinute());
  }

  /**
   * 读取字符串配置
   * <p>
   * 只有「后台从未保存过该项」才回落 yml 默认值；已保存但值为空串表示管理员主动清空，
   * 此时必须返回空串，否则清空 token/botId 无法真正停用 AI。
   */
  private String str(Map<String, Object> map, String key, String fallback) {
    if (!map.containsKey(key)) return fallback;
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private boolean bool(Map<String, Object> map, String key, boolean fallback) {
    Object value = map.get(key);
    if (value == null) return fallback;
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) return fallback;
    return "true".equalsIgnoreCase(text) || "1".equals(text);
  }

  private int intVal(Map<String, Object> map, String key, int fallback) {
    Object value = map.get(key);
    if (value == null) return fallback;
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
