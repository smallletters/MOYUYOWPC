package com.moyuyo.service.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 物流轨迹 API 配置
 * <p>
 * 对应 yml 中 {@code moyuyo.logistics.*} 配置项。
 * 未配置 provider 时默认 {@code none}，由 {@code NoopLogisticsTrackProvider} 接管，仅人工维护轨迹。
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "moyuyo.logistics")
public class LogisticsTrackProperties {

    /**
     * 提供商标识：none / 17track / kuaidi100 / aftership
     */
    private String provider = "none";

    /**
     * API 密钥（17TRACK / aftership 必填）
     */
    private String apiKey;

    /**
     * 快递100 customer id（仅 provider=kuaidi100 时必填）
     */
    private String customer;

    /**
     * 轨迹拉取间隔（分钟）
     */
    private int pollIntervalMinutes = 30;

    /**
     * 单次拉取最大运单数
     */
    private int pollBatchSize = 100;

    /**
     * HTTP 连接超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * HTTP 读取超时（毫秒）
     */
    private int readTimeoutMs = 15000;

    /**
     * 启动期校验：当 provider 非 none 时，对应必填项缺失则阻断启动
     * <p>
     * 校验规则：
     * <ul>
     *   <li>mock：无需任何配置，仅用于本地开发测试</li>
     *   <li>17track / aftership：apiKey 必填</li>
     *   <li>kuaidi100：apiKey + customer 均必填</li>
     * </ul>
     */
    @PostConstruct
    public void validate() {
        if ("none".equalsIgnoreCase(provider)) {
            log.info("Logistics track provider=none, using NoopLogisticsTrackProvider (manual maintenance only)");
            return;
        }
        switch (provider.toLowerCase()) {
            case "mock" -> {
                // Mock provider 不需要任何配置，仅用于本地开发测试
                // 生产环境禁用：建议在 application-prod.yml 显式覆盖为 none 或其他真实 provider
                log.warn("Logistics track provider=mock enabled (FOR DEV/TEST ONLY)");
            }
            case "17track", "aftership" -> {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "moyuyo.logistics.api-key is required when provider=" + provider
                                    + " (env: MOYUYO_LOGISTICS_API_KEY)");
                }
                log.info("Logistics track provider={} enabled, apiKey configured", provider);
            }
            case "kuaidi100" -> {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "moyuyo.logistics.api-key is required when provider=kuaidi100 "
                                    + "(env: MOYUYO_LOGISTICS_API_KEY)");
                }
                if (customer == null || customer.isBlank()) {
                    throw new IllegalStateException(
                            "moyuyo.logistics.customer is required when provider=kuaidi100 "
                                    + "(env: MOYUYO_LOGISTICS_CUSTOMER)");
                }
                log.info("Logistics track provider=kuaidi100 enabled, apiKey and customer configured");
            }
            default -> throw new IllegalStateException(
                    "Unsupported logistics provider: " + provider
                            + " (expected: none / mock / 17track / kuaidi100 / aftership)");
        }
    }
}
