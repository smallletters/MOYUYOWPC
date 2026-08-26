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
    private String topic;
    private Integer likes;
    private Integer comments;
    private Boolean liked;
    private Integer status;
    private LocalDateTime createTime;
    private List<CommentVO> commentList;

    @Data
    public static class CommentVO {
        private Long id;
        private Long userId;
        private String username;
        private String content;
        private Long parentId;
        private LocalDateTime createTime;
    }
}
