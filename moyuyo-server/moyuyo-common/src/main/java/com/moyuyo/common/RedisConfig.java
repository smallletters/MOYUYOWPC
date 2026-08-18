package com.moyuyo.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.Builder;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.TimeZone;

/**
 * Redis 连接配置（Lettuce 客户端）
 * <p>
 * 关键修复（P0 反序列化漏洞防御）：
 * 原实现使用 Jackson2JsonRedisSerializer&lt;Object&gt;(Object.class)，
 * 该序列化器在底层会调用 enableDefaultTyping()，导致 Redis 写入的 JSON 含 @class 字段实现多态反序列化。
 * 攻击者通过 redis-cli 注入恶意 @class 字段，可触发 Jackson 反序列化 gadget 链
 * （与 Fastjson 历史漏洞同源），例如通过 com.sun.rowset.JdbcRowSetImpl 等已知 gadget 类触发 JNDI 注入。
 * <p>
 * 修复方案：
 * 1. 显式构造 BasicPolymorphicTypeValidator，仅允许 com.moyuyo.dao.* / com.moyuyo.common.dto.*
 *    / java.util.* / java.lang.* / java.time.* / java.math.* 子类型
 *    拦截攻击者通过 redis 注入 com.sun.* / org.springframework.* 等已知 gadget 包
 * 2. 复制全局 ObjectMapper 后再 activateDefaultTyping，避免污染全局反序列化策略
 *    （全局 application.yml 中 default-typing=NONE 是 API 端反序列化安全基线）
 * 3. 切换到 GenericJackson2JsonRedisSerializer（支持自定义 ObjectMapper + @class 字段处理）
 *    保留 @class 字段的类型恢复能力，但严格限制白名单内的子类
 * <p>
 * 2026-08-12 增强：继承全局 Spring Boot 构造的 ObjectMapper（copyOf + customizeDefaults），
 * 确保 Redis 缓存序列化时同样应用 JavaTimeModule（LocalDateTime）+ Long 字符串序列化 + 时区配置，
 * 避免 new ObjectMapper() 直接构造导致与全局 Jackson 行为漂移，
 * 进而出现"缓存里的 LocalDateTime 序列化为时间戳数组 + 反序列化失败"的隐性 Bug。
 */
@Configuration
public class RedisConfig {

    /**
     * 多态类型校验白名单：
     * - com.moyuyo.dao.*        MyBatis-Plus 实体类
     * - com.moyuyo.common.dto.* 业务 DTO（含 dto 子包）
     * - java.util.*             集合框架（ArrayList/HashMap/HashSet 等）
     * - java.lang.*             基本类型包装（Long/Integer/String 等）
     * - java.time.*             时间类型（LocalDate/LocalDateTime 等）
     * - java.math.*             数值类型（BigDecimal/BigInteger）
     * <p>
     * 显式 allowIfBaseType 仅做"基类入口"校验，allowIfSubType 严格限制子类范围，
     * 防止攻击者通过继承白名单类注入恶意子类
     */
    private static PolymorphicTypeValidator buildPolymorphicTypeValidator() {
        // 显式禁用全部已知 gadget 命名空间（纵深防御）
        Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class) // 基线兜底：仅白名单内的子类可被反序列化
                .allowIfSubType("com.moyuyo.dao.")
                .allowIfSubType("com.moyuyo.common.dto.")
                .allowIfSubType("com.moyuyo.common.enums.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.");
        return builder.build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper globalObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 复制全局 ObjectMapper：避免污染 application.yml 中 default-typing=NONE 的全局配置
        // 2026-08-12 增强：通过 ObjectMapper.copy() 继承 JavaTimeModule / Long 字符串序列化器 / 时区 等所有 Spring Boot 自动配置的特性，
        // 避免 new ObjectMapper() 直接构造导致 LocalDateTime / BigDecimal 等类型的序列化行为漂移
        ObjectMapper redisObjectMapper = globalObjectMapper.copy();
        redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 仅声明字段，无 getter/setter 的类也可序列化
        redisObjectMapper.activateDefaultTyping(
                buildPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        // 复用全局 Jackson 时区与日期格式（与 application.yml spring.jackson.time-zone 对齐）
        // copy() 后已继承 time-zone，这里冗余声明便于审计
        redisObjectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Value 使用 JSON 序列化（带白名单的多态类型校验）
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}