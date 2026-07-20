package com.moyuyo.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.SignatureUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Value("${api.signature.secret:}")
    private String apiSecret;

    private static final List<String> SKIP_PATHS = Arrays.asList(
            "/api/admin/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/health",
            "/actuator/"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 跳过无需签名的路径
        for (String skipPath : SKIP_PATHS) {
            if (path.startsWith(skipPath)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 如果没有配置签名密钥，跳过验证（开发模式）
        if (apiSecret == null || apiSecret.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String signature = request.getHeader("X-Sign");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");

        if (signature == null || timestamp == null || nonce == null) {
            sendError(response, "Missing signature headers (X-Sign, X-Timestamp, X-Nonce)");
            return;
        }

        // 检查时间戳是否在有效范围内（10分钟）
        long now = System.currentTimeMillis() / 1000;
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid X-Timestamp format");
            return;
        }
        if (Math.abs(now - requestTime) > 600) {
            sendError(response, "Request expired, check your system time");
            return;
        }

        // 签名内容: method + path + timestamp + nonce + body(如果有)
        String payload = request.getMethod() + path + timestamp + nonce;
        boolean valid = SignatureUtil.verify(payload, apiSecret, signature);
        if (!valid) {
            sendError(response, "Invalid signature");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        Result<Void> result = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
