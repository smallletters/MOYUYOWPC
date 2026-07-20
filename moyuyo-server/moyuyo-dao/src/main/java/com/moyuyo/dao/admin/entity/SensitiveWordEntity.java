package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体
 */
@Data
@TableName("mo_sensitive_word")
public class SensitiveWordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 敏感词 */
    private String word;

    /** 替换词 */
    private String replacement;

    /** 匹配模式：EXACT 精确 / FUZZY 模糊 / REGEX 正则 */
    private String matchMode;

    /** 分类 */
    private String category;

    /** 命中次数 */
    private Integer hitCount;

    /** 最后命中时间 */
    private LocalDateTime lastHitTime;

    /** 状态：ENABLED / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
