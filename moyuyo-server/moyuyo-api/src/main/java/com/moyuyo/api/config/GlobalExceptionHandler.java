package com.moyuyo.api.config;

import com.moyuyo.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理 Controller 层未捕获的异常，消除各 Controller 中重复的 try-catch 模板代码
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("请求处理异常", e);
        return Result.error(500, "操作失败: " + e.getMessage());
    }
}
