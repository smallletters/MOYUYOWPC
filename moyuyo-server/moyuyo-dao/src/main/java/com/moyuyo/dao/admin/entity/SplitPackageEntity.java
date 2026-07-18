package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_split_package")
public class SplitPackageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 商品数量 */
    private Integer productCount;

    /** 分包裹数量 */
    private Integer splitCount;

    /** 总重量(kg) */
    private BigDecimal totalWeight;

    /** 状态：PENDING/PROCESSING/COMPLETED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
