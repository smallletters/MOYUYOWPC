package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 黑名单实体（对应 mo_blacklist 表）
 */
@Data
@TableName("mo_blacklist")
public class BlacklistEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类型：USER/IP/DEVICE/ADDRESS */
    private String type;

    /** 值 */
    private String value;

    /** 拉黑原因 */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 状态：ENABLED/DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
