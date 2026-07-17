package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * GDPR 用户同意记录实体
 */
@Data
@TableName("mo_gdpr_consent")
public class GdprConsentEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 同意类型：MARKETING/ANALYTICS/THIRD_PARTY/COOKIES */
    private String consentType;

    /** 是否授权 */
    private Boolean granted;

    /** IP地址 */
    private String ip;

    /** User-Agent */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
