package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单对话消息实体（对应 mo_ticket_message 表）
 *
 * - sender_type = USER：用户原始诉求或追问
 * - sender_type = AGENT：客服回复
 * - sender_type = SYSTEM：系统提示（如分配、转单、关闭等）
 */
@Data
@TableName("mo_ticket_message")
public class TicketMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联工单 id（mo_ticket.id） */
    private Long ticketId;

    /** 发送方类型 */
    private String senderType;

    /** 发送方 id（用户 id 或管理员 id） */
    private Long senderId;

    /** 发送方展示名 */
    private String senderName;

    /** 消息内容 */
    private String content;

    /** 消息类型 */
    private String contentType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
