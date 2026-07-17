package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单实体
 */
@Data
@TableName("mo_ticket")
public class TicketEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工单编号 */
    private String ticketNo;

    /** 工单类型 */
    private String type;

    /** 优先级：HIGH / MEDIUM / LOW */
    private String priority;

    /** 工单标题 */
    private String title;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String userName;

    /** 客服名称 */
    private String agentName;

    /** 状态：PENDING / PROCESSING / CLOSED */
    private String status;

    /** 回复内容 */
    private String responseTime;

    /** 是否超时 */
    private Boolean timeout;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
