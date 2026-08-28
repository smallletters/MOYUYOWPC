package com.moyuyo.common.exception;

import lombok.Getter;

/**
 * 业务异常：携带自定义业务码（如 502 下游故障、503 服务暂不可用）。
 *
 * 与 IllegalArgumentException 的区别：
 * - IllegalArgumentException 由 GlobalExceptionHandler 映射为 400；
 * - 本异常通过指定 code，可映射为 502/503/504 等 5xx 而非 400。
 *
 * 由 GlobalExceptionHandler 中对应 @ExceptionHandler(BusinessException.class) 统一捕获（见下条新增）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}