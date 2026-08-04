package com.moyuyo.common.dto.admin.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台记录订单打印请求
 */
@Data
public class OrderPrintRecordRequest {

  /** 订单ID,必填 */
  @NotNull(message = "订单ID不能为空")
  private Long orderId;

  /** 打印类型:PICK(拣货单)/SHIPPING(快递单)/INVOICE(发票),空则默认 PICK */
  private String printType;

  /** 模板名称,空则默认"默认模板" */
  private String templateName;

  /** 纸张大小:A4/A5 等,空则默认 A4 */
  private String paperSize;

  /** 操作人,空则默认"系统" */
  private String operator;
}
