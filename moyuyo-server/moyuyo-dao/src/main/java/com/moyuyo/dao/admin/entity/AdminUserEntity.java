package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户实体
 */
@Data
@TableName("mo_admin_user")
public class AdminUserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 姓名 */
    private String name;

    /** 邮箱 */
    private String email;

    /** 角色：SUPER_ADMIN 超级管理员 / OPERATOR 运营 */
    private String role;

    /** 状态：ACTIVE 启用 / DISABLED 禁用 */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
