package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("mo_knowledge_base")
public class KnowledgeBaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类：FAQ/GUIDE/POLICY/TRAINING */
    private String category;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 标签 */
    private String tags;

    /** 排序权重 */
    private Integer sortOrder;

    /** 查看次数 */
    private Integer viewCount;

    /** 状态：DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    /** 创建人ID */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
