package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新发货区域请求。所有字段均可选，仅传入的字段会被更新；name 改名将受 UNIQUE 索引保护。
 */
@Data
@Schema(description = "更新发货区域请求")
public class ShippingZoneUpdateRequest {

    @Schema(description = "区域名称", example = "北美")
    @Size(max = 128, message = "区域名称长度不能超过 128")
    private String name;

    @Schema(description = "国家码列表（ISO 3166-1 alpha-2，逗号分隔）", example = "US,CA,MX")
    @Size(max = 512, message = "国家码字符串长度不能超过 512")
    private String countryCodes;

    @Schema(description = "展示排序（升序）", example = "10")
    private Integer sortOrder;

    @Schema(description = "备注", example = "美国、加拿大")
    @Size(max = 255)
    private String remark;

    @Schema(description = "状态：ACTIVE / INACTIVE", example = "ACTIVE")
    @Pattern(regexp = "ACTIVE|INACTIVE|启用|停用", message = "状态必须是 ACTIVE/INACTIVE 或 启用/停用")
    private String status;
}