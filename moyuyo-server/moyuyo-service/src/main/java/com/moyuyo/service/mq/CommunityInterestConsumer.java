package com.moyuyo.service.mq;

import com.moyuyo.common.mq.CommunityInterestMessage;
import com.moyuyo.common.mq.TopicConstant;
import com.moyuyo.service.CommunityInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 社区兴趣画像消费者。
 *
 * - ORDERLY 消费：与生产者的 userId hashKey 配合，保证同一用户行为顺序
 * - Redis SETNX 幂等：同一 eventId 只处理一次，避免 MQ 重投导致权重重复累加
 * - 画像更新失败只记录日志，不影响消息消费位点（兴趣属弱一致数据）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
    topic = TopicConstant.COMMUNITY_INTEREST,
    consumerGroup = TopicConstant.CONSUMER_GROUP_COMMUNITY_INTEREST,
    messageModel = MessageModel.CLUSTERING,
    consumeMode = ConsumeMode.ORDERLY
)
public class CommunityInterestConsumer implements RocketMQListener<CommunityInterestMessage> {

  private final CommunityInterestService interestService;
  private final CommunityInterestEventDeduper eventDeduper;

  @Override
  public void onMessage(CommunityInterestMessage message) {
    if (message == null || message.getUserId() == null || message.getAction() == null) {
      log.warn("[community-interest-mq] invalid message, skipped: {}", message);
      return;
    }
    if (!eventDeduper.firstTime(message.getEventId())) {
      log.debug("[community-interest-mq] duplicated event skipped: eventId={}", message.getEventId());
      return;
    }
    // 本地重试：兴趣写库偶发抖动时快速重试，避免直接丢事件；重试仍失败则放弃，不阻塞有序消费队列
    Exception lastError = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        apply(message);
        return;
      } catch (Exception e) {
        lastError = e;
        if (attempt < 3) {
          try {
            Thread.sleep(100L * attempt);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    log.error("[community-interest-mq] apply failed after retries: eventId={}, action={}, reason={}",
        message.getEventId(), message.getAction(),
        lastError == null ? "unknown" : lastError.getMessage(), lastError);
  }

  private void apply(CommunityInterestMessage message) {
    Long userId = message.getUserId();
    switch (message.getAction()) {
      case LIKE:
        interestService.onLike(userId, message.getPostId(), true);
        break;
      case UNLIKE:
        interestService.onLike(userId, message.getPostId(), false);
        break;
      case COMMENT:
        interestService.onComment(userId, message.getPostId());
        break;
      case COLLECT:
        interestService.onCollect(userId, message.getPostId(), true);
        break;
      case UNCOLLECT:
        interestService.onCollect(userId, message.getPostId(), false);
        break;
      case FOLLOW:
        interestService.onFollow(userId, message.getTargetId(), true);
        break;
      case UNFOLLOW:
        interestService.onFollow(userId, message.getTargetId(), false);
        break;
      case NOT_INTERESTED:
        interestService.onNotInterested(userId, message.getPostId());
        break;
      default:
        break;
    }
  }
}
