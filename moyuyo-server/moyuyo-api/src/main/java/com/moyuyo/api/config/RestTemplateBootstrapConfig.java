package com.moyuyo.api.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * 在 RestTemplateAutoConfiguration 之前注册，确保 @Primary RestTemplate 最先创建
 */
@Configuration
@AutoConfigureBefore(RestTemplateAutoConfiguration.class)
public class RestTemplateBootstrapConfig {

  @Bean(name = {"restTemplate", "ecommRestTemplate"})
  @Primary
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
