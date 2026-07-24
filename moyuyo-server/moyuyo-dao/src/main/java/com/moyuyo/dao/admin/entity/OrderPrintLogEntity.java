package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单打印记录实体（对应 mo_order_print_log 表）
 */
@Data
@TableName("mo_order_print_log")
public class OrderPrintLogEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 订单ID */
  private Long orderId;

  /** 订单号 */
  private String orderNo;

  /** 打印类型: PICK(拣货单)/PACK(打包单)/SHIP(发货单)/LABEL(配货标签) */
  private String printType;

  /** 模板名称 */
  private String templateName;

  /** 纸张规格 */
  private String paperSize;

  /** 操作人 */
  private String operator;

  /** 打印次数 */
  private Integer printCount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
