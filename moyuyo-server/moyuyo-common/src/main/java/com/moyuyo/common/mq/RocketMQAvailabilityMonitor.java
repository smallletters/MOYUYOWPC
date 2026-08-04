package com.moyuyo.common.mq;

/**
 * RocketMQ 可用性监视器（轻量级 Bean）
 * <p>
 * 由 RocketMQConfig 注册；业务组件可通过注入此 Bean 判断 MQ 是否可用，
 * 决定走异步通道还是降级到同步实现。
 */
public class RocketMQAvailabilityMonitor {

    private final boolean available;

    public RocketMQAvailabilityMonitor(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }
}
