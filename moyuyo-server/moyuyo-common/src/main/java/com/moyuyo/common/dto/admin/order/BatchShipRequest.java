package com.moyuyo.common.dto.admin.order;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 管理后台批量发货请求
 * 支持批量传入订单ID,统一指定承运商和运单号
 */
@Data
public class BatchShipRequest {

  /** 待发货订单ID列表,允许 Number 或字符串形式的雪花ID */
  @NotEmpty(message = "订单ID列表不能为空")
  private List<Long> ids;

  /** 承运商,空则使用"默认承运商" */
  private String carrier;

  /** 运单号,空则为空字符串 */
  private String trackingNo;
}
