package com.moyuyo.api;

import com.moyuyo.common.config.WooCommerceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MOYUYO 后端服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.moyuyo")
@EnableConfigurationProperties(WooCommerceProperties.class)
public class MoyuyoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyuyoApplication.class, args);
    }
}
