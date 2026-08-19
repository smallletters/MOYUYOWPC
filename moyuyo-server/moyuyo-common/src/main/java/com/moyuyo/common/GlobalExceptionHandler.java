package com.moyuyo.common;

import com.moyuyo.common.utils.LogMasker;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.lettuce.core.RedisCommandTimeoutException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContextException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全局异常处理器
 * <p>
 * 关键策略：
 * - 统一返回 Result&lt;Void&gt;，避免堆栈信息泄露给客户端
 * - 限流/熔断/舱壁 异常归一化为 429/503 而非 500
 * - 异步客户端断连返回 204，避免 5xx 风暴触发 Alertmanager 误报
 * - DB 错误信息脱敏：剥离 SQL 关键字、列名、键名、URL 编码绕过片段、凭据 KV
 * - 容器异常（BeansException/LinkageError/AssertionError）兜底为 500 + 脱敏日志
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========== 错误消息脱敏正则（与 LogMasker.maskSensitiveKv 对齐，覆盖 15 类英文 + 7 类中文敏感键） ==========

    /** SQL 关键字片段：避免错误响应泄露表结构、查询条件 */
    private static final Pattern P_SQL_KEYWORDS = Pattern.compile(
            "(?i)\\b(SELECT\\s+\\*?\\s*FROM|INSERT\\s+INTO|UPDATE\\s+\\S+\\s+SET|DELETE\\s+FROM|DROP\\s+TABLE|TRUNCATE\\s+TABLE|ALTER\\s+TABLE)\\b[^\\n]{0,80}");

    /** Duplicate entry 模式：剥离具体值与索引名，仅保留"重复键"语义 */
    private static final Pattern P_DUPLICATE_KEY = Pattern.compile(
            "Duplicate entry [^']*'?[^']*'?\\s*for key '?\\w+'?\\.?\\d*", Pattern.CASE_INSENSITIVE);

    /** Column 不可空模式：剥离具体列名，仅保留"必填字段"语义 */
    private static final Pattern P_COLUMN_NOT_NULL = Pattern.compile(
            "Column '?\\w+'? cannot be null", Pattern.CASE_INSENSITIVE);

    /** 外键约束失败模式：剥离具体引用关系 */
    private static final Pattern P_FOREIGN_KEY = Pattern.compile(
            "foreign key constraint fails[^,]*,?\\s*(?:REFERENCES\\s+`?\\w+`?\\([^)]*\\))?", Pattern.CASE_INSENSITIVE);

    /** 绝对文件路径：避免泄露服务器目录结构 */
    private static final Pattern P_ABS_PATH = Pattern.compile(
            "(?:/opt/|/var/|/home/|/usr/|C:\\\\\\\\|D:\\\\\\\\)[\\w./\\\\-]+");

    /** 堆栈行号：避免泄露代码细节 */
    private static final Pattern P_STACK_LINE = Pattern.compile("\\s*at\\s+[\\w.$<>]+\\([^)]*\\)\\s*");

    // ========== 入参校验 ==========

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    /** Spring 6.1+ 新增的方法级校验异常（@Validated 方法形参注解触发） */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        // 6.1+ 移除了 getAllValidationResults()，改为直接遍历 getAllErrors()
        // 防御：MessageSourceResolvable.getDefaultMessage() 可能返回 null（自定义 MessageInterpolator 异常路径），
        // 这里过滤 null 避免 reduce 时出现 "null; null; null" 误导客户端
        String msg = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        // 防御：getConstraintViolations() 在 Hibernate Validator 偶发返回 null（Hibernate Validator 6+ 行为）
        // 与 Set 自身为 null 都做兜底，避免 NPE 污染上层
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String msg = (violations == null || violations.isEmpty())
                ? "参数校验失败"
                : violations.stream()
                        .map(v -> {
                            // 安全提取路径与消息：v 为 null 或属性 getter 抛异常时退化为占位符
                            // 避免 ConstraintViolation 内部状态异常导致整个 5xx 响应
                            jakarta.validation.Path path = v == null ? null : v.getPropertyPath();
                            String message = (v == null || v.getMessage() == null) ? "校验失败" : v.getMessage();
                            return (path == null ? "" : path.toString()) + ": " + message;
                        })
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleWebExchangeBind(WebExchangeBindException e) {
        return Result.error(400, "参数绑定失败");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(400, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // 不回显 e.getMessage()，避免泄露 JSON 解析细节（路径/列位置/字段名等敏感信息）
        // 仅输出统一文案；详细错误已记录在 DEBUG 级别日志便于运维排查
        if (log.isDebugEnabled()) {
            log.debug("请求体格式错误: {}", sanitizeErrorMessage(e.getMessage()));
        }
        return Result.badRequest("请求体格式错误");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(400, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, sanitizeErrorMessage(e.getMessage()));
    }

    /**
     * 业务状态冲突（如"审核记录已处理"等 IllegalStateException）。
     * 原实现未覆盖，导致该异常落到兜底 Exception handler 返回 500，
     * 前端误以为服务端故障；改为 409 + 业务可读 message，便于前端精准提示。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleIllegalState(IllegalStateException e) {
        return Result.error(409, sanitizeErrorMessage(e.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * P2 优化：上传文件超过 Tomcat max-swallow-size / Spring multipart max-file-size 时
     * 显式归一化为 413 而非 500，避免前端误以为服务端故障
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("上传文件超过最大限制: {}", e.getMessage());
        return Result.error(413, "上传文件过大，请压缩后重试");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.error(404, "请求的资源不存在");
    }

    // ========== 数据库异常（含敏感信息脱敏） ==========

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据库约束违反: {}", sanitizeErrorMessage(e.getMessage()));
        return Result.error(409, "数据冲突，请检查后重试");
    }

    /** 其它 DB 异常：脱敏后返回 500，避免泄露表结构/列名/索引名 */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDataAccess(DataAccessException e) {
        log.error("数据库访问异常: {}", sanitizeErrorMessage(e.getMessage()), e);
        return Result.error("数据访问异常，请稍后重试");
    }

    // ========== 限流 / 熔断 / 舱壁（resilience4j 异常归一化） ==========

    /**
     * 限流触发（429）—— 避免前端误判为 500
     */
    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleRateLimitExceeded(RequestNotPermitted e) {
        log.warn("限流触发: {}", e.getMessage());
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    /**
     * 熔断器 OPEN（503）—— 下游依赖不可用，避免与 500 混淆
     */
    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleCircuitBreakerOpen(CallNotPermittedException e) {
        log.warn("熔断器 OPEN: {}", e.getMessage());
        return Result.error(503, "服务暂时不可用，请稍后重试");
    }

    /**
     * 舱壁满（429）—— 并发数超限
     */
    @ExceptionHandler(BulkheadFullException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleBulkheadFull(BulkheadFullException e) {
        log.warn("舱壁满: {}", e.getMessage());
        return Result.error(429, "并发请求过多，请稍后再试");
    }

    // ========== Redis / 缓存异常归一化 ==========

    /**
     * Redis 连接失败（fail-open 或 503）—— 由业务层决定是否兜底
     * 此处统一返回 503，让客户端感知并重试
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisCommandTimeoutException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleRedisUnavailable(Exception e) {
        log.error("Redis 不可用: {}", e.getMessage());
        return Result.error(503, "缓存服务暂时不可用，请稍后重试");
    }

    /**
     * Spring Cache 抽象失败（fail-open 返回 503，便于客户端重试）。
     * <p>
     * 注：Spring 6.x 已移除 {@code org.springframework.cache.CacheException}，缓存失败统一抛
     * {@link org.springframework.dao.DataAccessException} 或包装为运行时异常。
     * 通过异常类名前缀（{@code org.springframework.cache}）匹配 spring.cache 包的运行时异常，
     * 避免被误识别为普通业务 RuntimeException。
     * <p>
     * P2 修复：原实现用 {@code @ExceptionHandler(RuntimeException.class)} + 内部类名前缀判断，
     * 会拦截所有未匹配到具体 handler 的 RuntimeException，导致非缓存 RuntimeException
     * 被错误识别为 503 + "缓存服务异常"，掩盖真实业务错误。
     * 现改为兜底 Exception handler 前置判断：仅当异常源自 spring.cache 包时返回 503，
     * 否则走 {@link #handleException(Exception)} 兜底逻辑。
     * <p>
     * 实现方式：在 handleException 内对 spring.cache 异常优先处理，避免与具体 ExceptionHandler 冲突。
     */
    // 此处不再单独声明 RuntimeException handler；统一在 handleException 中按包名识别 cache 异常

    /**
     * Redis 序列化异常（fail-closed，运维第一时间感知配置问题）
     */
    @ExceptionHandler(SerializationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleSerializationException(SerializationException e) {
        log.error("Redis 序列化异常（请检查序列化器配置）", e);
        return Result.error(503, "服务内部错误");
    }

    // ========== 下游 HTTP 客户端网络异常归一化（WooCommerce / Stripe / PayPal 等） ==========

    /**
     * 下游 HTTP 连接超时：归一化为 503（与熔断器 OPEN 语义对齐），让客户端识别为"服务暂时不可用"并重试，
     * 避免落到通用 Exception handler 返回 500 时被误判为服务端 Bug。
     */
    @ExceptionHandler({SocketTimeoutException.class, ConnectException.class, UnknownHostException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDownstreamNetwork(Exception e) {
        log.warn("下游网络异常（已归一化为 503）：{}: {}", e.getClass().getSimpleName(), sanitizeErrorMessage(e.getMessage()));
        return Result.error(503, "下游服务暂时不可用，请稍后重试");
    }

    // ========== 异步客户端断连：浏览器关闭/网络抖动造成，避免 5xx 风暴 ==========

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("异步请求超时（客户端可能已断开）: {}", e.getMessage());
        return Result.success();
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        log.debug("异步响应不可用（客户端已断开）: {}", e.getMessage());
        return Result.success();
    }

    @ExceptionHandler(ClientAbortException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> handleClientAbort(ClientAbortException e) {
        log.debug("客户端中断连接: {}", e.getMessage());
        return Result.success();
    }

    // ========== 安全异常 ==========

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Result.error(403, "无权限访问");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.unauthorized("认证失败");
    }

    // ========== 媒体类型异常 ==========

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.error(415, "不支持的媒体类型");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Result<Void> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return Result.error(406, "不接受的媒体类型");
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleMessageNotWritable(HttpMessageNotWritableException e) {
        log.error("响应写入失败", e);
        return Result.error("响应处理失败");
    }

    // ========== Spring 容器异常：避免泄露 Bean 名称 / 工厂方法 / 依赖图 ==========

    @ExceptionHandler(BeansException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleBeansException(BeansException e) {
        log.error("Spring 容器异常（已脱敏）", e);
        return Result.error("服务初始化异常");
    }

    @ExceptionHandler(ApplicationContextException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleContextException(ApplicationContextException e) {
        log.error("Spring 应用上下文异常（已脱敏）", e);
        return Result.error("服务初始化异常");
    }

    @ExceptionHandler(LinkageError.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleLinkageError(LinkageError e) {
        log.error("类加载/链接错误（已脱敏）", e);
        return Result.error("服务初始化异常");
    }

    @ExceptionHandler(AssertionError.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleAssertionError(AssertionError e) {
        log.error("断言失败（已脱敏）", e);
        return Result.error("服务内部错误");
    }

    // ========== 兜底异常 ==========
    // P2 修复：前置判断 spring.cache 包异常优先以 503 返回（避免 Spring Cache 失败被识别为普通 500）。
    // 仅当异常类路径以 org.springframework.cache 开头时才走 cache 分支，
    // 其余 RuntimeException / Exception 走统一 500 脱敏响应。
    // 通过 ResponseEntity 而非 @ResponseStatus 控制 HTTP 状态码，避免 @ResponseStatus 强制 500 与 cache 异常
    // 返回 503 冲突。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        if (e.getClass().getName().startsWith("org.springframework.cache")) {
            log.error("缓存异常: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.error(503, "缓存服务异常，请稍后重试"));
        }
        log.error("未预期异常: {}", sanitizeErrorMessage(e.getMessage()), e);
        // 返回脱敏后的 message，便于前端精准提示用户；不再固定写死「服务器内部错误」
        String msg = sanitizeErrorMessage(e.getMessage());
        if (msg == null || msg.isBlank()) {
            msg = "服务器内部错误";
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, msg));
    }

    // ========== 错误消息脱敏工具方法 ==========

    /**
     * 对错误消息做多轮脱敏，顺序：
     * 1. URL 解码 + 检测 SQL 关键字（防止 %53ELECT ... FROM 这类编码绕过）
     * 2. 替换 SQL 关键字 / Duplicate entry / Column not null / 外键约束 / 绝对路径 / 堆栈行号
     * 3. KV 敏感凭据脱敏（password=xxx / token=xxx 等）
     * <p>
     * 注意：必须先做 URL 解码与 SQL 关键字检测，再做 replaceAll；否则脱敏后的字符串无法识别编码绕过片段。
     */
    public static String sanitizeErrorMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String msg = raw;

        // Step 1：URL 解码 + 检测编码绕过 SQL 关键字（先解码，避免 %53ELECT 绕过）
        try {
            String decoded = URLDecoder.decode(msg, StandardCharsets.UTF_8);
            if (hasEncodedSqlKeyword(decoded)) {
                msg = decoded + " [SQL-FILTERED-encoded]";
            }
        } catch (Exception ignore) {
            // 解码失败说明输入非 URL 编码格式，按原值继续
        }

        // Step 2：替换 SQL 关键字 / Duplicate entry / Column not null / 外键约束
        msg = P_SQL_KEYWORDS.matcher(msg).replaceAll("[SQL-FILTERED]");
        msg = P_DUPLICATE_KEY.matcher(msg).replaceAll("Duplicate entry [VALUES-FILTERED]");
        msg = P_COLUMN_NOT_NULL.matcher(msg).replaceAll("Column [COLUMN-FILTERED] cannot be null");
        msg = P_FOREIGN_KEY.matcher(msg).replaceAll("foreign key constraint fails [RELATION-FILTERED]");

        // Step 3：剥离绝对路径与堆栈行号
        msg = P_ABS_PATH.matcher(msg).replaceAll("[PATH-FILTERED]");
        msg = P_STACK_LINE.matcher(msg).replaceAll("");

        // Step 4：KV 敏感凭据脱敏（与 LogMasker.maskSensitiveKv 对齐）
        msg = LogMasker.maskSensitiveKv(msg);

        // 限制最大长度，避免攻击者构造 1MB 异常消息触发日志膨胀
        if (msg.length() > 512) {
            msg = msg.substring(0, 512) + " ...[TRUNCATED]";
        }
        return msg;
    }

    /**
     * 检测 SQL 关键字（包括 URL 编码形式 %53ELECT / %73ELECT / %2A 等）
     * 仅做粗粒度检测，无需精确解码
     */
    private static boolean hasEncodedSqlKeyword(String input) {
        String upper = input.toUpperCase();
        // 直接匹配关键字
        if (upper.contains("SELECT") || upper.contains("INSERT") || upper.contains("UPDATE")
                || upper.contains("DELETE") || upper.contains("DROP") || upper.contains("TRUNCATE")) {
            // 进一步过滤掉正常英文单词（如 "DESELECTED"）
            if (upper.matches(".*\\b(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b.*")) {
                return true;
            }
        }
        // URL 编码匹配：%53ELECT（% + 5x）/%73ELECT（% + 7x）等
        return upper.matches(".*%[4-7][0-9A-F](ELECT|NSERT|PPDATE|ELETE|ROP|RUNCATE).*");
    }
}