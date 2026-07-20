package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 众筹项目实体（对应 mo_crowdfunding 表）
 */
@Data
@TableName("mo_crowdfunding")
public class CrowdfundingEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目名称 */
    private String projectName;

    /** 关联商品ID */
    private Long productId;

    /** 目标金额 */
    private BigDecimal targetAmount;

    /** 当前已筹金额 */
    private BigDecimal currentAmount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：PENDING/ACTIVE/FUNDED/FAILED/CANCELLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
