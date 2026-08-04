package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台创建订单导出任务请求
 */
@Data
public class OrderExportCreateRequest {

  /** 任务名称,空则由服务端默认为"订单导出" */
  private String taskName;

  /** 订单范围描述,空则由服务端默认为"全部订单" */
  private String orderScope;

  /** 导出格式,空则由服务端默认为"Excel" */
  private String format;
}
