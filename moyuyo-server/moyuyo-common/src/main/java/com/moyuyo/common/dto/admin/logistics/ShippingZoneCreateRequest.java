package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建发货区域请求
 * <p>
 * countryCodes 接受任意逗号分隔的字符串（含空白、小写、重复），服务端在
 * {@code AdminShippingZoneService} 内部做去空格/转大写/去重的归一化。
 * 不在 DTO 层用正则约束，避免把"us, ca"这种合法写法拒之门外。
 */
@Data
@Schema(description = "创建发货区域请求")
public class ShippingZoneCreateRequest {

    @Schema(description = "区域名称", example = "北美", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "区域名称不能为空")
    @Size(max = 128, message = "区域名称长度不能超过 128")
    private String name;

    @Schema(description = "国家码列表（ISO 3166-1 alpha-2，逗号分隔）",
            example = "US,CA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "国家码不能为空")
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