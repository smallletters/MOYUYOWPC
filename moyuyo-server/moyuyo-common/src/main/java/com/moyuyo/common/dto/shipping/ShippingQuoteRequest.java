package com.moyuyo.common.dto.shipping;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * APP 端运费试算请求。
 * 替代旧 estimate 接口；服务端按 zone × method 实时计算运费。
 */
@Data
@Schema(description = "运费试算请求")
public class ShippingQuoteRequest {

    @Schema(description = "收货国家 ISO 3166-1 alpha-2 大写", example = "US")
    @NotBlank(message = "country 不能为空")
    private String country;

    @Schema(description = "结算商品列表")
    @NotEmpty(message = "items 不能为空")
    private List<Item> items;

    @Schema(description = "满减前商品总金额（USD），用于免邮门槛计算；不传则服务端按 items 累加", example = "35.99")
    private BigDecimal subtotal;

    @Schema(description = "货币", example = "USD")
    private String currency;

    @Data
    public static class Item {
        @Schema(description = "商品 id", example = "100001")
        private Long productId;

        @Schema(description = "SKU id，可选", example = "200001")
        private Long skuId;

        @Schema(description = "购买数量", example = "1")
        private Integer quantity;

        @Schema(description = "单品重量（克），可选；若空则按商品 weight 字段；都没有则按 500g 默认", example = "1200")
        private Integer weightGrams;
    }
}
