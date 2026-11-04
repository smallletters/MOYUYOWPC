package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户私信消息实体（对应 mo_dm_message 表）
 *
 * read_flag 只对接收方有意义：接收方打开会话时，将其收到的未读消息批量置为已读。
 */
@Data
@TableName("mo_dm_message")
public class DmMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联会话ID */
    private Long conversationId;

    /** 发送方用户ID */
    private Long senderId;

    /** 接收方用户ID */
    private Long receiverId;

    /** 文本内容（上限 2000 字，由服务层校验） */
    private String content;

    /** 消息类型：TEXT 文本 / IMAGE 图片（图片时 content 为图片 URL） */
    private String contentType;

    /** 引用回复的目标消息ID（为空表示非引用消息） */
    private Long quoteMessageId;

    /** 引用消息摘要（冗余存储，列表无需回表） */
    private String quoteContent;

    /** 语音时长（秒），仅 VOICE 消息使用 */
    private Integer mediaDuration;

    /** 文件名，仅 FILE 消息使用 */
    private String mediaName;

    /** 文件大小（字节），仅 FILE 消息使用 */
    private Long mediaSize;

    /** 接收方是否已读：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 是否已撤回：0 否 / 1 是（撤回后 content 会被清空） */
    private Integer recalled;

    /** 撤回时间 */
    private LocalDateTime recallTime;

    /** 群聊消息发送者昵称（仅群聊列表补充展示，不落库） */
    @TableField(exist = false)
    private String senderNickname;

    /** 群聊消息发送者头像（仅群聊列表补充展示，不落库） */
    @TableField(exist = false)
    private String senderAvatar;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
