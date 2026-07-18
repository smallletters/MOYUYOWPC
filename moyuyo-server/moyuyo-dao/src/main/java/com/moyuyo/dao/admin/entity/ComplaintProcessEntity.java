package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mo_complaint_process")
public class ComplaintProcessEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long complaintId;
    private String action;
    private String operator;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
