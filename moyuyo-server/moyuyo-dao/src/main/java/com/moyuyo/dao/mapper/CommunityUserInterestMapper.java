package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.CommunityUserInterestEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface CommunityUserInterestMapper extends BaseMapper<CommunityUserInterestEntity> {

  /**
   * 兴趣权重累加：存在则 weight + delta，不存在则插入。
   * 依赖 uk_user_dim_key(user_id, dimension, interest_key) 唯一索引。
   */
  @Insert("INSERT INTO mo_user_interest (id, user_id, dimension, interest_key, weight, updated_at) "
      + "VALUES (#{id}, #{userId}, #{dimension}, #{interestKey}, #{weight}, NOW()) "
      + "ON DUPLICATE KEY UPDATE weight = weight + VALUES(weight), updated_at = NOW()")
  int upsert(@Param("id") Long id,
             @Param("userId") Long userId,
             @Param("dimension") String dimension,
             @Param("interestKey") String interestKey,
             @Param("weight") BigDecimal weight);

  /** 每日衰减：避免历史兴趣长期主导推荐 */
  @Update("UPDATE mo_user_interest SET weight = weight * 0.95 WHERE ABS(weight) >= 0.02")
  int decayAll();

  /** 清理衰减后可忽略的权重记录 */
  @Delete("DELETE FROM mo_user_interest WHERE ABS(weight) < 0.02")
  int deleteNegligible();
}
