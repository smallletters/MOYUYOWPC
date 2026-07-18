package com.moyuyo.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "woocommerce")
public class WooCommerceProperties {

    private String url;
    private String consumerKey;
    private String consumerSecret;
    private int connectTimeout = 10000;
    private int readTimeout = 30000;
}
