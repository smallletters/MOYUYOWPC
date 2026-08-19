package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 换货单实体
 * 状态机：APPLIED → APPROVED → SHIPPED_BACK → RESHIPPED → COMPLETED
 * 任意阶段允许 CANCELLED
 */
@Data
@TableName("mo_exchange")
public class ExchangeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private String exchangeNo;

    private Long oldSkuId;

    private Integer oldQuantity;

    private Long newSkuId;

    private Integer newQuantity;

    private String reason;

    private String description;

    private String images;

    private String status;

    private String carrier;

    private String trackingNo;

    private String reshipCarrier;

    private String reshipTracking;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime applyTime;

    private LocalDateTime approveTime;

    private LocalDateTime completeTime;

    private LocalDateTime cancelTime;
}
