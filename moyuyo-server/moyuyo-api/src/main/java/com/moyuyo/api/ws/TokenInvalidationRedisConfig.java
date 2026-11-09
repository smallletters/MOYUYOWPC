package com.moyuyo.api.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * token 失效事件 Redis 订阅配置。
 * <p>
 * 每个实例启动一个 RedisMessageListenerContainer，订阅 {@link TokenInvalidationBroadcaster#CHANNEL}；
 * 收到广播后关闭本实例上该用户的 WebSocket 长连接，实现多实例部署下的跨节点强制下线。
 * <p>
 * 容器由 Spring 生命周期自动 start/stop（autoStartup 默认 true），Redis 断线后自动重连。
 */
@Configuration
public class TokenInvalidationRedisConfig {

    @Bean
    public RedisMessageListenerContainer tokenInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            TokenInvalidationBroadcaster broadcaster) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> broadcaster.handleMessage(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(TokenInvalidationBroadcaster.CHANNEL));
        return container;
    }
}
