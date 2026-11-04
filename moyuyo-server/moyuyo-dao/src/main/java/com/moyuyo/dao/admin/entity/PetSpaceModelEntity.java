package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * APP 宠物 3D 空间模型实体
 * <p>
 * 同一 modelKey 可登记多个版本，只有 status=ENABLED 的版本会被 APP 使用。
 */
@Data
@TableName("mo_pet_space_model")
public class PetSpaceModelEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用途：SCENE 房间场景 / AVATAR_IDLE 静态角色 / AVATAR_WALK 行走角色 */
    private String modelKey;

    /** 模型名称 */
    private String displayName;

    /** 模型文件地址 */
    private String fileUrl;

    /** 文件格式：GLB/GLTF/FBX */
    private String fileFormat;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 版本标识 */
    private String version;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
