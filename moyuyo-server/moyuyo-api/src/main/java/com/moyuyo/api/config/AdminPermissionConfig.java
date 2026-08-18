package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.service.admin.AdminPermissionService;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理端接口级权限过滤器注册
 * <p>
 * 过滤器链顺序（与 WebConfig 保持联动）：
 *   SecurityHeaders(MIN_VALUE) < TraceId(0) < RequestLogging(1) < Signature(2)
 *   < IpRateLimit(3) < JwtAuth(4) < AdminPermission(5) < UserRateLimit(6)
 * <p>
 * 关键修复：原 order=4 与 JwtAuthFilter 同 order，Spring 同 order 时按 Bean 注册顺序执行，
 * 顺序不确定——AdminPermissionFilter 可能在 JwtAuthFilter 之前运行，导致 UserContextHolder.getRole() 始终为 null，
 * 任何非 SUPER_ADMIN 角色都会被错误地拒绝为 "Admin role required"。
 * 现改为 order=5，确保 JwtAuthFilter 先完成角色设置。
 */
@Configuration
public class AdminPermissionConfig {

    @Bean
    public FilterRegistrationBean<Filter> adminPermissionFilterRegistration(
            AdminPermissionService permissionService, ObjectMapper objectMapper) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminPermissionFilter(permissionService, objectMapper));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(5);
        return registration;
    }
}
