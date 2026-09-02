package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_community_post")
public class CommunityPostEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String content;

    private String images;

    /** 视频 URL（与 images 互斥：视频帖 images 为空，图片帖 video 为 null） */
    private String video;

    /** 视频封面图 URL（仅视频帖有值，用户从 3 个候选帧中选出后由前端上传） */
    private String cover;

    /** 定时发布时间：null = 立即发布；非空时由定时任务在到达时间点时改为 status=1 */
    private LocalDateTime scheduledAt;

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
