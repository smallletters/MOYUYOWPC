package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.community.CommunityPostVO;

import java.util.List;

public interface CommunityService {

    Page<CommunityPostVO> listPosts(String topic, int page, int size);

    /**
     * 按用户 ID 拉取该用户已发布的帖子（用于他人 profile 页）。仅展示 status=1。
     */
    Page<CommunityPostVO> listPostsByUser(Long userId, int page, int size);

    /**
     * 按关键字搜索帖子（基于 content LIKE %keyword%）。
     * topic 可选，不传则搜全部主题。
     */
    Page<CommunityPostVO> searchPosts(String keyword, String topic, int page, int size);

    CommunityPostVO getPostDetail(Long postId, Long currentUserId);

    CommunityPostVO createPost(Long userId, Long petId, String content, List<String> images, String video, String cover, String topic, java.time.LocalDateTime scheduledAt);

    /**
     * 定时任务:扫描到点的待发布帖子,切换为已发布。
     * @return 本次发布的帖子数
     */
    int publishScheduledPosts();

    void likePost(Long userId, Long postId);

    void unlikePost(Long userId, Long postId);

    void addComment(Long userId, Long postId, Long parentId, String content);

    /**
     * 删除帖子（仅作者本人）。
     * 内部会校验权限 + 仅允许删除已发布帖子 + 级联清理评论/点赞/收藏；
     * 任一异常通过 IllegalArgumentException / IllegalStateException 抛出，由 ControllerAdvice 转 4xx。
     */
    void deletePost(Long userId, Long postId);

    /**
     * 当前用户的帖子。petId 非空时按宠物过滤（用于宠物记忆树/我的宠物帖子）。
     */
    Page<CommunityPostVO> listMyPosts(Long userId, Long petId, int page, int size);

    /**
     * 当前用户收藏的帖子（按收藏时间倒序，含完整 VO）。
     * 用于"我的"页 → 收藏 入口。
     */
    Page<CommunityPostVO> listCollectedPosts(Long userId, int page, int size);

    /**
     * 当前用户点赞过的帖子（按点赞时间倒序，含完整 VO）。
     * 用于"我的"页 → 个人中心 → 点赞 Tab。
     * 返回 VO 的 liked 字段恒为 true（同一份 liked 表筛选而来），collected 字段反映当前是否已收藏。
     */
    Page<CommunityPostVO> listLikedPosts(Long userId, int page, int size);
}
