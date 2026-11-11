package com.moyuyo.common.ratelimit;

/**
 * 分布式限流触发异常，由 GlobalExceptionHandler 统一转成 HTTP 429。
 * <p>
 * 携带窗口长度，便于在响应头 Retry-After 中提示客户端可重试的等待秒数。
 */
public class RateLimitExceededException extends RuntimeException {

  /** 建议客户端重试的等待秒数 */
  private final int retryAfterSeconds;

  public RateLimitExceededException(String message, int retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
