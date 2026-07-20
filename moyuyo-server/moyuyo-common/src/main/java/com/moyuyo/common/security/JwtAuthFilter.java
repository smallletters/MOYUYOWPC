package com.moyuyo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verify-confirm",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/products",
            "/api/v1/categories",
            "/api/v1/products/",
            "/api/health",
            "/api/admin/auth/login",
            "/api/admin/auth/logout",
            "/swagger-ui.html",
            "/swagger-ui/",
            "/api-docs",
            "/v3/api-docs"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        try {
            if (isWhiteListed(path)) {
                chain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.validate(token)) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }

            if (redisTemplate != null) {
                String blacklisted = redisTemplate.opsForValue().get(REDIS_KEY_BLACKLIST + token);
                if (blacklisted != null) {
                    sendUnauthorized(response, "Token has been revoked");
                    return;
                }
            }

            Long userId = jwtUtil.getUserId(token);
            UserContextHolder.setUserId(userId);
            UserContextHolder.setToken(token);

            chain.doFilter(request, response);

        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        Result<Void> result = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
