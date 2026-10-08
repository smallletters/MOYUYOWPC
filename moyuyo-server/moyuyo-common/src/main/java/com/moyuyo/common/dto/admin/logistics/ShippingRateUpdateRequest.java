package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * partial update：所有字段都可空；null 字段不更新。
 * <p>
 * 关键设计：{@code freeThresholdClear} 标志位用于"把已存在的非空 freeThreshold 清空成 NULL"。
 * 原因：partial update 的 BigDecimal 字段在 Jackson 序列化下无法区分"传 null=不更新"和"显式传 null=清空"。
 * 前端在弹窗里加一个"清除免邮门槛"按钮，提交时同时设 {@code freeThreshold=null} + {@code freeThresholdClear=true}，
 * service 看到标志位就把 entity 的 freeThreshold 设为 null（覆盖）。
 */
@Data
@Schema(description = "更新运费规则请求")
public class ShippingRateUpdateRequest {

    @Schema(description = "首费")
    @DecimalMin(value = "0", message = "首费不能为负")
    private BigDecimal firstCharge;

    @Schema(description = "首件/首重数量")
    @Min(value = 1)
    private Integer firstUnit;

    @Schema(description = "续费")
    @DecimalMin(value = "0")
    private BigDecimal continueCharge;

    @Schema(description = "续件/续重单位")
    @Min(value = 1)
    private Integer continueUnit;

    @Schema(description = "满额包邮门槛。配合 freeThresholdClear=true 时可清空已有值。")
    private BigDecimal freeThreshold;

    @Schema(description = "显式清空 freeThreshold 标志：true 表示把 freeThreshold 设为 NULL（不包邮），忽略 freeThreshold 字段值。默认 false。",
            example = "false")
    private Boolean freeThresholdClear;

    @Schema(description = "货币")
    @Size(max = 8)
    private String currency;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "状态")
    @Pattern(regexp = "ACTIVE|INACTIVE|启用|停用")
    private String status;

    @Schema(description = "备注")
    @Size(max = 255)
    private String remark;

    @Schema(description = "计费方式：1=按件 2=按重 3=按金额")
    @Min(value = 1)
    private Integer chargeType;
}
