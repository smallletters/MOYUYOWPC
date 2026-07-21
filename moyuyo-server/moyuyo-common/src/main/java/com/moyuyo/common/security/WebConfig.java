package com.moyuyo.common.security;

import brave.Tracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.filter.SignatureFilter;
import com.moyuyo.common.filter.TraceIdFilter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    public TraceIdFilter traceIdFilter(Tracer tracer) {
        return new TraceIdFilter(tracer);
    }

    @Bean
    public SignatureFilter signatureFilter(ObjectMapper objectMapper, @Value("${api.signature.secret:}") String apiSecret) {
        return new SignatureFilter(objectMapper, apiSecret);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        return new JwtAuthFilter(jwtUtil, objectMapper);
    }

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
    public FilterRegistrationBean<Filter> jwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        return registration;
    }
}
