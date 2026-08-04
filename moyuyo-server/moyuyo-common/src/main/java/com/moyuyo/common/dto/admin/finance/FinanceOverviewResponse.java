package com.moyuyo.common.dto.admin.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理后台财务概览响应
 */
@Data
public class FinanceOverviewResponse {

  /** 总收入(月度 GMV) */
  private BigDecimal totalRevenue;

  /** 实际收入 */
  private BigDecimal actualIncome;

  /** 待结算金额 */
  private BigDecimal pendingSettlement;

  /** 已完成结算数量 */
  private Integer completedSettlements;

  /** 待处理事项数量 */
  private Integer pendingCount;

  /** 退款金额 */
  private BigDecimal refundAmount;

  /** 渠道分布,每项包含 channel/amount 等键 */
  private List<Map<String, Object>> channelDistribution;
}
