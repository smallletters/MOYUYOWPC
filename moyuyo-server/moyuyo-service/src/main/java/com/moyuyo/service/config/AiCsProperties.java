package com.moyuyo.service.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 智能客服配置
 * <p>
 * 对应 yml 中 {@code moyuyo.ai-cs.*} 配置项。
 * 默认关闭（enabled=false + provider=none），此时客服仍走
 * {@code CsService.autoReply()} 的内置关键词回复，行为与改造前完全一致。
 * <p>
 * 安全约束：token 属于后端机密，只能通过环境变量注入，禁止下发到 APP 端。
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "moyuyo.ai-cs")
public class AiCsProperties {

  /**
   * 总开关：false 时完全不调用第三方，走内置关键词回复
   */
  private boolean enabled = false;

  /**
   * 提供商标识：none（默认）/ coze
   */
  private String provider = "none";

  /**
   * 扣子 OpenAPI 地址（国内版 https://api.coze.cn，切勿使用国际版 api.coze.com）
   */
  private String baseUrl = "https://api.coze.cn";

  /**
   * 智能体 ID（扣子控制台发布到 API 渠道后获取）
   */
  private String botId;

  /**
   * 个人访问令牌（PAT），需具备 chat 权限
   */
  private String token;

  /**
   * HTTP 连接超时（毫秒）
   */
  private int connectTimeoutMs = 5000;

  /**
   * HTTP 读取超时（毫秒）
   */
  private int readTimeoutMs = 15000;

  /**
   * 单次对话总等待上限（毫秒），超时后降级为关键词回复
   */
  private int timeoutMs = 8000;

  /**
   * 轮询扣子 chat 状态的间隔（毫秒）
   */
  private int pollIntervalMs = 500;

  /**
   * 智能体输出该标记时视为「要求转人工」，标记本身不会展示给用户
   */
  private String humanMarker = "[[HUMAN]]";

  /**
   * 是否开启单用户 AI 调用限流（防止刷 token 造成费用失控）
   */
  private boolean rateLimitEnabled = true;

  /**
   * 单用户每分钟最多触发的 AI 轮次；超限只提示，不影响用户继续找人工
   */
  private int rateLimitPerMinute = 10;

  /**
   * 启动期仅做提示，不阻断启动。
   * <p>
   * 配置已支持在后台「系统配置 - AI 客服」中填写，yml 只作为兜底默认值，
   * 因此这里不能因为 yml 缺少 token 就抛出异常。
   */
  @PostConstruct
  public void validate() {
    if (!enabled) {
      log.info("AI 客服 yml 默认未启用，可稍后在后台「系统配置 - AI 客服」中开启");
      return;
    }
    if ("coze".equalsIgnoreCase(provider) && (botId == null || botId.isBlank() || token == null || token.isBlank())) {
      log.warn("AI 客服 yml 已启用但 bot-id / token 未配置完整，将以后台配置为准");
      return;
    }
    log.info("AI 客服 yml 默认配置已加载，provider={}, baseUrl={}", provider, baseUrl);
  }
}
