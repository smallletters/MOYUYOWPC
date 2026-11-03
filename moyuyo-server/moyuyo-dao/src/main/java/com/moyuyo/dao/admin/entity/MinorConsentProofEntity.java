package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * GDPR 家长同意凭证存档实体
 */
@Data
@TableName("mo_minor_consent_proof")
public class MinorConsentProofEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 未成年人用户ID */
    private Long userId;

    /** 监护人姓名 */
    private String guardianName;

    /** 监护人身份证号（脱敏存储） */
    private String guardianIdNo;

    /** 监护人联系电话 */
    private String guardianPhone;

    /** 关系：父母/法定监护人 */
    private String relationship;

    /** 凭证类型：ELECTRONIC/PAPER/THIRD_PARTY */
    private String proofType;

    /** 凭证附件地址 */
    private String proofUrl;

    /** 签署时间 */
    private LocalDateTime signedAt;

    /** 到期时间 */
    private LocalDateTime expireAt;

    /** 状态：ACTIVE/REVOKED/EXPIRED */
    private String status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
