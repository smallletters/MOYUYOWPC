package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.MarketingCampaignEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 营销活动 Mapper
 */
@Mapper
public interface MarketingCampaignMapper extends BaseMapper<MarketingCampaignEntity> {

  /**
   * 原子占用活动额度。
   * limitCount<=0 表示不限件数；limitAmount<=0 表示不限金额。
   */
  @Update("UPDATE mo_marketing_campaign SET "
      + "used_count = COALESCE(used_count, 0) + #{count}, "
      + "used_amount = COALESCE(used_amount, 0) + #{amount} "
      + "WHERE id = #{id} "
      + "AND (#{limitCount} <= 0 OR COALESCE(used_count, 0) + #{count} <= #{limitCount}) "
      + "AND (#{limitAmount} <= 0 OR COALESCE(used_amount, 0) + #{amount} <= #{limitAmount})")
  int occupy(@Param("id") Long id, @Param("count") int count, @Param("amount") BigDecimal amount,
      @Param("limitCount") int limitCount, @Param("limitAmount") BigDecimal limitAmount);

  /** 取消订单时回补活动额度（下限 0） */
  @Update("UPDATE mo_marketing_campaign SET "
      + "used_count = GREATEST(0, COALESCE(used_count, 0) - #{count}), "
      + "used_amount = GREATEST(0, COALESCE(used_amount, 0) - #{amount}) "
      + "WHERE id = #{id}")
  int release(@Param("id") Long id, @Param("count") int count, @Param("amount") BigDecimal amount);
}

