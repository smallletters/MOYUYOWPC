package com.moyuyo.common.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "社区帖子VO")
public class CommunityPostVO {

    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private String content;
    private List<String> images;
    /** 视频 URL（与 images 互斥） */
    private String video;
    /** 视频封面 URL */
    private String cover;
    private String topic;
    private Integer likes;
    private Integer comments;
    private Boolean liked;
    /** 当前登录用户是否已收藏(匿名访问固定 false) */
    private Boolean collected;
    private Integer status;
    private LocalDateTime createTime;
    /** 定时发布时间：null 表示非定时发布；非空表示等待在指定时间点发布 */
    private LocalDateTime scheduledAt;
    private List<CommentVO> commentList;

    @Data
    public static class CommentVO {
        private Long id;
        private Long userId;
        private String username;
        private String avatar;
        private String content;
        private Long parentId;
        private LocalDateTime createTime;
    }
}
