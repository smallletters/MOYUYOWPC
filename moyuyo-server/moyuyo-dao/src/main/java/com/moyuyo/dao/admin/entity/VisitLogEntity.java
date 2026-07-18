package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_visit_log")
public class VisitLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String sessionId;

    private String pageUrl;

    private String pageName;

    private String referrer;

    private Integer stayDuration;

    private String ip;

    private String userAgent;

    private String channel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
