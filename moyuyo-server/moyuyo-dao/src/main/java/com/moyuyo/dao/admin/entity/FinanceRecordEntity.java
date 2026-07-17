package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 财务记录实体
 */
@Data
@TableName("mo_finance_record")
public class FinanceRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 类型：PAYMENT / REFUND / SETTLEMENT */
    private String type;

    /** 支付渠道 */
    private String channel;

    /** 金额 */
    private Double amount;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
