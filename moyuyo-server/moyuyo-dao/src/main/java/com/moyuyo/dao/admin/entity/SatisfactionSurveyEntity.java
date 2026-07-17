package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 满意度调查实体
 */
@Data
@TableName("mo_satisfaction_survey")
public class SatisfactionSurveyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工单ID */
    private Long ticketId;

    /** 订单ID */
    private Long orderId;

    /** 用户ID */
    private Long userId;

    /** 评分 */
    private Integer score;

    /** 分类：SERVICE/QUALITY/LOGISTICS */
    private String category;

    /** 评价内容 */
    private String comment;

    /** 多维度评分JSON */
    private String dimensionsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
