package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建运费规则请求。
 * <p>
 * 与 mo_shipping_rate 表一一对应：每个 (zoneId, methodId, ACTIVE) 唯一。
 * 创建时若同 (zoneId, methodId) 已存在 ACTIVE 规则，应由 service 层返回 409。
 */
@Data
@Schema(description = "创建运费规则请求")
public class ShippingRateCreateRequest {

    @Schema(description = "发货区域 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "zoneId 不能为空")
    private Long zoneId;

    @Schema(description = "配送方式 id（关联 mo_shipping_method.id）", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "methodId 不能为空")
    private Long methodId;

    @Schema(description = "计费方式：1=按件 2=按重 3=按金额", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "chargeType 不能为空")
    @Min(value = 1, message = "chargeType 必须在 1-3 之间")
    private Integer chargeType;

    @Schema(description = "首费（USD）", example = "5.99", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @DecimalMin(value = "0", message = "首费不能为负")
    private BigDecimal firstCharge;

    @Schema(description = "首件/首重数量", example = "1")
    @Min(value = 1, message = "首件/首重必须 >= 1")
    private Integer firstUnit;

    @Schema(description = "续费（USD）", example = "2.50", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @DecimalMin(value = "0", message = "续费不能为负")
    private BigDecimal continueCharge;

    @Schema(description = "续件/续重单位（按件=件，按重=克）", example = "1")
    @Min(value = 1, message = "续件/续重单位必须 >= 1")
    private Integer continueUnit;

    @Schema(description = "满额包邮门槛（NULL=不包邮）", example = "59.00")
    private BigDecimal freeThreshold;

    @Schema(description = "货币", example = "USD")
    @Size(max = 8)
    private String currency;

    @Schema(description = "优先级", example = "10")
    private Integer priority;

    @Schema(description = "状态", example = "ACTIVE")
    @Pattern(regexp = "ACTIVE|INACTIVE|启用|停用", message = "状态必须是 ACTIVE/INACTIVE 或 启用/停用")
    private String status;

    @Schema(description = "备注")
    @Size(max = 255)
    private String remark;
}
