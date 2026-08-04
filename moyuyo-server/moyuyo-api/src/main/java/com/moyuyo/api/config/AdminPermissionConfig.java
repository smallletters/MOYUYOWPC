package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.service.admin.AdminPermissionService;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理端接口级权限过滤器注册
 * order=4，在 JwtAuthFilter（order=3）之后执行
 */
@Configuration
public class AdminPermissionConfig {

    @Bean
    public FilterRegistrationBean<Filter> adminPermissionFilterRegistration(
            AdminPermissionService permissionService, ObjectMapper objectMapper) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminPermissionFilter(permissionService, objectMapper));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(4);
        return registration;
    }
}
