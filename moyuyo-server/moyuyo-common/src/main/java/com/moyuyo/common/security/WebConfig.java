package com.moyuyo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.filter.IpRateLimitFilter;
import com.moyuyo.common.filter.SignatureFilter;
import com.moyuyo.common.filter.TraceIdFilter;
import com.moyuyo.common.filter.UserRateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class WebConfig {

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    /**
     * TraceIdFilter 不再依赖 brave.Tracer Bean，改为纯 MDC + UUID 管理
     * 关键修复：Spring Boot 3.x 默认走 micrometer-tracing（OTel 桥接），
     * moyuyo-api 只通过 micrometer-tracing-bridge-brave 可选引入 Brave，
     * brave.Tracer Bean 在某些最小化 dev profile / 单元测试环境启动时缺失，
     * 强制注入 Tracer 会导致 WebConfig 整段链路报错。
     * <p>
     * 与 micrometer-tracing 的链路关联可通过 ObservationRegistry 显式注入（后续按需）
     */
    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    public SignatureFilter signatureFilter(ObjectMapper objectMapper,
                                           @Value("${api.signature.secret:}") String apiSecret,
                                           @Value("${api.signature.enabled:true}") boolean signatureEnabled,
                                           org.springframework.core.env.Environment environment,
                                           org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                           MeterRegistry meterRegistry) {
        // 关键修复：必须传入 Environment 让过滤器判断当前 profile（prod 环境未配置签名密钥时强制 fail-closed）。
        // 历史 Bug：原 WebConfig 仅传 (objectMapper, apiSecret)，而 SignatureFilter 构造函数需要 Environment，
        // Spring 启动时会因 NoSuchMethodError / UnsatisfiedDependencyException 直接阻断应用启动。
        // 新增 redisTemplateProvider + meterRegistry：实现 nonce 重放保护（SETNX + Prometheus 监控），
        // ObjectProvider 让 Redis 在单元测试 / 简化场景下可选缺失
        return new SignatureFilter(objectMapper, apiSecret, signatureEnabled, environment,
                redisTemplateProvider, meterRegistry);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil,
                                       ObjectMapper objectMapper,
                                       StringRedisTemplate redisTemplate,
                                       MeterRegistry meterRegistry) {
        // 注入 MeterRegistry 让 JwtAuthFilter 暴露 moyuyo_jwt_blacklist_fail_closed_total 指标
        return new JwtAuthFilter(jwtUtil, objectMapper, redisTemplate, meterRegistry);
    }

    @Bean
    public IpRateLimitFilter ipRateLimitFilter(StringRedisTemplate redisTemplate,
                                               ObjectMapper objectMapper,
                                               @Value("${moyuyo.ip-ratelimit.enabled:true}") boolean enabled,
                                               @Value("${moyuyo.ip-ratelimit.limit-per-minute:60}") int limitPerMinute,
                                               MeterRegistry meterRegistry) {
        return new IpRateLimitFilter(redisTemplate, objectMapper, enabled, limitPerMinute, meterRegistry);
    }

    @Bean
    public UserRateLimitFilter userRateLimitFilter(StringRedisTemplate redisTemplate,
                                                   ObjectMapper objectMapper,
                                                   @Value("${moyuyo.user-ratelimit.enabled:true}") boolean enabled,
                                                   @Value("${moyuyo.user-ratelimit.limit-per-minute:200}") int limitPerMinute,
                                                   MeterRegistry meterRegistry) {
        return new UserRateLimitFilter(redisTemplate, objectMapper, enabled, limitPerMinute, meterRegistry);
    }

    /**
     * 过滤器链顺序（与 README 50、59 项承诺对齐）：
     * SecurityHeaders(MIN_VALUE) < TraceId(0) < RequestLogging(1) < Signature(2) <
     *   IpRateLimit(3) < JwtAuth(4) < AdminPermission(5) < UserRateLimit(6)
     * <p>
     * 关键决策：
     * - TraceId 在最前：保证所有日志带 traceId
     * - IpRateLimit 在 JwtAuth 之前：未鉴权恶意流量被 IP 限流阻断，不浪费 JWT 验签 CPU
     * - AdminPermission 在 JwtAuth 之后：依赖 JwtAuthFilter 设置的 UserContextHolder.role
     * - UserRateLimit 在 JwtAuth 之后：依赖 UserContextHolder.getUserId() 设置
     */
    @Bean
    public FilterRegistrationBean<Filter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(traceIdFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> requestLoggingFilterRegistration(RequestLoggingFilter loggingFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(loggingFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> signatureFilterRegistration(SignatureFilter signatureFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(signatureFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> ipRateLimitFilterRegistration(IpRateLimitFilter ipRateLimitFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(ipRateLimitFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> jwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> userRateLimitFilterRegistration(UserRateLimitFilter userRateLimitFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(userRateLimitFilter);
        registration.addUrlPatterns("/api/*");
        // 关键修复：原 order=5 在 AdminPermissionFilter(order=4) 之前，依赖链断裂。
        // 现改为 order=6，在 AdminPermissionFilter(5) 之后执行，让管理员接口先做权限校验、再做用户限流
        // （admin 接口通常调用频率低，被权限拦截后不会进入限流统计）
        registration.setOrder(6);
        return registration;
    }
}
