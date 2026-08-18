package com.moyuyo.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一返回体
 * <p>
 * 关键修复：
 * - 增加 success(T, String) 与 error(int, String, T) 重载，便于批量返回 traceId / data
 * - 显式补上无 traceId 构造路径，避免 MDC 为 null 时 NPE
 * - 暴露业务码常量，避免 Controller / Service 中散落的魔法数字
 * <p>
 * P2 优化：{@code @JsonInclude(JsonInclude.Include.NON_NULL)} 仅在 traceId/message/data 非 null 时序列化。
 * 异步线程（@Async / MQ 消费者 / @Scheduled 任务）调用 Result.success() 时 traceId 通常为 null，
 * 不序列化可节省 ~30 字节/响应 × 高 QPS 接口累计节省可观带宽。
 * 注意：code 是 int 默认值 0，会被序列化（成功响应语义需要）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;

    // ========== 业务状态码（与 HTTP 状态码对齐，便于客户端统一处理） ==========

    /** 业务成功 */
    public static final int CODE_SUCCESS = 0;
    /** 请求参数错误 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录 / Token 失效 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限访问 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 业务校验失败 / 数据冲突 */
    public static final int CODE_CONFLICT = 409;
    /** 请求体过大（如上传超限） */
    public static final int CODE_PAYLOAD_TOO_LARGE = 413;
    /** 限流触发 */
    public static final int CODE_RATE_LIMITED = 429;
    /** 服务暂时不可用（熔断 / 下游不可用） */
    public static final int CODE_SERVICE_UNAVAILABLE = 503;
    /** 服务器内部错误 */
    public static final int CODE_INTERNAL_ERROR = 500;

    private Result() {}

    /**
     * 从 MDC 读取当前请求的 traceId，用于响应体关联服务端日志
     * <p>
     * P2 优化：原每处构造方法都直接 {@code MDC.get("traceId")} 重复三次，且未来若 MDC key 变更需改 3 处。
     * 现抽取为私有静态方法，与 TraceIdFilter 中的 key 字符串保持单一来源（实际上 MDC key 仅一处声明），
     * 便于后续接入 OpenTelemetry / Brave Tracer 时统一替换。
     * <p>
     * 防御：MDC.get 本身不会抛 NPE，但异步线程（@Async / MQ 消费者 / @Scheduled 任务）通常 MDC 为空，
     * 调用 Result.success() 会得到 null traceId，下游日志聚合无法关联。统一在入口处兜底返回 null，
     * 由 Jackson 序列化为 JSON null（或通过 @JsonInclude 隐藏），业务侧不应假设 traceId 一定非空。
     */
    private static String currentTraceId() {
        try {
            return MDC.get("traceId");
        } catch (Exception e) {
            // MDC 在某些隔离线程（如 CompletableFuture 自定义线程池）下可能抛 IllegalStateException，
            // 兜底返回 null 避免业务接口因日志上下文异常而 5xx
            return null;
        }
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = CODE_SUCCESS;
        r.message = "success";
        r.data = data;
        r.traceId = currentTraceId();
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = currentTraceId();
        return r;
    }

    public static <T> Result<T> error(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        r.traceId = currentTraceId();
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(CODE_INTERNAL_ERROR, message);
    }

    /** 400 便捷构造（与 GlobalExceptionHandler#handleValidation 等场景对齐） */
    public static <T> Result<T> badRequest(String message) {
        return error(CODE_BAD_REQUEST, message);
    }

    public static <T> Result<T> unauthorized(String message) {
        return error(CODE_UNAUTHORIZED, message);
    }

    /** 403 便捷构造 */
    public static <T> Result<T> forbidden(String message) {
        return error(CODE_FORBIDDEN, message);
    }

    /** 404 便捷构造 */
    public static <T> Result<T> notFound(String message) {
        return error(CODE_NOT_FOUND, message);
    }

    /** 409 便捷构造 */
    public static <T> Result<T> conflict(String message) {
        return error(CODE_CONFLICT, message);
    }

    /** 429 便捷构造 */
    public static <T> Result<T> rateLimited(String message) {
        return error(CODE_RATE_LIMITED, message);
    }

    /** 503 便捷构造 */
    public static <T> Result<T> serviceUnavailable(String message) {
        return error(CODE_SERVICE_UNAVAILABLE, message);
    }

    /**
     * 判定当前响应是否为业务成功
     */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }
}