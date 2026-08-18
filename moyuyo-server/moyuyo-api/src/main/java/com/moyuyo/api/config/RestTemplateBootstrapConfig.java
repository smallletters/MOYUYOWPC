package com.moyuyo.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate Bean 配置
 * <p>
 * P1 修复：原实现直接 {@code new RestTemplate()}，未配置 connect/read timeout。
 * JDK 默认 HttpURLConnection 是永不超时（无限阻塞），当 WooCommerce / 第三方 API 卡死时
 * Tomcat 工作线程会被永久占满（最大 600），整个应用随即雪崩。
 * <p>
 * 修复：显式设置 connect=5s / read=15s，与 application.yml 中 moyuyo.http.* 配置对齐，
 * 并支持通过 MOYUYO_HTTP_CONNECT_TIMEOUT_MS / MOYUYO_HTTP_READ_TIMEOUT_MS 环境变量覆盖。
 */
@Configuration
public class RestTemplateBootstrapConfig {

    /** 连接建立超时（与 moyuyo.http.connect-timeout-ms 对齐） */
    @Value("${moyuyo.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /** 响应读取超时（与 moyuyo.http.read-timeout-ms 对齐） */
    @Value("${moyuyo.http.read-timeout-ms:15000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
