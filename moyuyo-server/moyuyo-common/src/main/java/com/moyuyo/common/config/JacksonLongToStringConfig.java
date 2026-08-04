package com.moyuyo.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * <p>
 * 雪花 ID（MyBatis-Plus ASSIGN_ID）为 19 位 Long，超出 JavaScript Number.MAX_SAFE_INTEGER (2^53)，
 * 若按数字序列化，前端 JS 解析时会发生精度丢失（如 2080199390045450241 -> 2080199390045450200），
 * 导致点击"上架/编辑"等操作时携带错误的 ID，后端返回"商品不存在"。
 * 解决方案：将 Long / long 统一序列化为字符串，保证 ID 精确传递。
 */
@Configuration
public class JacksonLongToStringConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
    return builder -> builder
        .serializerByType(Long.class, ToStringSerializer.instance)
        .serializerByType(Long.TYPE, ToStringSerializer.instance);
  }
}
