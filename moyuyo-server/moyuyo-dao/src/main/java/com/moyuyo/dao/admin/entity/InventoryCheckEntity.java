package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mo_inventory_check")
public class InventoryCheckEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String checkNo;
    private Long skuId;
    private String productName;
    private String skuSpec;
    private Integer bookQuantity;
    private Integer actualQuantity;
    private Integer difference;
    private String status;
    private String checker;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
}
