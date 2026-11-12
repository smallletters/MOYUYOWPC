package com.moyuyo.service.mq;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.moyuyo.common.mq.CommunityInterestMessage;
import com.moyuyo.common.mq.TopicConstant;
import com.moyuyo.service.CommunityInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 社区兴趣画像消息生产者。
 *
 * 异步策略：
 * - 事务内调用时注册 afterCommit，只有业务事务真正提交才发送，避免回滚产生脏事件
 * - 按 userId 作为 hashKey 顺序发送，保证同一用户的点赞/取消点赞顺序不乱
 * - MQ 未启用、发送异常或异步回调失败时，降级为同步写库，保证行为不丢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityInterestMessageProducer {

  private static final String TOPIC = TopicConstant.COMMUNITY_INTEREST;

  @Autowired(required = false)
  private RocketMQTemplate rocketMQTemplate;

  /** 与 rocketmq.enabled 保持一致：dev 默认关闭，直接走同步降级，避免无谓的连接重试 */
  @Value("${rocketmq.enabled:false}")
  private boolean mqEnabled;

  private final CommunityInterestService interestService;
  private final CommunityInterestEventDeduper eventDeduper;

  public void onLike(Long userId, Long postId, boolean increase) {
    dispatch(CommunityInterestMessage.builder()
        .eventId(IdWorker.getIdStr())
        .userId(userId)
        .postId(postId)
        .action(increase ? CommunityInterestMessage.Action.LIKE
            : CommunityInterestMessage.Action.UNLIKE)
        .occurredAt(System.currentTimeMillis())
        .build());
  }

  public void onComment(Long userId, Long postId) {
    dispatch(CommunityInterestMessage.builder()
        .eventId(IdWorker.getIdStr())
        .userId(userId)
        .postId(postId)
        .action(CommunityInterestMessage.Action.COMMENT)
        .occurredAt(System.currentTimeMillis())
        .build());
  }

  public void onCollect(Long userId, Long postId, boolean increase) {
    dispatch(CommunityInterestMessage.builder()
        .eventId(IdWorker.getIdStr())
        .userId(userId)
        .postId(postId)
        .action(increase ? CommunityInterestMessage.Action.COLLECT
            : CommunityInterestMessage.Action.UNCOLLECT)
        .occurredAt(System.currentTimeMillis())
        .build());
  }

  public void onFollow(Long userId, Long targetId, boolean increase) {
    dispatch(CommunityInterestMessage.builder()
        .eventId(IdWorker.getIdStr())
        .userId(userId)
        .targetId(targetId)
        .action(increase ? CommunityInterestMessage.Action.FOLLOW
            : CommunityInterestMessage.Action.UNFOLLOW)
        .occurredAt(System.currentTimeMillis())
        .build());
  }

  public void onNotInterested(Long userId, Long postId) {
    dispatch(CommunityInterestMessage.builder()
        .eventId(IdWorker.getIdStr())
        .userId(userId)
        .postId(postId)
        .action(CommunityInterestMessage.Action.NOT_INTERESTED)
        .occurredAt(System.currentTimeMillis())
        .build());
  }

  /**
   * 事务内延迟到提交后发送；无事务则立即发送。
   */
  private void dispatch(CommunityInterestMessage message) {
    if (message == null || message.getUserId() == null || message.getAction() == null) return;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendOrFallback(message);
        }
      });
      return;
    }
    sendOrFallback(message);
  }

  private void sendOrFallback(CommunityInterestMessage message) {
    if (!mqEnabled || rocketMQTemplate == null) {
      log.debug("[community-interest-mq] mq disabled, fallback sync: {}", message.getAction());
      fallback(message);
      return;
    }
    try {
      // 按 userId 顺序发送，消费者也使用 ORDERLY 模式
      rocketMQTemplate.asyncSendOrderly(TOPIC, message, String.valueOf(message.getUserId()),
          new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
              log.debug("[community-interest-mq] sent: eventId={}, action={}",
                  message.getEventId(), message.getAction());
            }

            @Override
            public void onException(Throwable throwable) {
              log.warn("[community-interest-mq] send failed, fallback sync: eventId={}, reason={}",
                  message.getEventId(), throwable == null ? "unknown" : throwable.getMessage());
              fallback(message);
            }
          });
    } catch (Exception e) {
      log.warn("[community-interest-mq] send exception, fallback sync: eventId={}, reason={}",
          message.getEventId(), e.getMessage());
      fallback(message);
    }
  }

  /**
   * MQ 降级：直接同步更新兴趣画像。
   */
  private void fallback(CommunityInterestMessage message) {
    // 与消费者共用幂等键：避免"发送报错但 Broker 已收到"时降级同步 + 消费重复累加
    if (!eventDeduper.firstTime(message.getEventId())) {
      log.debug("[community-interest-mq] fallback skipped duplicated event: eventId={}",
          message.getEventId());
      return;
    }
    try {
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
    } catch (Exception e) {
      log.error("[community-interest-mq] fallback failed: eventId={}, reason={}",
          message.getEventId(), e.getMessage(), e);
    }
  }
}
