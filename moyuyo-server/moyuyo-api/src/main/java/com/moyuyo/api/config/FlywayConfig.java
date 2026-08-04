package com.moyuyo.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway 启动策略配置
 * <p>
 * 仅在 dev profile 下启用 flyway.repair()，用于修复开发过程中因手动改 SQL 导致的
 * checksum 不一致问题。生产环境严禁执行 repair()，以免历史迁移错位导致数据漂移。
 * 生产环境依赖 application-prod.yml 中 repair-on-migrate: false，由运维手工处理异常。
 */
@Slf4j
@Configuration
@Profile("dev")
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy() {
        return flyway -> {
            log.warn("[dev] 执行 flyway.repair()，仅限本地开发使用，生产环境禁用");
            flyway.repair();
            flyway.migrate();
        };
    }
}
