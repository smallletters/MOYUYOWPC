package com.moyuyo.common.dto.logistics;

import java.util.List;
import lombok.Data;

/**
 * 燕文物流 — 创建运单请求参数
 * <p>
 * 对应燕文开放平台接口：express.order.create
 * 参考文档：https://opendocs.yw56.com.cn/webfile/7250693184987074560/
 * <p>
 * 燕文采用嵌套对象结构（receiverInfo/parcelInfo/productList/senderInfo），
 * 本类用静态嵌套类描述各子对象，便于在 YanWenLabelService 中按业务数据组装。
 * <p>
 * 关键设计取舍：
 * <ul>
 *   <li>MoDB 字段不修改：燕文需要的 weight/goodsNameEn/country/state/city/zipCode
 *       在业务侧从现有 receiverAddress 字符串兜底解析，避免动分区表结构。</li>
 *   <li>字段按官方文档保留所有可选项，方便后续扩展（IOSS/HSCode 等）。</li>
 * </ul>
 */
@Data
public class YanWenCreateRequest {

    /** 渠道/产品编码（必填，从 mo_carrier.channelId 取） */
    private String channelId;

    /** 订单来源（可选，如 portal / shopify / woocommerce） */
    private String orderSource;

    /** 订单号（必填，≤50字符） */
    private String orderNumber;

    /** 平台交易号（可选） */
    private String transactionNumber;

    /** 收款到账日期（yyyy-MM-dd，可选） */
    private String dateOfReceipt;

    /** 交货仓代码（美国必填：LAX01/NJC01/ORD01/...，其他国家可空） */
    private String companyCode;

    /** 拣货单信息（≤200字符，会在燕文打印标签"拣货单"区域显示 —— ⭐ 这是自定义字段打印的核心入口） */
    private String remark;

    /** 销售平台（可选，如 Shopify / WooCommerce） */
    private String salesPlatform;

    /** 收件人信息（必填） */
    private ReceiverInfo receiverInfo = new ReceiverInfo();

    /** 包裹信息（必填） */
    private ParcelInfo parcelInfo = new ParcelInfo();

    /** 发件人信息（可选，跨境场景下建议传国内发货人） */
    private SenderInfo senderInfo = new SenderInfo();

    /** ==================== 子结构 ==================== */

    @Data
    public static class ReceiverInfo {
        /** 收件人姓名（必填，≤50字符） */
        private String name;
        /** 收件人电话（≤50字符） */
        private String phone;
        /** 收件人邮箱（≤100字符） */
        private String email;
        /** 收件人公司（≤100字符） */
        private String company;
        /** 目的国id或二字码（必填，US/CN/FR/...） */
        private String country;
        /** 州/省（≤50字符） */
        private String state;
        /** 城市（≤50字符） */
        private String city;
        /** 邮编（≤50字符） */
        private String zipCode;
        /** 门牌号（≤50字符） */
        private String houseNumber;
        /** 详细地址（必填，≤200字符） */
        private String address;
        /** 收件人税号（≤50字符） */
        private String taxNumber;
        /** FINFL号码（≤50字符，欧盟专用） */
        private String finflNumber;
    }

    @Data
    public static class ParcelInfo {
        /** 是否带电（0=否 1=是） */
        private Integer hasBattery = 0;
        /** 申报币种（USD/EUR/GBP） */
        private String currency = "USD";
        /** 申报总数量 */
        private Integer totalQuantity;
        /** 总重量（单位：g） */
        private Integer totalWeight;
        /** 包裹高（单位：cm） */
        private Integer height;
        /** 包裹宽（单位：cm） */
        private Integer width;
        /** 包裹长（单位：cm） */
        private Integer length;
        /** IOSS号（欧盟≤22欧元免税专用） */
        private String ioss;
        /** 商品列表（数组形式） */
        private List<ProductItem> productList = new java.util.ArrayList<>();
    }

    @Data
    public static class ProductItem {
        /** 中文品名（必填，≤200字符） */
        private String goodsNameCh;
        /** 英文品名（必填，≤200字符；如数据库无英文品名，用中文品名兜底） */
        private String goodsNameEn;
        /** 申报单价（≤18位数字，2位小数） */
        private String price;
        /** 商品海关编码（≤50字符，可选） */
        private String hscode;
        /** 商品链接（≤2000字符，可选） */
        private String url;
        /** 商品材质（≤500字符，可选） */
        private String material;
        /** 单票数量 */
        private Integer quantity;
        /** 单件重量（单位：g） */
        private Integer weight;
        /** 商品SKU（≤100字符） */
        private String sku;
        /** IMEI编码（≤50字符，手机/电子设备专用） */
        private String imei;
    }

    @Data
    public static class SenderInfo {
        private String name;
        private String phone;
        private String email;
        private String company;
        private String country;
        private String state;
        private String city;
        private String zipCode;
        private String houseNumber;
        private String address;
        private String taxNumber;
    }
}
