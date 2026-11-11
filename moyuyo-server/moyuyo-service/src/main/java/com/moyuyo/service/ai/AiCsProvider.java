package com.moyuyo.service.ai;

/**
 * AI 智能客服提供方 SPI
 * <p>
 * 按 {@code moyuyo.ai-cs.provider} 注入实现：
 * <ul>
 *   <li>{@code none} - {@code NoopAiCsProvider}，默认空实现，表示不启用 AI</li>
 *   <li>{@code coze} - {@code CozeAiCsProvider}，调用扣子 OpenAPI</li>
 * </ul>
 * 业务侧只依赖本接口，后续换 Dify / 自建模型时新增实现即可，不改调用方。
 */
public interface AiCsProvider {

  /**
   * 发起一轮问答
   *
   * @param userId         幂等用户标识（传脱敏 ID，不要传手机号/邮箱）
   * @param conversationId 上一轮返回的第三方会话 ID，首轮传 null
   * @param text           用户问题原文
   * @return 问答结果；无法回复时返回 {@link AiReplyResult#empty()}，不抛业务异常给上层
   */
  AiReplyResult reply(String userId, String conversationId, String text);

  /**
   * 提供商标识，用于日志与监控
   */
  String getProviderName();
}
