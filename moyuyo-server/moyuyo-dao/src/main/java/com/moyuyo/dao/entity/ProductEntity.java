package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mo_product")
public class ProductEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String spuCode;

    private Long wooProductId;

    private Long categoryId;

    private Long brandId;

    private Long brandIpId;

    private String name;

    private String mainImage;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String stockStatus;

    private Integer sales;

    private String attributes;

    private String detail;

    private String shortDetail;

    private java.math.BigDecimal weight;

    private String tags;

    private String productType;

    private Boolean manageStock;

    private Boolean onSale;

    private LocalDateTime wooModified;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<ProductSkuEntity> skus;

    @TableField(exist = false)
    private List<ProductImageEntity> images;

    /** 平均评分（仅 Controller 层动态注入，无对应数据库列） */
    @TableField(exist = false)
    private Double rating;

    /** 有效评论数（仅 Controller 层动态注入） */
    @TableField(exist = false)
    private Integer reviewCount;

    /** 是否允许评论（非表字段，来自 attributes.reviews_allowed，默认允许） */
    @TableField(exist = false)
    private Boolean reviewsAllowed;

    /** 是否允许缺货订购（非表字段，来自 attributes.backorders，默认不允许） */
    @TableField(exist = false)
    private Boolean allowBackorder;

    /** 是否虚拟商品（非表字段，来自 attributes.is_virtual，虚拟订单免运费/免地址） */
    @TableField(exist = false)
    private Boolean isVirtual;

    /** 税状态（非表字段，来自 attributes.tax_status，APP 结算页预估税费） */
    @TableField(exist = false)
    private String taxStatus;

    /** 税类（非表字段，来自 attributes.tax_class，APP 结算页预估税费） */
    @TableField(exist = false)
    private String taxClass;

    /** 会员价（仅 Controller 层按当前用户会员等级动态注入，无对应数据库列） */
    @TableField(exist = false)
    private BigDecimal memberPrice;

    /** 会员折扣系数（1.00 = 原价，0.95 = 9.5 折；前端按当前 SKU 价 × 系数自行计算展示价） */
    @TableField(exist = false)
    private BigDecimal memberPriceRate;

    /** 是否处于进行中的秒杀活动（秒杀商品不展示/不叠加会员价） */
    @TableField(exist = false)
    private Boolean flashSaleActive;

    /** 命中的营销活动 ID */
    @TableField(exist = false)
    private Long campaignId;

    /** 营销活动名称 */
    @TableField(exist = false)
    private String campaignName;

    /** 活动优惠类型：PERCENT / DIRECT / THRESHOLD */
    @TableField(exist = false)
    private String campaignType;

    /** 活动优惠值：PERCENT 为折扣系数（0.7），DIRECT 为减免金额，THRESHOLD 为减免金额 */
    @TableField(exist = false)
    private BigDecimal campaignValue;

    /** 满减门槛（仅 THRESHOLD 有效） */
    @TableField(exist = false)
    private BigDecimal campaignMinAmount;

    /** 活动价（按商品主价计算，SKU 价格不同时前端按 campaignType/Value 重算） */
    @TableField(exist = false)
    private BigDecimal campaignPrice;

    /** 活动优惠金额 */
    @TableField(exist = false)
    private BigDecimal campaignDiscount;

    /** 活动标签文案，如“限时 7 折”“立减 10 元”“满 100 减 20” */
    @TableField(exist = false)
    private String campaignTag;

    /** 预售配置（来自 mo_product_presale，后台编辑回显 + APP 详情页展示；无预售时为 null） */
    @TableField(exist = false)
    private ProductPresaleEntity presaleInfo;
}
