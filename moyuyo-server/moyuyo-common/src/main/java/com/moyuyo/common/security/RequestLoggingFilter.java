package com.moyuyo.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求日志过滤器 - 记录每次 API 请求的详细信息
 * 包括：请求方法、URI、耗时、响应状态、追踪 ID
 * 配合 JwtAuthFilter 使用，在 JwtAuthFilter 之前执行（order=0）
 */
@Slf4j
public class RequestLoggingFilter implements Filter {

  private static final String TRACE_ID_KEY = "traceId";
  private static final String REQUEST_START_KEY = "reqStart";

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                       FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    // 关键修复：不要重复生成 traceId 覆盖 TraceIdFilter 写入 MDC 的值
    // 历史 Bug：此处 UUID.randomUUID 会在 TraceIdFilter 之后执行（order=1 vs TraceIdFilter=0），
    // 覆盖 TraceIdFilter 写入的合法 traceId（客户端传入合法值时丢失），并导致同请求日志 traceId 不一致
    String traceId = MDC.get(TRACE_ID_KEY);
    if (traceId == null || traceId.isEmpty()) {
        // TraceIdFilter 未生效场景（如单元测试直接 new RequestLoggingFilter）兜底
        traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID_KEY, traceId);
    }
    MDC.put(REQUEST_START_KEY, String.valueOf(System.currentTimeMillis()));

    String method = request.getMethod();
    String uri = request.getRequestURI();
    String query = request.getQueryString();
    String fullPath = query != null ? uri + "?" + query : uri;

    try {
      // 记录请求开始
      if (log.isDebugEnabled()) {
        log.debug("→ [{}] {} [traceId={}]", method, fullPath, traceId);
      }

      chain.doFilter(request, response);
    } catch (Exception e) {
      // 记录异常（包含堆栈）
      log.error("← [{}] {} 异常: {} [traceId={}]", method, fullPath, e.getMessage(), traceId, e);
      throw e;
    } finally {
      // 计算耗时并记录响应
      String startStr = MDC.get(REQUEST_START_KEY);
      if (startStr != null) {
        long elapsed = System.currentTimeMillis() - Long.parseLong(startStr);
        int status = response.getStatus();

        // 按 HTTP 状态码分级记录
        if (elapsed > 3000) {
          log.warn("← [{}] {} → {} ({}ms) [慢请求] traceId={}", method, fullPath, status, elapsed, traceId);
        } else if (status >= 500) {
          log.error("← [{}] {} → {} ({}ms) [服务端错误] traceId={}", method, fullPath, status, elapsed, traceId);
        } else if (status >= 400) {
          log.warn("← [{}] {} → {} ({}ms) [客户端错误] traceId={}", method, fullPath, status, elapsed, traceId);
        } else {
          log.info("← [{}] {} → {} ({}ms) traceId={}", method, fullPath, status, elapsed, traceId);
        }
      }

      // 清理 MDC（UserContextHolder 由 JwtAuthFilter 负责清理）
      MDC.remove(TRACE_ID_KEY);
      MDC.remove(REQUEST_START_KEY);
    }
  }
}
