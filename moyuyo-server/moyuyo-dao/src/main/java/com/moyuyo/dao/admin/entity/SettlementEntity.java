package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算记录实体
 */
@Data
@TableName("mo_settlement")
public class SettlementEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 结算单号 */
    private String settlementNo;

    /** 支付渠道 */
    private String payChannel;

    /** 结算周期 */
    private String period;

    /** 结算金额 */
    private Double amount;

    /** 状态：PENDING / SETTLING / SETTLED / ABNORMAL */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 结算时间 */
    private LocalDateTime settleTime;
}
