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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
