package com.moyuyo.service;

import java.util.Map;

/**
 * 社区用户兴趣画像服务。
 *
 * 行为信号（点赞/收藏/评论/关注/不感兴趣）会累加到 mo_user_interest，
 * 推荐流按 topic / author 维度读取权重参与排序。
 */
public interface CommunityInterestService {

  /** 点赞 / 取消点赞 */
  void onLike(Long userId, Long postId, boolean increase);

  /** 发表评论 */
  void onComment(Long userId, Long postId);

  /** 收藏 / 取消收藏 */
  void onCollect(Long userId, Long postId, boolean increase);

  /** 关注 / 取消关注作者（targetId 为被关注者用户 ID） */
  void onFollow(Long userId, Long targetId, boolean increase);

  /** 标记不感兴趣（负反馈） */
  void onNotInterested(Long userId, Long postId);

  /**
   * 读取某维度兴趣权重。
   *
   * @param dimension topic / author
   * @return key -> weight；无数据返回空 Map
   */
  Map<String, Double> weights(Long userId, String dimension);

  /** 当前用户推荐版本号（Redis 自增，兴趣变化时递增） */
  long feedVersion(Long userId);
}
