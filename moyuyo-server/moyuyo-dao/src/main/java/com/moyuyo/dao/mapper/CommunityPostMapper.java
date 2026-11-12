package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.CommunityPostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPostEntity> {

  /**
   * 刷新近 7 天帖子的热度分：点赞/评论加权后按时间衰减。
   * 由 CommunityHotScoreJob 定时调用，写入 hot_score 供推荐召回排序。
   */
  @Update("UPDATE mo_community_post "
      + "SET hot_score = (3 * IFNULL(likes, 0) + 5 * IFNULL(comments, 0)) "
      + "               / POW(TIMESTAMPDIFF(HOUR, create_time, NOW()) + 2, 1.5) "
      + "WHERE status = 1 AND deleted = 0 "
      + "  AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
  int refreshHotScores();
}

