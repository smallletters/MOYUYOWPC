package com.moyuyo;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

  @MockBean
  protected SetOperations<String, String> setOperations;

  /** 内存模拟 Redis String 存储 */
  private final Map<String, String> redisStore = new ConcurrentHashMap<>();

  /** 内存模拟 Redis Set 存储（用于 refresh token 反向索引等） */
  private final Map<String, Set<String>> redisSetStore = new ConcurrentHashMap<>();

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

    // 连接 redisTemplate.opsForSet() 到内存 mock
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    // add(key, values...) → 将值加入集合
    // 注意1：Mockito 5.x 中 any(Class) 匹配单个 varargs 元素而非整个数组，需用 any() 匹配整个 vararg
    // 注意2：SetOperations.add 返回类型为 Long，必须显式返回 long，避免 Integer 自动装箱导致 ClassCastException
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      String key = (String) args[0];
      Object valuesArg = args[1];
      Set<String> set = redisSetStore.computeIfAbsent(key, k -> new HashSet<>());
      if (valuesArg instanceof String[]) {
        for (String v : (String[]) valuesArg) {
          set.add(v);
        }
      } else if (valuesArg != null) {
        set.add(valuesArg.toString());
      }
      return (long) set.size();
    }).when(setOperations).add(anyString(), any());

    // members(key) → 返回集合所有成员
    when(setOperations.members(anyString())).thenAnswer(invocation -> {
      String key = (String) invocation.getArguments()[0];
      Set<String> set = redisSetStore.get(key);
      return set == null ? new HashSet<>() : new HashSet<>(set);
    });

    // remove(key, values...) → 从集合移除元素
    // 注意：SetOperations.remove 返回类型为 Long，必须显式返回 Long，避免拆箱/装箱导致 ClassCastException
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      String key = (String) args[0];
      Object valuesArg = args[1];
      Set<String> set = redisSetStore.get(key);
      long removed = 0;
      if (set != null) {
        if (valuesArg instanceof String[]) {
          for (String v : (String[]) valuesArg) {
            if (set.remove(v)) removed++;
          }
        } else if (valuesArg != null) {
          if (set.remove(valuesArg.toString())) removed++;
        }
      }
      return (Long) removed;
    }).when(setOperations).remove(anyString(), any());

    // delete(key) → 从内存删除（String 和 Set 都清理）
    doAnswer(invocation -> {
      String key = invocation.getArgument(0).toString();
      boolean removed = redisStore.remove(key) != null;
      removed |= redisSetStore.remove(key) != null;
      return removed;
    }).when(redisTemplate).delete(anyString());

    // delete(Collection<String> keys) → 批量删除
    doAnswer(invocation -> {
      Collection<String> keys = invocation.getArgument(0);
      long count = 0;
      for (String key : keys) {
        if (redisStore.remove(key) != null) count++;
        if (redisSetStore.remove(key) != null) count++;
      }
      return count;
    }).when(redisTemplate).delete((Collection<String>) any());

    // hasKey(key) → 检查是否存在
    when(redisTemplate.hasKey(anyString())).thenAnswer(invocation ->
      redisStore.containsKey(invocation.getArgument(0).toString())
        || redisSetStore.containsKey(invocation.getArgument(0).toString())
    );
  }
}
