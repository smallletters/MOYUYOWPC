package com.moyuyo.common;

import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一返回体
 */
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;

    private Result() {}

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }
}
