package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mo_gdpr_policy")
public class GdprPolicyEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String version;
    private String title;
    private String content;
    private String status;
    private LocalDate effectiveDate;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
