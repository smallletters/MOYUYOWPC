package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话实体（对应 mo_cs_session 表）
 */
@Data
@TableName("mo_cs_session")
public class CsSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话标识 */
    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 客服人员ID */
    private Long csStaffId;

    /** 会话状态：WAITING/PROCESSING/CLOSED */
    private String status;

    /** 渠道 */
    private String channel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
