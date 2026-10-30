package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片空间分组实体（对应 mo_upload_group）。
 */
@Data
@TableName("mo_upload_group")
public class UploadGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分组名称 */
    private String name;

    /** 排序值（越小越靠前） */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
