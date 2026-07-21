package com.moyuyo.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate Bean 配置
 */
@Configuration
public class RestTemplateBootstrapConfig {

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
