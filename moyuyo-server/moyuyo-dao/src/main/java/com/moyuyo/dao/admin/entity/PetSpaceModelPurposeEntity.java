package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * APP 宠物 3D 空间模型用途实体
 * <p>
 * 每个用途声明允许的模型文件格式，后台可新增自定义用途。
 */
@Data
@TableName("mo_pet_space_model_purpose")
public class PetSpaceModelPurposeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用途标识：大写字母/数字/下划线，创建后不可修改 */
    private String purposeKey;

    /** 用途名称 */
    private String purposeName;

    /** 允许的文件格式，逗号分隔：GLB,GLTF,FBX */
    private String allowedFormats;

    /** 用途说明 */
    private String description;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
