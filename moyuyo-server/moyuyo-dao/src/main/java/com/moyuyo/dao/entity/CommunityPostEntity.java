package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_community_post")
public class CommunityPostEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    private String images;

    private String topic;

    private Integer likes;

    private Integer comments;

    /** 状态：1=已发布，0=隐藏（数据库列 tinyint） */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
