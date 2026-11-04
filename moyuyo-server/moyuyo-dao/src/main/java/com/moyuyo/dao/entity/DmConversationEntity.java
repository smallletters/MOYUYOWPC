package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户私信会话实体（对应 mo_dm_conversation 表）
 *
 * 一对用户只会有一条会话：user_a 固定为较小的用户 ID，user_b 为较大的用户 ID，
 * 由数据库唯一索引 uk_dm_conv_pair 保证幂等。
 */
@Data
@TableName("mo_dm_conversation")
public class DmConversationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话类型：SINGLE 单聊 / GROUP 群聊 */
    private String type;

    /** 群名称（仅群聊） */
    private String name;

    /** 群主用户ID（仅群聊） */
    private Long ownerId;

    /** 群公告（仅群聊） */
    private String announcement;

    /** 群头像 URL（仅群聊） */
    private String avatar;

    /** 参与者A（较小用户ID；群聊为 NULL） */
    private Long userA;

    /** 参与者B（较大用户ID；群聊为 NULL） */
    private Long userB;

    /** 最后一条消息ID */
    private Long lastMessageId;

    /** 最后一条消息类型：TEXT / IMAGE */
    private String lastMessageType;

    /** 最后一条消息预览（截断，列表页直接展示） */
    private String lastMessageContent;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageAt;

    /** A 的未读数（B 发来的消息） */
    private Integer unreadA;

    /** B 的未读数（A 发来的消息） */
    private Integer unreadB;

    /** A 是否置顶该会话 */
    private Integer pinnedA;

    /** B 是否置顶该会话 */
    private Integer pinnedB;

    /** A 是否删除（隐藏）该会话：1 隐藏，收到新消息/主动打开时恢复 */
    private Integer deletedA;

    /** B 是否删除（隐藏）该会话：1 隐藏，收到新消息/主动打开时恢复 */
    private Integer deletedB;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
