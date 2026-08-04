package com.moyuyo.api.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全响应头过滤器注册
 * order=Integer.MIN_VALUE，确保在所有业务过滤器之前执行，
 * 让安全响应头对全部响应（含被拦截器拒绝的响应）均生效。
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        // 拦截所有请求，确保所有响应都带上安全头
        registration.addUrlPatterns("/*");
        // 最早执行，确保下游过滤器/拦截器返回的响应也带上安全头
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}
