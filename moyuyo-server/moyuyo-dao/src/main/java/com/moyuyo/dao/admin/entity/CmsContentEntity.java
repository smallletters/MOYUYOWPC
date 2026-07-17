package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CMS 内容管理实体
 */
@Data
@TableName("mo_cms_content")
public class CmsContentEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标题 */
    private String title;

    /** 类型：BANNER / RECOMMEND / TOPIC / PUSH */
    private String type;

    /** 内容 */
    private String content;

    /** 图片链接 */
    private String imageUrl;

    /** 跳转链接 */
    private String linkUrl;

    /** 展示位置，如 "首页"/"分类页" */
    private String location;

    /** 状态：ACTIVE / PAUSED */
    private String status;

    /** 排序权重 */
    private Integer sortOrder;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 点击率 */
    private Double ctr;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
