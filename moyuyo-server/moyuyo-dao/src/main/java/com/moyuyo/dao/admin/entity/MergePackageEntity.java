package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_merge_package")
public class MergePackageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 合包单号 */
    private String mergeNo;

    /** 订单数量 */
    private Integer orderCount;

    /** 包裹数量 */
    private Integer packageCount;

    /** 总重量(kg) */
    private BigDecimal totalWeight;

    /** 状态：PENDING/PROCESSING/COMPLETED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
