package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 关税配置实体（对应 mo_tariff_config 表）
 */
@Data
@TableName("mo_tariff_config")
public class TariffConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 国家代码 */
    private String countryCode;

    /** 商品类别 */
    private String productCategory;

    /** 税率 */
    private BigDecimal rate;

    /** 币种 */
    private String currency;

    /** 最低阈值 */
    private BigDecimal minThreshold;

    /** 最高阈值 */
    private BigDecimal maxThreshold;

    /** 生效时间 */
    private LocalDateTime effectiveDate;

    /** 失效时间 */
    private LocalDateTime expireDate;

    /** 状态：ENABLED/DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
