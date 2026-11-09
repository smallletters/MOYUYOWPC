package com.moyuyo.common.dto.aftersales;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户填写退货物流请求
 */
@Data
@Schema(description = "退货物流")
public class AfterSalesReturnRequest {

  @NotBlank(message = "退货物流公司不能为空")
  @Schema(description = "退货物流公司", example = "UPS")
  private String company;

  @NotBlank(message = "退货运单号不能为空")
  @Schema(description = "退货运单号", example = "1Z999AA10123456784")
  private String trackingNo;
}
