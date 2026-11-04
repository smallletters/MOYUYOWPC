package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.CampaignUserUsageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 营销活动用户用量 Mapper
 */
@Mapper
public interface CampaignUserUsageMapper extends BaseMapper<CampaignUserUsageEntity> {

  /** 幂等初始化用户用量行，避免并发首次下单时唯一键冲突 */
  @Insert("INSERT IGNORE INTO mo_campaign_user_usage (id, campaign_id, user_id, used_count) "
      + "VALUES (#{id}, #{campaignId}, #{userId}, 0)")
  int initRow(@Param("id") Long id, @Param("campaignId") Long campaignId, @Param("userId") Long userId);

  /** 原子限购占用：仅当用户已占用件数 + 本次件数 <= 限购上限时成功 */
  @Update("UPDATE mo_campaign_user_usage SET used_count = used_count + #{count} "
      + "WHERE campaign_id = #{campaignId} AND user_id = #{userId} "
      + "AND used_count + #{count} <= #{limit}")
  int occupy(@Param("campaignId") Long campaignId, @Param("userId") Long userId,
      @Param("count") int count, @Param("limit") int limit);

  /** 释放用户已占用件数（下限 0） */
  @Update("UPDATE mo_campaign_user_usage SET used_count = GREATEST(0, used_count - #{count}) "
      + "WHERE campaign_id = #{campaignId} AND user_id = #{userId}")
  int release(@Param("campaignId") Long campaignId, @Param("userId") Long userId,
      @Param("count") int count);
}
