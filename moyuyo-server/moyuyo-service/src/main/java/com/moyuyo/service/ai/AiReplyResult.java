package com.moyuyo.service.ai;

/**
 * 第三方智能体一次问答的结果
 *
 * @param text           回复正文；null 表示本次没有拿到有效回复（调用方走降级逻辑）
 * @param conversationId 第三方会话 ID，用于下一轮复用上下文；null 表示无法复用
 * @param needHuman      智能体是否要求转人工
 * @param usage          用量与耗时，用于成本核算；null 表示未产生可记录信息
 * @param errorMessage   失败原因，成功时为 null
 */
public record AiReplyResult(String text, String conversationId, boolean needHuman,
                            AiUsage usage, String errorMessage) {

  /** 无有效回复（降级信号） */
  public static AiReplyResult empty() {
    return new AiReplyResult(null, null, false, null, null);
  }

  /** 调用失败：保留耗时与原因，供用量表记录 */
  public static AiReplyResult failure(String errorMessage, long durationMs) {
    return new AiReplyResult(null, null, false, AiUsage.ofDuration(durationMs), errorMessage);
  }

  /** 是否拿到了可展示的回复 */
  public boolean hasText() {
    return text != null && !text.isBlank();
  }
}
