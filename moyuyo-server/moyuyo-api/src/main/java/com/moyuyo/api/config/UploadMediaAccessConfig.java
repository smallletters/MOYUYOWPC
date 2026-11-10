package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.security.MediaUrlSigner;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 私信媒体访问控制过滤器注册。
 * <p>
 * 必须在 SecurityHeadersFilter 之后、且不依赖 JwtAuthFilter（后者只拦截 /api/*）。
 * order 取 10：让安全响应头先写入，再执行签名校验。
 */
@Configuration
public class UploadMediaAccessConfig {

    @Bean
    public UploadMediaAccessFilter uploadMediaAccessFilter(MediaUrlSigner mediaUrlSigner,
                                                           ObjectMapper objectMapper) {
        return new UploadMediaAccessFilter(mediaUrlSigner, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<Filter> uploadMediaAccessFilterRegistration(
            UploadMediaAccessFilter uploadMediaAccessFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(uploadMediaAccessFilter);
        // 只覆盖静态上传目录，过滤器内部再按 dm 前缀精确判断
        registration.addUrlPatterns("/uploads/*");
        registration.setOrder(10);
        return registration;
    }
}
