package com.moyuyo.service.config;

/**
 * AI 客服运行时生效配置
 * <p>
 * 由后台「系统配置 - AI 客服」填写并落库（mo_system_config），
 * 与 yml 中的 {@link AiCsProperties} 合并后得到：
 * 数据库有值时以数据库为准，为空时回退 yml / 环境变量。
 *
 * @param enabled            是否启用 AI 自动回复
 * @param provider           提供方：none / coze
 * @param baseUrl            第三方接口地址
 * @param botId              智能体 ID（扣子 OpenAPI 用）
 * @param projectId          项目 ID（扣子编程 coze.site 用）
 * @param token              访问令牌（仅后端可见，后台接口返回时会脱敏）
 * @param timeoutMs          单次对话总超时
 * @param humanMarker        转人工标记
 * @param rateLimitEnabled   是否开启按用户限流
 * @param rateLimitPerMinute 单用户每分钟上限
 */
public record AiCsConfig(boolean enabled, String provider, String baseUrl, String botId, String projectId,
                         String token, int timeoutMs, String humanMarker, boolean rateLimitEnabled,
                         int rateLimitPerMinute) {

  /**
   * 是否具备实际调用条件：开关打开、指定了实现、且凭据齐全。
   * botId 与 projectId 对应两种扣子部署形态，有其一即可；
   * 后台只填了一半时，客服继续走内置关键词回复，不会给用户回兜底话术。
   */
  public boolean usable() {
    return enabled
        && provider != null && !provider.isBlank() && !"none".equalsIgnoreCase(provider)
        && (botId != null && !botId.isBlank() || projectId != null && !projectId.isBlank())
        && token != null && !token.isBlank();
  }
}
