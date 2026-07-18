package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_warehouse")
public class WarehouseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仓库名称 */
    private String name;

    /** 仓库类型：SELF/THIRD_PARTY/OVERSEAS */
    private String type;

    /** 国家 */
    private String country;

    /** 城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 面积(㎡) */
    private Integer area;

    /** 负责人 */
    private String manager;

    /** 联系电话 */
    private String phone;

    /** SKU数量 */
    private Integer skuCount;

    /** 总库存 */
    private Integer totalStock;

    /** 使用率(%) */
    private Integer usageRate;

    /** 状态：ACTIVE/INACTIVE/MAINTENANCE */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
