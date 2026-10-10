package com.moyuyo.common.annotation;

import java.lang.annotation.*;

/**
 * 管理后台写操作审计注解
 * <p>
 * 标注在 {@code /api/admin/**} 下的 Controller 写方法上（POST/PUT/PATCH/DELETE），
 * 由 {@code AdminAuditAspect} 拦截后自动写入 {@code mo_audit_log}，
 * 供管理后台「运营日志」页面展示。
 * <p>
 * 与 {@link OperationLog} 的区别：
 * <ul>
 *   <li>{@code OperationLog}：面向业务操作（支付回调/订单状态变更等），表为 {@code mo_operation_log}</li>
 *   <li>{@code AdminAudit}：面向"管理员在后台做了什么"，表为 {@code mo_audit_log}</li>
 * </ul>
 * 字段对齐：action 枚举固定为 CREATE/UPDATE/DELETE/EXPORT/LOGIN；
 * module 取类级 {@link #module()} 或 URL 第一段。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @PostMapping("/orders/{id}/refund")
 * @AdminAudit(action = "UPDATE", module = "ORDER", resourceId = "#id",
 *             detail = "管理员审核退款，金额=#amount")
 * public Result<?> auditRefund(@PathVariable Long id, @RequestBody RefundAuditReq req) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAudit {

    /**
     * 操作动作（CREATE / UPDATE / DELETE / EXPORT / LOGIN），与数据库 action 字段枚举对齐
     */
    String action();

    /**
     * 操作模块（ORDER / PRODUCT / USER / SYSTEM 等），可省略时由 URL 第一段推断
     */
    String module() default "";

    /**
     * 资源 ID 表达式，支持 SpEL（如 "#id" / "#req.userId"），可省略
     */
    String resourceId() default "";

    /**
     * 操作详情，支持 SpEL 引用方法参数，可省略
     */
    String detail() default "";

    /**
     * 是否记录请求参数（默认 false，敏感接口请显式开启并配合 LogMasker）
     */
    boolean logParams() default false;
}
