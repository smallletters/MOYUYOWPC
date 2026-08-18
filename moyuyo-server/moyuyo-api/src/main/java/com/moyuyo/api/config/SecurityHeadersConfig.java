package com.moyuyo.api.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全响应头过滤器注册
 * order=Integer.MIN_VALUE，确保在所有业务过滤器之前执行，
 * 让安全响应头对全部响应（含被拦截器拒绝的响应）均生效。
 * <p>
 * 关键修复：SecurityHeadersFilter 使用 @Value 注入 CSP 白名单环境变量，
 * 必须通过 Spring 容器构造（@Bean）才能完成属性绑定；通过 new SecurityHeadersFilter()
 * 直接创建会丢失 @Value 注入，导致 moyuyo.security.csp.* 环境变量失效。
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public SecurityHeadersFilter securityHeadersFilter() {
        // 通过 Spring 容器创建并完成 @Value 注入（CSP 白名单 / 资源策略等）
        return new SecurityHeadersFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilterRegistration(SecurityHeadersFilter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        // 拦截所有请求，确保所有响应都带上安全头
        registration.addUrlPatterns("/*");
        // 最早执行，确保下游过滤器/拦截器返回的响应也带上安全头
        registration.setOrder(Integer.MIN_VALUE);
        // 显式声明由本配置注册：防止 SecurityHeadersFilter 误加 @Component 后被 Spring Boot
        // 默认 url-pattern=/* 自动注册一次，与本显式注册叠加导致响应头被设置两次（即便 HTTP 允许，
        // 也浪费 CPU 并给排查带来不便）。移除 @Component 后此处仅作"安全网"存在。
        // 这里返回的 bean 自身 enabled=true（默认），不再额外 setEnabled(false)，避免误禁用过滤器
        return registration;
    }
}
