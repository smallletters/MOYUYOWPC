package com.moyuyo.common.annotation;

import java.lang.annotation.*;

/**
 * 关键业务操作日志注解
 * <p>
 * 标注在 Controller 或 Service 方法上，由 {@link com.moyuyo.service.audit.OperationLogAspect}
 * 自动记录操作日志，包括操作人、操作类型、入参和耗时。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @OperationLog(type = "支付回调", detail = "#orderNo")
 * public void handleWebhook(String orderNo, String payload) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型（如：支付回调、订单状态变更、退款处理）
     */
    String type();

    /**
     * 操作详情，支持 SpEL 表达式引用方法参数（如 "#orderNo"）
     */
    String detail() default "";

    /**
     * 是否记录请求参数（默认 true，敏感接口请设为 false）
     */
    boolean logParams() default true;

    /**
     * 是否记录响应结果（默认 false，避免大数据量）
     */
    boolean logResult() default false;
}
