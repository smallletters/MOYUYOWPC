package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区帖子「不感兴趣」记录（对应 mo_community_not_interested 表）
 *
 * 仅作用于记录用户自己的推荐流，不影响帖子本身和其他用户。
 */
@Data
@TableName("mo_community_not_interested")
public class CommunityNotInterestedEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 用户ID */
  private Long userId;

  /** 被标记不感兴趣的帖子ID */
  private Long postId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
