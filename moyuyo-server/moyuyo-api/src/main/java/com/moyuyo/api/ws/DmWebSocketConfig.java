package com.moyuyo.api.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 私信 WebSocket 配置
 *
 * 端点：ws(s)://<host>/ws/dm?ticket=<一次性票据>
 * 注意：该路径不在 /api/* 下，因此 JwtAuthFilter 不会拦截，
 * 鉴权由 DmWebSocketAuthInterceptor 在握手阶段完成。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DmWebSocketConfig implements WebSocketConfigurer {

    private final DmWebSocketHandler dmWebSocketHandler;
    private final DmWebSocketAuthInterceptor dmWebSocketAuthInterceptor;

    /**
     * 允许的 Origin：复用 CORS 白名单（moyuyo.cors.allowed-origins）。
     * 未配置时保持通配，兼容 dev 本地直连与原生 APP（握手不带 Origin 头），
     * 生产环境配置该变量后浏览器端握手即收敛到业务域名白名单。
     */
    @Value("${moyuyo.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dmWebSocketHandler, "/ws/dm")
                .addInterceptors(dmWebSocketAuthInterceptor)
                // 鉴权已由一次性 ticket 完成；这里再按 Origin 白名单收紧浏览器侧攻击面
                .setAllowedOriginPatterns(resolveAllowedOriginPatterns());
    }

    /** 解析 Origin 白名单：未配置或配置为空时回退通配，避免误配置导致握手全量失败 */
    private String[] resolveAllowedOriginPatterns() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return new String[] { "*" };
        }
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * WebSocket 容器参数（Tomcat / Jetty 通用）。
     * <p>
     * 空闲超时：客户端每 25s 发一次心跳，连续 3 次未到达（90s）即判定连接已死并关闭。
     * 不配置时半开连接（移动网络切换、客户端被系统杀掉）会一直挂在注册表里，
     * 表现为"在线连接数虚高 + 内存缓慢泄漏"。
     * <p>
     * 文本缓冲上限：私信只走心跳这一种上行文本，16KB 足够，避免异常大帧占内存。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(90_000L);
        container.setMaxTextMessageBufferSize(16 * 1024);
        return container;
    }
}
