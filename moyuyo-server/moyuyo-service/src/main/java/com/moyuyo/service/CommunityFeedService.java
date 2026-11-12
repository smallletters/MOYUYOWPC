package com.moyuyo.service;

import com.moyuyo.common.dto.community.CommunityFeedPageVO;

/**
 * 社区个性化信息流。
 *
 * 推荐流：多路召回（关注/兴趣话题/热门/新鲜/探索）→ 兴趣打分 → 多样性重排 → Redis 快照分页。
 * 关注流：收件箱 + 关注关系召回，按发布时间倒序快照分页。
 */
public interface CommunityFeedService {

  /**
   * @param userId 当前用户，匿名传 null
   * @param tab    recommend / follow
   * @param cursor 上一页返回的 nextCursor，首页传空
   * @param size   每页数量（服务端限制 1~50）
   * @param topic  可选话题过滤
   */
  CommunityFeedPageVO getFeed(Long userId, String tab, String cursor, int size, String topic);

  /**
   * 失效指定用户的推荐快照（负反馈等导致排序/内容需要立即变化时调用）。
   */
  void invalidateUserSnapshots(Long userId);
}
