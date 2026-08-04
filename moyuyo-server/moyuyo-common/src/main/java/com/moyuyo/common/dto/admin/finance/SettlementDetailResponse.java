package com.moyuyo.common.dto.admin.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台结算详情响应
 */
@Data
public class SettlementDetailResponse {

  private Long id;

  /** 结算单号,如 SET-20260615 */
  private String settlementNo;

  /** 结算周期 */
  private String period;

  /** 总金额 */
  private BigDecimal totalAmount;

  /** 手续费(按 1% 估算) */
  private BigDecimal fee;

  /** 净额(总金额 - 手续费) */
  private BigDecimal netAmount;

  /** 状态:PENDING/COMPLETED/FAILED */
  private String status;

  /** 结算时间,无则回退到创建时间 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime settleTime;

  /** 关联订单数量 */
  private Integer orderCount;

  /** 关联订单明细列表 */
  private List<OrderSummary> orders;

  /** 结算详情中的订单摘要内嵌对象 */
  @Data
  public static class OrderSummary {
    private String orderNo;
    private BigDecimal amount;
    private BigDecimal fee;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
  }
}
