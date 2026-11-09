package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_notification")
public class NotificationEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String type;

  /** 业务类型：ORDER（订单）/ AFTER_SALE（售后），APP 据此决定点击跳转目标 */
  private String bizType;

  private String title;

  private String content;

  private Long relatedId;

  // read 是 MariaDB 保留字，用反引号转义列名
  @TableField("`read`")
  private Integer read;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
