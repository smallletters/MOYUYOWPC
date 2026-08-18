package com.moyuyo.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 注入过滤器
 * <p>
 * 关键设计：
 * 1. 不依赖 brave.Tracer / ObservationRegistry 等链路追踪 SDK Bean，
 *    仅通过 SLF4J MDC 管理 traceId，保证在最小化 dev profile / 单元测试场景下也能稳定运行
 * 2. 客户端可通过 X-Trace-Id 传入 traceId，便于跨服务调用时复用同一 traceId，
 *    但必须通过白名单 + 长度校验（防 CRLF 注入到响应头 / 控制字符污染结构化日志）
 * 3. 未传 X-Trace-Id 时自动生成 32 位 UUID（去除 "-"），全局唯一
 * 4. traceId 同时写入 MDC 与响应头 X-Trace-Id，便于客户端排障关联日志
 * <p>
 * 与 micrometer-tracing 的链路关联（若需 spanId 上报）：
 * 可在 WebConfig 中通过 ObservationRegistry 显式注入，本过滤器保留 traceId 作为链路入口，
 * 不强制绑定 Brave / OTel SDK
 */
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    /** traceId 长度上限：与上游约定的字符数对齐，防止攻击者传入超长 traceId 撑爆日志 */
    private static final int TRACE_ID_MAX_LENGTH = 64;

    /** traceId 字符白名单：仅允许 [A-Za-z0-9_-]，拒绝 CRLF / 控制字符 / 中文等 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId;
        String requestTraceId = request.getHeader("X-Trace-Id");

        if (requestTraceId != null && !requestTraceId.isEmpty()) {
            // 客户端传入的 traceId 必须通过字符白名单 + 长度校验
            if (TRACE_ID_PATTERN.matcher(requestTraceId).matches()) {
                traceId = requestTraceId;
            } else {
                // 非法格式：丢弃客户端值，生成新 traceId，避免被攻击者注入 CRLF / 控制字符
                log.debug("丢弃非法 X-Trace-Id（不匹配字符白名单），长度={}", requestTraceId.length());
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
        } else {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}