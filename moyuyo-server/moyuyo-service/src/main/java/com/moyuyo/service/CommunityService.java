package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.community.CommunityPostVO;

import java.util.List;

public interface CommunityService {

    Page<CommunityPostVO> listPosts(String topic, int page, int size);

    /**
     * 按关键字搜索帖子（基于 content LIKE %keyword%）。
     * topic 可选，不传则搜全部主题。
     */
    Page<CommunityPostVO> searchPosts(String keyword, String topic, int page, int size);

    CommunityPostVO getPostDetail(Long postId, Long currentUserId);

    CommunityPostVO createPost(Long userId, String content, List<String> images, String topic);

    void likePost(Long userId, Long postId);

    void unlikePost(Long userId, Long postId);

    void addComment(Long userId, Long postId, Long parentId, String content);

    Page<CommunityPostVO> listMyPosts(Long userId, int page, int size);

    /**
     * 当前用户收藏的帖子（按收藏时间倒序，含完整 VO）。
     * 用于"我的"页 → 收藏 入口。
     */
    Page<CommunityPostVO> listCollectedPosts(Long userId, int page, int size);
}
