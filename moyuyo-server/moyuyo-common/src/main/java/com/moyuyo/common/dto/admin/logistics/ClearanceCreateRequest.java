package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建清关记录请求。
 *
 * <p>声明单号 + 关联订单号 + 商品名称是必填基础信息；HS 编码、税率、状态可选。
 * 注意：本表 (mo_clearance) 同时承担"清关记录"和"海关库"两种语义，
 * 由 controller code 根据 URL 前缀 (/clearance vs /customs) 切换行为，
 * 不在本 DTO 上区分。
 */
@Data
@Schema(description = "创建清关记录请求")
public class ClearanceCreateRequest {

    @Schema(description = "报关单号", example = "DECL-2026-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 64, message = "报关单号长度不能超过 64")
    private String declarationNo;

    @Schema(description = "关联订单号", example = "MO20260101001", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 64)
    private String orderNo;

    @Schema(description = "商品名称", example = "宠物用品", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 255)
    private String productName;

    @Schema(description = "HS 编码", example = "420100")
    @Size(max = 32)
    private String hsCode;

    @Schema(description = "税率 (%)", example = "10.0")
    @DecimalMin(value = "0", message = "税率不能小于 0")
    @DecimalMax(value = "100", message = "税率不能大于 100")
    private BigDecimal taxRate;

    @Schema(description = "状态：PENDING/INSPECTING/CLEARED/REJECTED", example = "PENDING")
    @Size(max = 16)
    private String status;
}