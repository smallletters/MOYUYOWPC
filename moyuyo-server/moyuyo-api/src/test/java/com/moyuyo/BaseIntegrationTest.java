package com.moyuyo;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 集成测试基类 — 使用内存 Map 模拟 Redis，替代 RETURNS_DEEP_STUBS
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

  @MockBean
  protected StringRedisTemplate redisTemplate;

  @MockBean
  protected ValueOperations<String, String> valueOperations;

  /** 内存模拟 Redis 存储 */
  private final Map<String, String> redisStore = new ConcurrentHashMap<>();

  @BeforeEach
  void setUpRedisMock() {
    // 连接 redisTemplate.opsForValue() 到内存 mock
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // set(key, value) → 写入内存
    doAnswer(invocation -> {
      redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(valueOperations).set(anyString(), anyString());

    // set(key, value, timeout, unit) → 写入内存（忽略超时）
    doAnswer(invocation -> {
      redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

    // get(key) → 从内存读取
    when(valueOperations.get(anyString())).thenAnswer(invocation ->
      redisStore.get(invocation.getArgument(0).toString())
    );

    // delete(key) → 从内存删除
    doAnswer(invocation -> {
      redisStore.remove(invocation.getArgument(0).toString());
      return null;
    }).when(redisTemplate).delete(anyString());

    // hasKey(key) → 检查是否存在
    when(redisTemplate.hasKey(anyString())).thenAnswer(invocation ->
      redisStore.containsKey(invocation.getArgument(0).toString())
    );
  }
}
