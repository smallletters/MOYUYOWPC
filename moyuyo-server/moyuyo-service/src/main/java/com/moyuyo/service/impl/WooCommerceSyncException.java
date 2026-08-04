package com.moyuyo.service.impl;

/**
 * WooCommerce 同步业务异常
 * <p>
 * 用于区分 WooCommerce 上游错误（限流/超时/HTTP 4xx/5xx）与本地业务异常，
 * 让上层（Controller / GlobalExceptionHandler）能给出明确的前端提示。
 */
public class WooCommerceSyncException extends RuntimeException {

    public WooCommerceSyncException(String message) {
        super(message);
    }

    public WooCommerceSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
