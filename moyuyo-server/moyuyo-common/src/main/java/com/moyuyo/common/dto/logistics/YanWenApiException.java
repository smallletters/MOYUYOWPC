package com.moyuyo.common.dto.logistics;

import lombok.Getter;

/**
 * 燕文物流 API 调用异常。
 * 用于在调用 SDK 过程中区分网络/业务/参数错误。
 */
@Getter
public class YanWenApiException extends RuntimeException {

    /** 燕文返回的 code（业务错误码） */
    private final String yanwenCode;

    public YanWenApiException(String message) {
        super(message);
        this.yanwenCode = null;
    }

    public YanWenApiException(String message, Throwable cause) {
        super(message, cause);
        this.yanwenCode = null;
    }

    public YanWenApiException(String yanwenCode, String message) {
        super(message);
        this.yanwenCode = yanwenCode;
    }
}