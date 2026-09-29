package com.moyuyo.common.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "地址验证响应")
public class AddressValidateResponse {

    @Schema(description = "是否可配送", example = "true")
    private boolean shippable;

    @Schema(description = "提示信息", example = "We currently ship to this region")
    private String message;

    /** 批量校验用：包装单条结果 */
    @Data
    @AllArgsConstructor
    public static class Item {
        @Schema(description = "地址 ID")
        private Long addressId;
        @Schema(description = "是否可配送")
        private boolean shippable;
        @Schema(description = "命中国家码（失败时返回）")
        private String country;
    }
}
