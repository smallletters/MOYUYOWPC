package com.moyuyo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器配置
 * <p>
 * 重要约束：标注 @RateLimiter 的方法 <b>不能</b> 同时被 @Async 包装，
 * 因为：
 * <ol>
 *   <li>resilience4j 的 @RateLimiter 通过 Spring AOP 实现，需要同步调用栈才能拦截</li>
 *   <li>如果方法被 @Async 包装，AOP 拦截将在异步线程执行，限流器实例无法生效</li>
 * </ol>
 * <p>
 * 因此本类声明的 Executor 仅用于明确"非限流"业务场景（如发送邮件、异步日志），
 * 调用 @RateLimiter 方法时禁止使用此 Executor。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "moyuyoTaskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("moyuyo-async-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.warn("异步任务被拒绝执行：{}", r.toString());
        });
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("异步任务执行异常：method={}, params={}", method.getName(), params, ex);
    }
}