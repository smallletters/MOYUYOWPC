package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GDPR 未成年人年龄验证记录实体
 */
@Data
@TableName("mo_minor_verification")
public class MinorVerificationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 声明年龄 */
    private Integer age;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 验证状态：VERIFIED/PENDING/FAILED/EXEMPTED */
    private String verifyStatus;

    /** 监护人同意：GRANTED/PENDING/REVOKED/EXEMPTED */
    private String guardianConsent;

    /** 风险等级：LOW/MIDDLE/HIGH */
    private String riskLevel;

    /** 验证渠道：BIRTHDATE/IDCARD/FACE/THIRD_PARTY */
    private String verifyChannel;

    /** 验证来源（系统/人工） */
    private String verifiedBy;

    /** 最近一次验证时间 */
    private LocalDateTime verifiedTime;

    /** 下一次复检时间 */
    private LocalDateTime nextCheckTime;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
