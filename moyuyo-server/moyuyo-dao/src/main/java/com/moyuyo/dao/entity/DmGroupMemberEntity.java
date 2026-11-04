package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊会话成员实体（对应 mo_dm_group_member 表）
 *
 * 每个成员独立维护未读、置顶、删除状态，互不影响。
 */
@Data
@TableName("mo_dm_group_member")
public class DmGroupMemberEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 群会话ID */
    private Long conversationId;

    /** 成员用户ID */
    private Long userId;

    /** 该成员未读数 */
    private Integer unread;

    /** 是否置顶该群：0 否 / 1 是 */
    private Integer pinned;

    /** 是否删除（隐藏）该群：0 否 / 1 是 */
    private Integer deleted;

    /** 群内角色：OWNER 群主 / MEMBER 普通成员 */
    private String role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
