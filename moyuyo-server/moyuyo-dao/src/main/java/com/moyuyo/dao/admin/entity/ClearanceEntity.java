package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_clearance")
public class ClearanceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 报关单号 */
    private String declarationNo;

    /** 关联订单号 */
    private String orderNo;

    /** 商品名称 */
    private String productName;

    /** HS编码 */
    private String hsCode;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 状态：PENDING/INSPECTING/CLEARED/REJECTED */
    private String status;

    /** 申报时间 */
    private LocalDateTime declareTime;

    /** 清关完成时间 */
    private LocalDateTime clearanceTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
