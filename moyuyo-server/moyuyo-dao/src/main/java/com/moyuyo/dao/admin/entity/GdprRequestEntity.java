package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * GDPR 用户请求实体
 */
@Data
@TableName("mo_gdpr_request")
public class GdprRequestEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 请求类型：ACCESS/DELETE/PORTABILITY/RECTIFY */
    private String requestType;

    /** 状态：PENDING/PROCESSING/COMPLETED/REJECTED */
    private String status;

    /** 详情JSON */
    private String detailJson;

    /** 处理人ID */
    private Long processedBy;

    /** 处理时间 */
    private LocalDateTime processedTime;

    /** 处理备注 */
    private String responseNote;

    /** 过期时间 */
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
