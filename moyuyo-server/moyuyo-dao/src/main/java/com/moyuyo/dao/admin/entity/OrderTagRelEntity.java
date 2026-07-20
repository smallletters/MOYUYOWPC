package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单标签关联实体（对应 mo_order_tag_rel 表）
 */
@Data
@TableName("mo_order_tag_rel")
public class OrderTagRelEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标签ID */
    private Long tagId;

    /** 订单ID */
    private Long orderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
