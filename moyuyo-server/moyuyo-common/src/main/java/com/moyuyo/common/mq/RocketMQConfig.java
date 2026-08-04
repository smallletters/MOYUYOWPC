package com.moyuyo.common.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 自动配置入口与优雅降级
 * <p>
 * 设计要点：
 * 1. 项目使用 rocketmq-spring-boot-starter 做生产者/消费者自动装配
 * 2. 当 spring.rocketmq.name-server 未配置（dev 本地无 MQ）时，禁用 RocketMQ 相关 Bean，
 *    避免阻塞应用启动；同时 OrderMessageProducer / OrderTimeoutConsumer 由 ConditionalOnProperty 守卫同步降级
 * 3. 当 ROCKETMQ_ENABLED=false 时显式关闭（用于灰度期回滚到同步模式）
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMQConfig {

    @Value("${rocketmq.name-server:}")
    private String nameServer;

    @Bean
    public RocketMQAvailabilityMonitor rocketMQAvailabilityMonitor() {
        boolean available = nameServer != null && !nameServer.isBlank();
        if (!available) {
            log.warn("[RocketMQ] rocketmq.name-server 未配置，MQ 异步能力将降级（订单超时取消等需依赖应用层兜底）");
        } else {
            log.info("[RocketMQ] 接入 NameSrv: {}", nameServer);
        }
        return new RocketMQAvailabilityMonitor(available);
    }
}
