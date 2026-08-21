package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话消息实体（对应 mo_cs_message 表）
 *
 * 一条消息代表客户与客服之间的一次对话片段：
 * - sender_type=USER 时 sender_id = C 端用户 id
 * - sender_type=AGENT 时 sender_id = 后台管理员 id
 * - sender_type=SYSTEM 时 sender_id 为空，表示系统提示（如"客服已接入""会话已关闭"）
 */
@Data
@TableName("mo_cs_message")
public class CsMessageEntity {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联会话 id（mo_cs_session.id） */
    private Long sessionId;

    /** 发送方类型：USER 用户 / AGENT 客服 / SYSTEM 系统 */
    private String senderType;

    /** 发送方 id：用户或客服管理员 id；系统消息时为空 */
    private Long senderId;

    /** 发送方展示名（冗余字段，避免渲染时再去联表查） */
    private String senderName;

    /** 消息内容 */
    private String content;

    /** 消息类型：TEXT 文本 / IMAGE 图片 / SYSTEM 系统提示 */
    private String contentType;

    /** 是否已被客服读取（用户发的消息才需要标记；客服发的无需标记） */
    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
