package com.moyuyo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试
 * 验证每类异常映射到正确的 HTTP 状态码和业务错误码
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("IllegalArgumentException -> 400")
    void illegalArgument_returns400() {
        Result<Void> r = handler.handleIllegalArgument(new IllegalArgumentException("参数非法"));
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).contains("参数非法");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException -> 400 请求体格式错误")
    void messageNotReadable_returns400() {
        Result<Void> r = handler.handleMessageNotReadable(new HttpMessageNotReadableException("bad json"));
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("MissingServletRequestParameterException -> 400 包含参数名")
    void missingParam_returns400() {
        Result<Void> r = handler.handleMissingParam(
                new MissingServletRequestParameterException("userId", "Long"));
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).contains("userId");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException -> 400 包含字段名")
    void typeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "userId", null, new RuntimeException());
        Result<Void> r = handler.handleTypeMismatch(ex);
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).contains("userId");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException -> 405")
    void methodNotSupported_returns405() {
        Result<Void> r = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"));
        assertThat(r.getCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("NoResourceFoundException -> 404")
    void noResourceFound_returns404() {
        Result<Void> r = handler.handleNotFound(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/foo"));
        assertThat(r.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("DataIntegrityViolationException -> 409（数据冲突）")
    void dataIntegrity_returns409() {
        Result<Void> r = handler.handleDataIntegrity(new DataIntegrityViolationException("dup key"));
        assertThat(r.getCode()).isEqualTo(409);
        // 不应向客户端泄露内部异常细节
        assertThat(r.getMessage()).doesNotContain("dup key");
    }

    @Test
    @DisplayName("AccessDeniedException -> 403")
    void accessDenied_returns403() {
        Result<Void> r = handler.handleAccessDenied(new AccessDeniedException("禁止访问"));
        assertThat(r.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("兜底 Exception -> 500（不泄露内部信息）")
    void exception_returns500() {
        Result<Void> r = handler.handleException(new RuntimeException("数据库连接失败，含密码 root/123456"));
        assertThat(r.getCode()).isEqualTo(500);
        // 关键安全断言：内部异常信息绝对不能泄露给客户端
        assertThat(r.getMessage()).doesNotContain("root");
        assertThat(r.getMessage()).doesNotContain("123456");
        assertThat(r.getMessage()).doesNotContain("数据库连接失败");
    }
}