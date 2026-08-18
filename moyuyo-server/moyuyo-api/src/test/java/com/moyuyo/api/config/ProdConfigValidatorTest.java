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
        env.setProperty("payment.stripe.secret-key", "AbCdEf9gHiJkLmN0PqRsTuVwXyZ1aBcDe");
        env.setProperty("payment.stripe.webhook-secret", "DeFgHi1jKlMnOpQrStUvWxYzAbCdEf9g");
        env.setProperty("payment.paypal.client-id", "PayPalClientIdAbCdEfGhIjKlMnO");
        env.setProperty("payment.paypal.client-secret", "PayPalCliAbCdEfGhIjKlMnOpQrS");
        env.setProperty("payment.paypal.webhook-id", "5G90ABCDEFGHabcdef");
        env.setProperty("payment.paypal.allowed-origins", "https://moyuyo.com,https://admin.moyuyo.com");
        env.setProperty("jwt.secret", "QWJjZGVmMTIzNDU2Nzg5MEdoaWprbG1ub3BxcnN0dXZ3eHl6K0FCQ0RFRjEyMzQ1Njc4OTBnaGlqa2xt");
        env.setProperty("admin.username", "moyuyo_admin");
        env.setProperty("admin.email", "admin@moyuyo.com");
        env.setProperty("admin.password", "AdminPwdAbc123!@#XYZxyz");
        env.setProperty("api.signature.secret", "ApiSignAbCdEfGhIjKlMnOpQrStUvWx");
        env.setProperty("spring.elasticsearch.uris", "https://es-node1:9200");
        env.setProperty("spring.elasticsearch.username", "elastic");
        env.setProperty("spring.elasticsearch.password", "EsPwdAbc!@#XYZxyzAbcDefGh");
        env.setProperty("spring.datasource.password", "DbPwdAbc!@#XYZxyzAbcDefGh");
        env.setProperty("spring.data.redis.password", "RedisPwdAbc!@#XYZx");
        env.setProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword", "TrustAbc!@#XYZxyzAb");
        env.setProperty("mysql.exporter.password", "ExporterAbc!@#XYZxyzAbcDefGh");
        env.setProperty("redis.exporter.password", "RedisExpAbc!@#XYZx");
        env.setProperty("moyuyo.cors.allowed-origins", "https://admin.moyuyo.com");

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

    /**
     * P1 回归测试：合法密钥即使含 "password"/"secret"/"token"/"session"/"cookie"/"key" 等通用子串，
     * 也不应被 isWeakPlaceholder 误判。
     * <p>
     * 历史 Bug：{@code contains(weak.toUpperCase())} 误把 {@code JwtSecret_2026_Token_v2} 这类
     * 合法密钥判定为弱密钥（含 "token"/"secret" 子串）。
     * <p>
     * 修复后：占位符必须带边界（{@code YOUR_PASSWORD_HERE} / {@code _PASSWORD_HERE} 等）才算弱，
     * 子串匹配不构成占位符。
     */
    @Test
    @DisplayName("合法密钥即使含 password/secret/token 等通用子串也不应被判定为弱密钥")
    void prodProfile_legitSecretsContainingCommonSubstrings_shouldPass() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        // 含 password / secret / token / session / cookie / key 等短词的合法密钥
        env.setProperty("payment.stripe.secret-key", "sk_live_JwtSecret_2026_Token_v2_AbCdEf");
        env.setProperty("payment.stripe.webhook-secret", "whsec_AbCdEf_session_cookie_key_XyZ1");
        env.setProperty("payment.paypal.client-id", "PayPalClientIdAbCdEfGhIjKlMnO");
        env.setProperty("payment.paypal.client-secret", "PayPalCliAbCdEfGhIjKlMnOpQrS");
        env.setProperty("payment.paypal.webhook-id", "5G90ABCDEFGHabcdef");
        env.setProperty("payment.paypal.allowed-origins", "https://moyuyo.com");
        env.setProperty("jwt.secret", "QWJjZGVmMTIzNDU2Nzg5MEdoaWprbG1ub3BxcnN0dXZ3eHl6K0FCQ0RFRjEyMzQ1Njc4OTBnaGlqa2xt");
        env.setProperty("admin.username", "moyuyo_admin");
        env.setProperty("admin.email", "admin@moyuyo.com");
        env.setProperty("admin.password", "AdminPwdAbc123!@#XYZxyz");
        env.setProperty("api.signature.secret", "ApiSignToken_2026_password_key_XyZ1");
        env.setProperty("spring.elasticsearch.uris", "https://es-node1:9200");
        env.setProperty("spring.elasticsearch.username", "elastic");
        env.setProperty("spring.elasticsearch.password", "EsPwdAbc!@#XYZxyzAbcDefGh");
        env.setProperty("spring.datasource.password", "DbPwdAbc_password_secret_XyZ1");
        env.setProperty("spring.data.redis.password", "RedisPwdAbc_token_session_XyZ1");
        env.setProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword", "TrustAbc!@#XYZxyzAb");
        env.setProperty("mysql.exporter.password", "ExporterAbc!@#XYZxyzAbcDefGh");
        env.setProperty("redis.exporter.password", "RedisExpAbc!@#XYZx");
        env.setProperty("moyuyo.cors.allowed-origins", "https://admin.moyuyo.com");

        assertDoesNotThrow(() -> validator.onApplicationEvent(createEvent(env)));
    }
}
