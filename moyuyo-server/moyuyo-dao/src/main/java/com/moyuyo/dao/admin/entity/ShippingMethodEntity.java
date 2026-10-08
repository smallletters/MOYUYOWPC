package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配送方式字典（mo_shipping_method）。
 * 区别于 mo_shipping_strategy.method：strategy.method 是自由文本，
 * 这里 code 是机器可识别的唯一标识（standard/express/same_day/...），
 * 由 APP 端 checkout.vue 选用，由 mo_shipping_rate 引用计费。
 */
@Data
@TableName("mo_shipping_method")
public class ShippingMethodEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 方式编码：standard / express / priority / same_day */
    private String code;

    /** 英文名（i18n fallback） */
    private String nameEn;

    /** 中文名 */
    private String nameZh;

    /** 预计送达最小天数 */
    private Integer etaMinDays;

    /** 预计送达最大天数 */
    private Integer etaMaxDays;

    /** 展示排序，升序 */
    private Integer sortOrder;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
