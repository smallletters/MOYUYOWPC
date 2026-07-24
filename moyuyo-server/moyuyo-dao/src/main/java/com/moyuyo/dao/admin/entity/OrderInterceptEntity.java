package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单拦截记录实体（对应 mo_order_intercept 表）
 */
@Data
@TableName("mo_order_intercept")
public class OrderInterceptEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 订单ID */
  private Long orderId;

  /** 订单号 */
  private String orderNo;

  /** 拦截类型: RISK(风控)/MANUAL(人工)/SYSTEM(系统) */
  private String interceptType;

  /** 拦截原因 */
  private String reason;

  /** 拦截原因模板 */
  private String reasonTemplate;

  /** 操作人 */
  private String operator;

  /** 状态: ACTIVE/RELEASED */
  private String status;

  /** 解除原因 */
  private String releaseReason;

  /** 解除操作人 */
  private String releaseOperator;

  /** 解除时间 */
  private LocalDateTime releaseTime;

  /** 是否通知用户: 0否 1是 */
  private Integer notifyUser;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
