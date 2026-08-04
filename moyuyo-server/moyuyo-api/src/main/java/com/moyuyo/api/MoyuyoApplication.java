package com.moyuyo.api;

import com.moyuyo.common.config.WooCommerceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MOYUYO 后端服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.moyuyo", exclude = {
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties(WooCommerceProperties.class)
@EnableAsync
@EnableScheduling
public class MoyuyoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyuyoApplication.class, args);
    }
}
