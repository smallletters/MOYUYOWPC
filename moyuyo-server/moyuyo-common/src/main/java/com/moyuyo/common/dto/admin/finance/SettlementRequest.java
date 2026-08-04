package com.moyuyo.common.dto.admin.finance;

import lombok.Data;

/**
 * 管理后台结算记录请求(创建/更新共用)
 * 创建时 id 为空,更新时 id 由路径参数提供
 */
@Data
public class SettlementRequest {

  /** 结算周期,如 "2026-06-15" 或 "2026-06-01~2026-06-15" */
  private String period;

  /** 结算金额 */
  private Double amount;

  /** 状态:PENDING/COMPLETED/FAILED */
  private String status;

  /** 备注 */
  private String remark;

  /** 支付渠道 */
  private String payChannel;
}
