package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核实体（对应 mo_content_review 表）
 */
@Data
@TableName("mo_content_review")
public class ContentReviewEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 内容类型 */
    private String contentType;

    /** 内容ID */
    private Long contentId;

    /** 用户ID */
    private Long userId;

    /** 内容摘要 */
    private String contentExcerpt;

    /** 图片（多个以逗号分隔） */
    private String images;

    /** 审核原因 */
    private String reason;

    /** 审核状态：PENDING/APPROVED/REJECTED */
    private String status;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核意见 */
    private String reviewComment;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 是否自动审核：0-人工，1-自动 */
    private Integer autoFlag;

    /** 自动审核评分 */
    private Integer autoScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
