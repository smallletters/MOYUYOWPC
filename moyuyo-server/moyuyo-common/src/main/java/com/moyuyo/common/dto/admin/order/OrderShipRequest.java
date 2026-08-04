package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台确认发货请求
 * 字段命名与前端 carrier/trackingNo 保持一致
 * 两个字段均允许为空,空值时由服务端给出默认值(carrier=默认承运商, trackingNo="")
 */
@Data
public class OrderShipRequest {

  /** 承运商,空则使用"默认承运商" */
  private String carrier;

  /** 运单号,空则为空字符串 */
  private String trackingNo;
}
