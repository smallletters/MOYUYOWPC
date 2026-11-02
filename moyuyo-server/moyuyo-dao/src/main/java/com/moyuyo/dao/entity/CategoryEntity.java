package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mo_category")
public class CategoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    private String name;

    /**
     * 多语言名称 JSON 字符串，例如 {"zh-CN":"洗护美容","en-US":"Bath & Grooming"}。
     * 未配置时为空，前端回落到 name / 静态字典。
     */
    private String nameI18n;

    private String icon;

    private Integer sort;

    private Integer level;

    private Long collectionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<CategoryEntity> children;
}
