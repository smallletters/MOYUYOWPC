package com.moyuyo.common.dto.refund;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款申请请求
 * 支持两种场景：
 * 1) 整单退款：仅传 orderId + type=FULL + amount（可选，不传则取订单实付）
 * 2) 拆单退款：传 items 数组指定每个 SKU 的退款数量与金额（type=PARTIAL 强制要求）
 */
@Data
@Schema(description = "退款申请请求")
public class RefundApplyRequest {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @NotBlank(message = "退款类型不能为空")
    @Schema(description = "退款类型: FULL（全单）/ PARTIAL（拆单）", example = "PARTIAL")
    private String type;

    @Schema(description = "退款金额（type=FULL 且不传时取订单实付金额）", example = "25.00")
    private BigDecimal amount;

    @NotBlank(message = "退款原因不能为空")
    @Schema(description = "退款原因", example = "Item damaged")
    private String reason;

    @Schema(description = "问题描述")
    private String description;

    @Schema(description = "图片URL列表(JSON)", example = "[\"https://...\"]")
    private String images;

    /**
     * 拆单退款的子项明细（type=PARTIAL 时必填）
     * 每个子项必须属于订单，且 amount 合计 = 请求 amount
     */
    @Schema(description = "拆单退款子项（type=PARTIAL 时必填）")
    private List<RefundItem> items;

    @Data
    @Schema(description = "退款子项")
    public static class RefundItem {
        @NotNull(message = "SKU ID 不能为空")
        private Long skuId;

        @Schema(description = "退款数量", example = "1")
        private Integer quantity;

        @Schema(description = "该子项退款金额", example = "25.00")
        private BigDecimal amount;

        @Schema(description = "该子项退款原因（可与主原因不同）")
        private String reason;
    }
}
