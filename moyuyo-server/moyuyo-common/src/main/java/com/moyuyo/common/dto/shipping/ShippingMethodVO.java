package com.moyuyo.common.dto.shipping;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * APP 端"配送方式 + 实时运费"响应。
 * 字段对齐 mo_shipping_method（code/name/eta）+ mo_shipping_rate（freight/free）。
 * <p>
 * 注意：freight 是服务端权威值，下单时由 OrderService 重算覆盖前端传的 freight。
 */
@Data
@Schema(description = "配送方式+实时运费")
public class ShippingMethodVO {

    @Schema(description = "配送方式编码：standard/express/priority/same_day", example = "standard")
    private String code;

    @Schema(description = "英文名（i18n fallback）", example = "Standard Shipping")
    private String nameEn;

    @Schema(description = "中文名", example = "标准配送")
    private String nameZh;

    @Schema(description = "预计送达最小天数", example = "5")
    private Integer etaMinDays;

    @Schema(description = "预计送达最大天数", example = "8")
    private Integer etaMaxDays;

    @Schema(description = "展示排序，升序（值小排前）", example = "10")
    private Integer sortOrder;

    @Schema(description = "实时运费（已应用免邮门槛）", example = "5.99")
    private BigDecimal freight;

    @Schema(description = "货币", example = "USD")
    private String currency;

    @Schema(description = "是否免邮（已应用满额免邮门槛）", example = "false")
    private Boolean free;

    @Schema(description = "免邮门槛 - 商品金额。>0 表示未达免邮门槛（还差多少），=0 表示已满足或无门槛", example = "23.01")
    private BigDecimal freeShortBy;

    @Schema(description = "免邮门槛（满额免邮的金额阈值，NULL=无门槛）", example = "59.00")
    private BigDecimal freeThreshold;
}
