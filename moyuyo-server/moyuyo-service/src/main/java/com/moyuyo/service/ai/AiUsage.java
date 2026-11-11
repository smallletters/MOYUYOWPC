package com.moyuyo.service.ai;

/**
 * 单次第三方调用的用量与耗时
 *
 * @param requestId    第三方对话 ID（扣子 chat_id），用于和对方账单对账
 * @param inputTokens  输入 token 数
 * @param outputTokens 输出 token 数
 * @param totalTokens  总 token 数
 * @param durationMs   调用耗时（毫秒）
 */
public record AiUsage(String requestId, int inputTokens, int outputTokens, int totalTokens, long durationMs) {

  /** 无用量信息（例如调用直接失败） */
  public static AiUsage ofDuration(long durationMs) {
    return new AiUsage(null, 0, 0, 0, durationMs);
  }
}
