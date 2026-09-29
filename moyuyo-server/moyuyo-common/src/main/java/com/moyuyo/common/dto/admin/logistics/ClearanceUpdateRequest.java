package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新清关记录请求（partial update，所有字段均可空）。
 */
@Data
@Schema(description = "更新清关记录请求")
public class ClearanceUpdateRequest {

    @Schema(description = "报关单号")
    @Size(max = 64)
    private String declarationNo;

    @Schema(description = "关联订单号")
    @Size(max = 64)
    private String orderNo;

    @Schema(description = "商品名称")
    @Size(max = 255)
    private String productName;

    @Schema(description = "HS 编码")
    @Size(max = 32)
    private String hsCode;

    @Schema(description = "税率 (%)")
    @DecimalMin(value = "0", message = "税率不能小于 0")
    @DecimalMax(value = "100", message = "税率不能大于 100")
    private BigDecimal taxRate;

    @Schema(description = "状态：PENDING/INSPECTING/CLEARED/REJECTED")
    @Size(max = 16)
    private String status;
}