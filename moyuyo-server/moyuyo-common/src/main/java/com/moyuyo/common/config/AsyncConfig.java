package com.moyuyo.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>
 * P1 修复：RejectedExecutionHandler 增加 Prometheus 计数器（moyuyo_async_task_rejected_total），
 * 让运维第一时间感知异步任务堆积（队列满或线程池关闭）。
 * <p>
 * P2 修复：原 {@code @Autowired(required=false) setMeterRegistry} 通过 setter 注入 MeterRegistry
 * 并注册 Gauge，但执行器 Bean 在 {@code @PostConstruct} 之前初始化，setter 触发时
 * getAsyncExecutor() 已被 Spring 解析并缓存返回值，导致 Counter 永远累加但 Gauge 始终为 0。
 * 现改为通过 {@code ObjectProvider<MeterRegistry>} 在 {@link #getAsyncExecutor()} 调用时
 * 实时解析 MeterRegistry，并在自定义拒绝处理器中按需注册 Counter（Counter 天然累加语义，
 * 比 Gauge 更适合"事件计数"场景）。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** 异步任务被拒绝计数（绑定到 Prometheus Counter，天然累加语义） */
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public AsyncConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    @Bean(name = "moyuyoTaskExecutor")
    public Executor getAsyncExecutor() {
        // 在执行器初始化时即注册 Counter，确保拒绝任务累加立即被 Prometheus 抓取
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        Counter rejectedCounter = registry != null
                ? Counter.builder("moyuyo_async_task_rejected_total")
                        .description("异步任务被拒绝累计次数（队列满或线程池关闭）")
                        .register(registry)
                : null;
        // 同时维护 Gauge 用于历史兼容（部分 Grafana 看板可能已引用该指标名）
        if (registry != null) {
            registry.gauge("moyuyo_async_task_rejected_total_gauge", rejectedTasks, AtomicLong::doubleValue);
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("moyuyo-async-");
        // 拒绝策略：CallerRunsPolicy（业务可用性 > 吞吐），与 application-prod.yml 的 spring.task.execution 策略对齐
        // 任务回压到调用线程形成天然的过载保护，配合业务层限流避免雪崩
        executor.setRejectedExecutionHandler((r, exec) -> {
            long total = rejectedTasks.incrementAndGet();
            if (rejectedCounter != null) {
                rejectedCounter.increment();
            }
            log.warn("异步任务被拒绝执行：{}（累计 {} 次）。考虑调整 moyuyoTaskExecutor 容量或降低异步任务并发",
                    r.toString(), total);
            // 不再 throw RejectedExecutionException，而是 CallerRuns 让 Tomcat 业务线程同步执行兜底
            if (!exec.isShutdown()) {
                r.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("异步任务执行异常：method={}, params={}", method.getName(), params, ex);
    }
}