package com.moyuyo.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社区兴趣画像更新消息。
 *
 * 业务侧只负责投递行为事件，兴趣权重由消费者统一累加，
 * 避免点赞/评论等主链路被画像写库拖慢。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityInterestMessage {

  /** 事件 ID：消费者用于幂等去重 */
  private String eventId;

  /** 触发行为的用户 */
  private Long userId;

  /** 帖子 ID（关注类事件可为空） */
  private Long postId;

  /** 目标用户 ID（关注/取关时使用） */
  private Long targetId;

  /** 行为类型 */
  private Action action;

  /** 事件发生时间（毫秒），用于排查乱序 */
  private Long occurredAt;

  public enum Action {
    LIKE,
    UNLIKE,
    COMMENT,
    COLLECT,
    UNCOLLECT,
    FOLLOW,
    UNFOLLOW,
    NOT_INTERESTED
  }
}
