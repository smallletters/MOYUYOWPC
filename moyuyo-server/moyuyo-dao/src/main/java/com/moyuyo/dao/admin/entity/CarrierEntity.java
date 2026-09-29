package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_carrier")
public class CarrierEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 承运商名称 */
    private String name;

    /** 承运商编码：yanwen / cainiao / kuaidi100 / sf / jd / ... */
    private String code;

    /** 运输方式：AIR/LAND/SEA/MIX */
    private String transportMode;

    /** 平均配送天数 */
    private BigDecimal avgDeliveryDays;

    /** 首重价格 */
    private BigDecimal firstWeightPrice;

    /** 续重价格 */
    private BigDecimal renewWeightPrice;

    /** 好评率(%) */
    private BigDecimal praiseRate;

    /** 状态 */
    private String status;

    /** API 账号/客户号（如燕文 userId） */
    private String apiUserId;

    /** API 密钥/Token（apitoken / partnerKey / appSecret） */
    private String apiToken;

    /** 渠道/产品编码（如燕文 channelId、菜鸟 cpCode） */
    private String channelId;

    /** API 基础地址（不同承运商接入环境不同） */
    private String apiBaseUrl;

    /** 是否启用电子面单 API：0=关闭 1=启用 */
    private Integer labelApiEnabled;

    /** API 备注/对接说明 */
    private String apiRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}