package com.moyuyo.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求日志过滤器 - 记录每次 API 请求的详细信息
 * 包括：请求方法、URI、耗时、响应状态、追踪 ID
 * 配合 JwtAuthFilter 使用，在 JwtAuthFilter 之前执行（order=0）
 */
@Slf4j
@Component
public class RequestLoggingFilter implements Filter {

  private static final String TRACE_ID_KEY = "traceId";
  private static final String REQUEST_START_KEY = "reqStart";

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                       FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    // 生成追踪 ID 并放入 MDC（日志上下文），JwtAuthFilter 内也可通过 MDC 获取
    String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    MDC.put(TRACE_ID_KEY, traceId);
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
