package com.moyuyo.common.dto.refund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款商品明细 VO
 * 用于管理后台退款列表展示商品图片、标题、SKU 与数量
 */
@Data
@AllArgsConstructor
@Schema(description = "退款商品明细")
public class RefundItemVO {

    private Long productId;

    private Long skuId;

    /** 商品标题（下单快照） */
    private String name;

    /** 商品主图（下单快照，/uploads/... 或 http(s) 地址） */
    private String image;

    /** SKU 编码（mo_product_sku.sku_code，缺失时前端回退展示规格） */
    private String skuCode;

    /** 规格文本（如 500ml / Large） */
    private String skuSpec;

    /** 数量：拆单退款为退款数量，整单退款为下单数量 */
    private Integer quantity;

    /** 金额：拆单退款为子项退款金额，整单退款为商品小计 */
    private BigDecimal amount;
}
