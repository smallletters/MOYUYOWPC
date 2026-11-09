package com.moyuyo.common.dto.aftersales;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后申请请求
 * <p>
 * 对齐亚马逊 APP 的 "Return or replace items" 流程：
 * 选择商品与数量 → 选择原因 → 选择解决方案（退货退款/仅退款/换货）→ 提交。
 */
@Data
@Schema(description = "售后申请请求")
public class AfterSalesCreateRequest {

  @NotNull(message = "订单ID不能为空")
  @Schema(description = "订单ID", example = "1")
  private Long orderId;

  @Schema(description = "订单项ID（按商品售后时必填；仅退款可整单申请）", example = "1")
  private Long orderItemId;

  @NotBlank(message = "售后类型不能为空")
  @Schema(description = "售后类型: RETURN_REFUND(退货退款) / REFUND_ONLY(仅退款) / EXCHANGE(换货)",
      example = "RETURN_REFUND")
  private String type;

  @NotBlank(message = "售后原因不能为空")
  @Schema(description = "售后原因", example = "商品损坏")
  private String reason;

  @Schema(description = "申请退款金额（不传则按商品可退金额自动计算）", example = "25.00")
  private BigDecimal amount;

  @Schema(description = "售后数量（不传默认 1）", example = "1")
  private Integer quantity;

  @Schema(description = "问题描述")
  private String description;

  @Schema(description = "凭证图片 URL 列表(JSON)", example = "[\"/uploads/2026/11/09/a.jpg\"]")
  private String images;
}
