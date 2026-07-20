package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 众筹支持记录实体（对应 mo_crowdfunding_pledge 表）
 */
@Data
@TableName("mo_crowdfunding_pledge")
public class CrowdfundingPledgeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 众筹项目ID */
    private Long crowdfundingId;

    /** 支持用户ID */
    private Long userId;

    /** 支持金额 */
    private BigDecimal amount;

    /** 支持档位 */
    private String pledgeLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
