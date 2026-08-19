package com.moyuyo.common.dto.exchange;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "换货申请请求")
public class ExchangeApplyRequest {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "原 SKU 不能为空")
    private Long oldSkuId;

    @NotNull
    @Positive(message = "原商品数量必须为正数")
    private Integer oldQuantity;

    @NotNull(message = "换新 SKU 不能为空")
    private Long newSkuId;

    @NotNull
    @Positive(message = "换新商品数量必须为正数")
    private Integer newQuantity;

    @NotBlank(message = "换货原因不能为空")
    private String reason;

    private String description;

    private String images;
}
