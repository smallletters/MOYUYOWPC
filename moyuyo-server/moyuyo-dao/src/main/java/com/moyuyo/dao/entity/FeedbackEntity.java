package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_feedback")
public class FeedbackEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String type;

  private String content;

  private String images;

  private String contact;

  private String status;

  private String replyContent;

  /** 管理员回复时间（管理端处理时写入） */
  private LocalDateTime replyTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  /** 记录最后更新时间（DB 侧 ON UPDATE 自动维护，此处仅用于读取） */
  private LocalDateTime updateTime;
}
