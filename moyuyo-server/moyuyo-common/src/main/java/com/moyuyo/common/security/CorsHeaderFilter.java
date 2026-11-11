package com.moyuyo.common.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 过滤器链最前置的 CORS 响应头过滤器。
 * <p>
 * 背景：CORS 由 Spring MVC 的 {@code WebMvcConfig#addCorsMappings} 处理，但
 * JwtAuthFilter / SignatureFilter / AdminPermissionFilter 在 DispatcherServlet 之前
 * 就直接写出 401/403（以及限流 429），此时响应不带 Access-Control-Allow-Origin，
 * 浏览器会把响应整体拦掉 → H5 端 uni.request 走 fail 分支，被误报成"网络异常"。
 * <p>
 * 解决：在链路最开始就把简单请求所需的 CORS 头写到响应上，后续任何过滤器/控制器
 * 写出的响应都会带上这些头。预检(OPTIONS)仍交给 Spring MVC 统一处理，避免重复实现
 * Allow-Methods / Allow-Headers 逻辑。
 * <p>
 * 白名单与 MVC 共用同一份配置 {@code moyuyo.cors.allowed-origins}（支持 {@code *} 通配）。
 */
@Slf4j
public class CorsHeaderFilter implements Filter {

    /** 精确匹配的 origin */
    private final List<String> exactOrigins = new ArrayList<>();
    /** 含通配符的 origin 模式（如 https://*.moyuyo.com） */
    private final List<Pattern> wildcardOrigins = new ArrayList<>();

    public CorsHeaderFilter(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            log.warn("[cors] moyuyo.cors.allowed-origins 未配置，401/403 响应不会携带 CORS 头");
            return;
        }
        for (String raw : allowedOrigins.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (origin.indexOf('*') >= 0) {
                wildcardOrigins.add(toPattern(origin));
            } else {
                exactOrigins.add(origin);
            }
        }
    }

    /**
     * 通配符转正则：{@code *} 匹配任意非 / 字符（与 Spring allowedOriginPatterns 语义一致），
     * 其余字符按字面量转义，避免配置中的特殊字符被当成正则元字符。
     */
    private static Pattern toPattern(String originPattern) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : originPattern.toCharArray()) {
            if (c == '*') {
                sb.append("[^/]*");
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.append('$').toString());
    }

    private boolean isAllowed(String origin) {
        for (String allowed : exactOrigins) {
            if (allowed.equalsIgnoreCase(origin)) {
                return true;
            }
        }
        for (Pattern pattern : wildcardOrigins) {
            if (pattern.matcher(origin).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 预检请求由 Spring MVC 的 CORS 处理器统一回写 Allow-Methods / Allow-Headers
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod())) {
            applyCorsHeaders(request, response);
        }
        chain.doFilter(request, response);
    }

    /**
     * 回写简单请求所需的 CORS 头：ACAO + 允许携带凭证 + 暴露自定义响应头。
     * 不使用 {@code addHeader}，避免与 MVC 已写入的头产生重复值导致浏览器拒绝。
     */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        if (!isAllowed(origin)) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Expose-Headers", "X-Trace-Id, Content-Disposition");
        // 响应按 Origin 区分：避免代理/缓存把 A 站点的 ACAO 复用给 B 站点
        response.addHeader("Vary", "Origin");
    }
}
