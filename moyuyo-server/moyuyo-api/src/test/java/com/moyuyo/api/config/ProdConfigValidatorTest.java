package com.moyuyo.api.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ProdConfigValidator 单元测试
 * 验证生产环境必填配置缺失时能正确阻断启动；
 * dev/test profile 不应触发校验。
 */
class ProdConfigValidatorTest {

    private final ProdConfigValidator validator = new ProdConfigValidator();

    @AfterEach
    void cleanup() {
        System.clearProperty("spring.profiles.active");
    }

    @SuppressWarnings("deprecation")
    private ApplicationEnvironmentPreparedEvent createEvent(org.springframework.core.env.ConfigurableEnvironment env) {
        // Spring Boot 3.5.x 的 ApplicationEnvironmentPreparedEvent 构造签名：
        // (ConfigurableBootstrapContext, SpringApplication, String[], ConfigurableEnvironment)
        return new ApplicationEnvironmentPreparedEvent(
                new org.springframework.boot.DefaultBootstrapContext(),
                new org.springframework.boot.SpringApplication(),
                new String[] {},
                env);
    }

    @Test
    @DisplayName("prod profile + 密钥缺失 → 抛 IllegalStateException 阻断启动")
    void prodProfile_missingSecrets_shouldThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(createEvent(env)));
    }

    @Test
    @DisplayName("prod profile + 全部密钥正常 → 校验通过不抛异常")
    void prodProfile_allSecretsPresent_shouldPass() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("payment.stripe.secret-key", "sk_live_xxxxxxxxxxxxxxxxxxxxx");
        env.setProperty("payment.stripe.webhook-secret", "whsec_xxxxxxxxxxxxxxxxxxxxx");
        env.setProperty("payment.paypal.client-id", "paypal_client_xxxxxxxxxxxxxxxxxx");
        env.setProperty("payment.paypal.client-secret", "paypal_secret_xxxxxxxxxxxxxxxxxx");
        env.setProperty("payment.paypal.webhook-id", "WH-XXXXXXXXXXXXX");
        env.setProperty("jwt.secret", "a-very-long-jwt-secret-at-least-32-chars-long-1234");
        env.setProperty("admin.password", "a-strong-admin-password-123");
        env.setProperty("api.signature.secret", "another-very-long-api-sign-secret-1234567890");
        env.setProperty("spring.elasticsearch.uris", "https://es-node1:9200");

        assertDoesNotThrow(() -> validator.onApplicationEvent(createEvent(env)));
    }

    @Test
    @DisplayName("prod profile + JWT_SECRET 不足 32 位 → 抛 IllegalStateException")
    void prodProfile_shortJwtSecret_shouldThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("payment.stripe.secret-key", "sk_live_xxx");
        env.setProperty("payment.stripe.webhook-secret", "whsec_xxx");
        env.setProperty("payment.paypal.client-id", "id_xxx");
        env.setProperty("payment.paypal.client-secret", "secret_xxx");
        env.setProperty("payment.paypal.webhook-id", "WH-xxx");
        env.setProperty("jwt.secret", "short_jwt_secret_only_20_chars");
        env.setProperty("admin.password", "a-strong-admin-password-123");
        env.setProperty("api.signature.secret", "another-very-long-api-sign-secret-1234567890");
        env.setProperty("spring.elasticsearch.uris", "https://es-node1:9200");

        assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(createEvent(env)));
    }

    @Test
    @DisplayName("prod profile + ADMIN_PASSWORD 不足 12 位 → 抛 IllegalStateException")
    void prodProfile_shortAdminPassword_shouldThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("payment.stripe.secret-key", "sk_live_xxx");
        env.setProperty("payment.stripe.webhook-secret", "whsec_xxx");
        env.setProperty("payment.paypal.client-id", "id_xxx");
        env.setProperty("payment.paypal.client-secret", "secret_xxx");
        env.setProperty("payment.paypal.webhook-id", "WH-xxx");
        env.setProperty("jwt.secret", "a-very-long-jwt-secret-at-least-32-chars-long-1234");
        env.setProperty("admin.password", "short");
        env.setProperty("api.signature.secret", "another-very-long-api-sign-secret-1234567890");
        env.setProperty("spring.elasticsearch.uris", "https://es-node1:9200");

        assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(createEvent(env)));
    }

    @Test
    @DisplayName("dev profile 不应触发校验（即使密钥缺失）")
    void devProfile_shouldSkipValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertDoesNotThrow(() -> validator.onApplicationEvent(createEvent(env)));
    }

    @Test
    @DisplayName("test profile 不应触发校验")
    void testProfile_shouldSkipValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        assertDoesNotThrow(() -> validator.onApplicationEvent(createEvent(env)));
    }
}
