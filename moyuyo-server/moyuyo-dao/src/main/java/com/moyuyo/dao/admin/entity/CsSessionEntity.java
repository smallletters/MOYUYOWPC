package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话实体（对应 mo_cs_session 表）
 *
 * 字段映射说明：
 * - sessionId (驼峰) ↔ session_id (SQL 列)，MyBatis-Plus 自动转
 * - csStaffId (驼峰) ↔ operator_id (SQL 列) 是历史遗留不一致；
 *   当前页面没有在 UI 上暴露客服绑定功能，因此暂不修旧字段；
 *   新代码如需读写客服绑定，建议显式 @TableField(value="operator_id")
 */
@Data
@TableName("mo_cs_session")
public class CsSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话标识（业务展示用 sessionId 字符串） */
    private String sessionId;

    /** 用户ID（mo_user.id） */
    private Long userId;

    /**
     * 客服人员ID（历史遗留：实际 SQL 列名为 operator_id）
     * 这里加上显式 @TableField 绑定，规避自动驼峰转换把 csStaffId → cs_staff_id（不存在列）
     */
    @TableField(value = "operator_id")
    private Long csStaffId;

    /** 会话状态：WAITING/PROCESSING/CLOSED */
    private String status;

    /** 渠道 */
    private String channel;

    /** 消息数（V20260720_01 已存在 message_count 列；前端列表需要这个值） */
    private Integer messageCount;

    /** 最后一条消息时间（V20260820_02 新增 last_message_at 列） */
    private LocalDateTime lastMessageAt;

    /** 会话结束时间（V20260720_01 已存在 close_time 列，可空） */
    private LocalDateTime closeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
