package com.moyuyo.common.dto.admin.logistics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新承运商请求（partial update，所有字段均可空）。
 */
@Data
@Schema(description = "更新承运商请求")
public class CarrierUpdateRequest {

    @Schema(description = "承运商名称", example = "顺丰国际")
    @Size(max = 128)
    private String name;

    @Schema(description = "运输方式", example = "AIR",
            allowableValues = {"AIR", "LAND", "SEA", "MIX", "快递", "海运", "空运", "陆运"})
    @Pattern(regexp = "AIR|LAND|SEA|MIX|快递|海运|空运|陆运",
            message = "运输方式必须是 AIR/LAND/SEA/MIX 或 快递/海运/空运/陆运")
    private String transportMode;

    @Schema(description = "平均配送天数", example = "5.5")
    @DecimalMin(value = "0", message = "配送天数不能小于 0")
    private BigDecimal avgDeliveryDays;

    @Schema(description = "首重价格", example = "10.00")
    @DecimalMin(value = "0", message = "首重价格不能小于 0")
    private BigDecimal firstWeightPrice;

    @Schema(description = "续重价格", example = "5.00")
    @DecimalMin(value = "0", message = "续重价格不能小于 0")
    private BigDecimal renewWeightPrice;

    @Schema(description = "好评率 (%)", example = "98.5")
    @DecimalMin(value = "0", message = "好评率不能小于 0")
    @DecimalMax(value = "100", message = "好评率不能大于 100")
    private BigDecimal praiseRate;

    @Schema(description = "状态：ACTIVE / INACTIVE", example = "ACTIVE")
    @Pattern(regexp = "ACTIVE|INACTIVE|启用|停用", message = "状态必须是 ACTIVE/INACTIVE 或 启用/停用")
    private String status;

    @Schema(description = "承运商编码（用于订单关联，如 yanwen_us / cainiao_yw）", example = "yanwen_us")
    @Size(max = 64)
    private String code;

    @Schema(description = "API 账号/客户号（如燕文 userId）", example = "100000")
    @Size(max = 128)
    private String apiUserId;

    @Schema(description = "API 密钥/Token（apitoken / partnerKey / appSecret）", example = "D6140AA...")
    @Size(max = 256)
    private String apiToken;

    @Schema(description = "渠道/产品编码（如燕文 channelId、菜鸟 cpCode）", example = "1615")
    @Size(max = 64)
    private String channelId;

    @Schema(description = "API 基础地址（不同承运商接入环境不同）", example = "https://open.yw56.com.cn/api/order")
    @Size(max = 256)
    private String apiBaseUrl;

    @Schema(description = "是否启用电子面单 API：0=关闭 1=启用", example = "1")
    private Integer labelApiEnabled;

    @Schema(description = "API 备注/对接说明", example = "燕文-美国专线")
    @Size(max = 500)
    private String apiRemark;
}