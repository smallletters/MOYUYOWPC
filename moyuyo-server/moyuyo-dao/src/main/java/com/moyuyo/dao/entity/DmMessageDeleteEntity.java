package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户私信消息删除记录（对应 mo_dm_message_delete 表）
 *
 * 仅记录「某个用户删除了某条消息」，删除只影响自己的会话视图；
 * 对方的消息记录与会话摘要保持不变。
 */
@Data
@TableName("mo_dm_message_delete")
public class DmMessageDeleteEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 执行删除的用户ID */
    private Long userId;

    /** 被删除的消息ID */
    private Long messageId;

    /** 所属会话ID */
    private Long conversationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
