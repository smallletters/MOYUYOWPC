package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_community_topic_v2")
public class CommunityTopicV2Entity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String name;

  private String description;

  private String coverImage;

  private Integer postCount;

  private Integer followCount;

  private Integer viewCount;

  private Integer hot;

  private Integer sortOrder;

  private Integer active;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}