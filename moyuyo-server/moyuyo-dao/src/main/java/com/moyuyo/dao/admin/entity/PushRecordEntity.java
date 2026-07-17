package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推送记录实体
 */
@Data
@TableName("mo_push_record")
public class PushRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 推送标题 */
    private String title;

    /** 推送摘要 */
    private String summary;

    /** 类型：APP_PUSH / SMS / EMAIL */
    private String type;

    /** 目标链接 */
    private String targetUrl;

    /** 目标用户数 */
    private Integer targetUserCount;

    /** 发送数 */
    private Integer sentCount;

    /** 送达数 */
    private Integer deliveredCount;

    /** 点击数 */
    private Integer clickCount;

    /** 状态：PENDING / SENT / CANCELLED */
    private String status;

    /** 计划发送时间 */
    private LocalDateTime scheduledTime;

    /** 实际发送时间 */
    private LocalDateTime sentTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
