package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建发货策略请求
 * <p>
 * 同时支持新旧字段名（strategyName/shippingMethod/feeRule 与 name/method/ruleDesc），
 * 在 {@code AdminShippingStrategyService} 内部做兼容归一化。
 */
@Data
@Schema(description = "创建发货策略请求")
public class ShippingStrategyCreateRequest {

    @Schema(description = "策略名称（新字段名）", example = "美国标准快递")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    private String strategyName;

    @Schema(description = "策略名称（旧字段名兼容）")
    @Size(max = 128)
    private String name;

    @Schema(description = "适用区域（展示文案，自由文本）", example = "华东地区")
    @Size(max = 64)
    private String region;

    @Schema(description = "所属发货区域 id", example = "1")
    @NotNull(message = "zoneId 不能为空")
    private Long zoneId;

    @Schema(description = "配送方式（新字段名）", example = "快递",
            allowableValues = {"快递", "海运", "空运"})
    private String shippingMethod;

    @Schema(description = "配送方式（旧字段名兼容）", example = "快递")
    private String method;

    @Schema(description = "计费规则描述（新字段名）", example = "首重10元，续重5元/kg")
    @Size(max = 255)
    private String feeRule;

    @Schema(description = "计费规则描述（旧字段名兼容）", example = "首重10元，续重5元/kg")
    @Size(max = 255)
    private String ruleDesc;

    @Schema(description = "优先级", example = "1")
    private Integer priority;

    @Schema(description = "状态：ACTIVE/INACTIVE", example = "ACTIVE")
    @Pattern(regexp = "ACTIVE|INACTIVE|启用|停用", message = "状态必须是 ACTIVE/INACTIVE 或 启用/停用")
    private String status;
}