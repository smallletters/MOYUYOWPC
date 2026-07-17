package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员权限实体
 */
@Data
@TableName("mo_admin_permission")
public class AdminPermissionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 资源名称，如 cms / rbac / orders / products / users / marketing / finance / inventory / push / ticket / settings / analytics */
    private String resource;

    /** 操作类型：view / create / edit / delete */
    private String action;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
