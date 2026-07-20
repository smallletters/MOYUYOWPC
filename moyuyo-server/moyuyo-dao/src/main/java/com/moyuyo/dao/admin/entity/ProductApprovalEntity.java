package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品审核实体（对应 mo_product_approval 表）
 */
@Data
@TableName("mo_product_approval")
public class ProductApprovalEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 提交人ID */
    private Long submitterId;

    /** 审核类型 */
    private String type;

    /** 审核状态：PENDING/APPROVED/REJECTED */
    private String status;

    /** 审核原因/备注 */
    private String reason;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 紧急标志：0-普通，1-紧急 */
    private Integer urgentFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
