package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 社区用户兴趣画像（对应 mo_user_interest 表）。
 *
 * dimension 取 topic / author，interest_key 分别为话题名或作者用户 ID。
 * 权重由点赞、收藏、评论、关注、负反馈等行为累加，推荐排序时读取。
 */
@Data
@TableName("mo_user_interest")
public class CommunityUserInterestEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  /** topic / author */
  private String dimension;

  private String interestKey;

  private BigDecimal weight;

  private LocalDateTime updatedAt;
}
