package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单标签实体（对应 mo_order_tag 表）
 */
@Data
@TableName("mo_order_tag")
public class OrderTagEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标签名称 */
    private String tagName;

    /** 标签颜色 */
    private String tagColor;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态：ENABLED/DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
