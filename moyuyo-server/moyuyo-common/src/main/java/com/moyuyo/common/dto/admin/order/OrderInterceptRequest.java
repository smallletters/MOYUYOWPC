package com.moyuyo.common.dto.admin.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台创建订单拦截请求
 */
@Data
public class OrderInterceptRequest {

  /** 订单ID,必填 */
  @NotNull(message = "订单ID不能为空")
  private Long orderId;

  /** 拦截类型:MANUAL(人工)/SYSTEM(系统)/RISK(风控),空则默认 MANUAL */
  private String interceptType;

  /** 拦截原因 */
  private String reason;

  /** 拦截原因模板ID/名称 */
  private String reasonTemplate;

  /** 操作人,空则默认"系统" */
  private String operator;
}
