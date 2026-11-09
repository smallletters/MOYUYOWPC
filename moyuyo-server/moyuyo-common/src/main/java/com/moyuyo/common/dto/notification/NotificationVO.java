package com.moyuyo.common.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知 VO（C 端返回）
 * <p>
 * 相比实体去掉了 userId：前端只需要展示与跳转信息，无需暴露归属用户主键。
 */
@Data
@Schema(description = "通知")
public class NotificationVO {

  private Long id;

  /** 通知分类：ORDER / ACTIVITY / SYSTEM，对应 APP 通知中心 tab */
  private String type;

  /** 业务类型：ORDER（订单）/ AFTER_SALE（售后），APP 点击跳转依据 */
  private String bizType;

  private String title;

  private String content;

  /** 关联业务主键（订单 ID / 售后单 ID） */
  private Long relatedId;

  /** 0 未读 / 1 已读 */
  private Integer read;

  private LocalDateTime createTime;
}
