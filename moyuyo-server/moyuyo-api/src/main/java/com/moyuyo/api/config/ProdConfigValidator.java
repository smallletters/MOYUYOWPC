package com.moyuyo.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境启动期必填配置校验器
 * <p>
 * 在 Spring 上下文初始化之前检查关键密钥/连接配置，任一缺失则中止启动。
 * 避免出现"运行时才报 NullPointerException / 支付网关 401"等难以诊断的问题。
 * <p>
 * 仅在 prod profile 下生效；dev/test 不校验以便本地开发。
 * <p>
 * 实现要点：通过 ApplicationEnvironmentPreparedEvent 在上下文创建前介入，
 * 直接读取 Environment，避免 @Value 尚未注入的问题。
 */
@Slf4j
public class ProdConfigValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** 必须显式设置的密钥项 (envKey, 用户提示) */
    private static final List<String[]> REQUIRED = List.of(
            new String[] { "payment.stripe.secret-key", "STRIPE_SECRET_KEY" },
            new String[] { "payment.stripe.webhook-secret", "STRIPE_WEBHOOK_SECRET" },
            new String[] { "payment.paypal.client-id", "PAYPAL_CLIENT_ID" },
            new String[] { "payment.paypal.client-secret", "PAYPAL_CLIENT_SECRET" },
            new String[] { "payment.paypal.webhook-id", "PAYPAL_WEBHOOK_ID" },
            new String[] { "jwt.secret", "JWT_SECRET" },
            new String[] { "admin.password", "ADMIN_PASSWORD" },
            new String[] { "api.signature.secret", "API_SIGN_SECRET" },
            new String[] { "spring.elasticsearch.uris", "ELASTICSEARCH_URIS" });

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();

        // 仅 prod profile 校验；dev/test 跳过
        String[] activeProfiles = env.getActiveProfiles();
        boolean isProd = java.util.Arrays.asList(activeProfiles).contains("prod");
        if (!isProd) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String[] kv : REQUIRED) {
            String key = kv[0];
            String envVar = kv[1];
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) {
                missing.add(envVar + " (对应配置: " + key + ")");
                continue;
            }
            // 长度校验
            if ("jwt.secret".equals(key) && value.length() < 32) {
                missing.add(envVar + " 长度不足 32 字符（当前 " + value.length() + "）");
            } else if ("admin.password".equals(key) && value.length() < 12) {
                missing.add(envVar + " 长度不足 12 字符（当前 " + value.length() + "）");
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("\n=========================================================\n");
            msg.append("[FATAL] 生产环境启动失败：以下必填配置缺失或非法：\n");
            for (String m : missing) {
                msg.append("  - ").append(m).append("\n");
            }
            msg.append("请在 .env 或容器环境变量中显式设置后重启。\n");
            msg.append("=========================================================");
            log.error(msg.toString());
            // 抛出异常阻断 Spring 启动上下文
            throw new IllegalStateException(msg.toString());
        }

        log.info("[prod] 启动期必填配置校验通过（{} 项）", REQUIRED.size());
    }
}
