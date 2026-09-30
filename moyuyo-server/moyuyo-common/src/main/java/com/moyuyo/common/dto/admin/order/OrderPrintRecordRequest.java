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

  /**
   * 打印类型（白名单见 AdminOrderOpsController.ALLOWED_PRINT_TYPES）：
   *   PICK 拣货单 / PACK 打包单 / SHIP 发货单 / LABEL 配货标签 / SHIPPING_LABEL 快递面单。
   * 空则默认 PICK（与历史行为一致）。
   */
  private String printType;

  /** 模板名称,空则默认"默认模板" */
  private String templateName;

  /** 纸张大小:A4/A5 等,空则默认 A4 */
  private String paperSize;

  /** 操作人,空则默认"系统" */
  private String operator;
}
