package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_shipping_zone")
public class ShippingZoneEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 区域名称，如 北美、欧盟、东南亚 */
    private String name;

    /** 国家码列表，逗号分隔，ISO 3166-1 alpha-2 大写，如 US,CA */
    private String countryCodes;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 展示排序，升序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}