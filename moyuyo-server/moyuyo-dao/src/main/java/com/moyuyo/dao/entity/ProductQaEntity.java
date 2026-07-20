package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品问答实体（对应 mo_product_qa 表）
 */
@Data
@TableName("mo_product_qa")
public class ProductQaEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 提问用户ID */
    private Long userId;

    /** 问题内容 */
    private String question;

    /** 回答内容 */
    private String answer;

    /** 回答人ID */
    private Long answererId;

    /** 状态：PENDING/ANSWERED/CLOSED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
