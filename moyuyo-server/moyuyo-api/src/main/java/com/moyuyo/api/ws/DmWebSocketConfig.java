package com.moyuyo.api.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 私信 WebSocket 配置
 *
 * 端点：ws(s)://<host>/ws/dm?token=<JWT>
 * 注意：该路径不在 /api/* 下，因此 JwtAuthFilter 不会拦截，
 * 鉴权由 DmWebSocketAuthInterceptor 在握手阶段完成。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DmWebSocketConfig implements WebSocketConfigurer {

    private final DmWebSocketHandler dmWebSocketHandler;
    private final DmWebSocketAuthInterceptor dmWebSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dmWebSocketHandler, "/ws/dm")
                .addInterceptors(dmWebSocketAuthInterceptor)
                // 客户端来源不限制：连接已通过 JWT 鉴权，跨域站点无法伪造 token
                .setAllowedOriginPatterns("*");
    }
}
