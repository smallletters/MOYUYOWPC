package com.moyuyo.service.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 社区兴趣事件幂等器。
 *
 * 生产者降级同步与 MQ 消费者共用同一个 eventId 去重键：
 * - MQ 发送结果不确定（回调报错但 Broker 实际已收到）时，降级同步先占位
 * - 消费者随后收到同事件会直接跳过，避免权重重复累加
 * - Redis 不可用时放行，优先保证行为不丢
 */
@Component
@RequiredArgsConstructor
public class CommunityInterestEventDeduper {

  private static final String KEY_PREFIX = "community:interest:event:";
  private static final Duration KEY_TTL = Duration.ofDays(1);

  private final StringRedisTemplate redisTemplate;

  /**
   * @return true 表示首次处理；false 表示重复事件
   */
  public boolean firstTime(String eventId) {
    if (eventId == null || eventId.isBlank()) return true;
    try {
      Boolean first = redisTemplate.opsForValue()
          .setIfAbsent(KEY_PREFIX + eventId, "1", KEY_TTL);
      return first == null || Boolean.TRUE.equals(first);
    } catch (Exception e) {
      // Redis 故障时放行，允许极少量重复
      return true;
    }
  }
}
