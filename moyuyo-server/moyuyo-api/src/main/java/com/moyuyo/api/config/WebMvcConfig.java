package com.moyuyo.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 生产环境必须通过环境变量 MOYUYO_CORS_ORIGINS 显式设置允许的前端域名
    // 默认值为空（不开放任何跨域来源），避免误配置导致 CORS 全开放
    @Value("${moyuyo.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter converter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converters.add(0, converter);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = parseOrigins(allowedOrigins);
        if (patterns.length == 0) {
            // 未配置跨域来源时不注册 CORS，避免空数组导致的意外开放
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Trace-Id", "Accept", "Origin")
                .exposedHeaders("X-Trace-Id", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 解析跨域来源配置，过滤空字符串与重复项，避免 split(",") 在空值时产生 [""] 导致误配置。
     */
    private String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .resourceChain(true);
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
