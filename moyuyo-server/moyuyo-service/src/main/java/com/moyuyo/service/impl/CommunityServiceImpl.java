package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.common.utils.XssSanitizer;
import com.moyuyo.dao.admin.entity.ContentReviewEntity;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.dao.admin.mapper.ContentReviewMapper;
import com.moyuyo.dao.admin.mapper.SensitiveWordMapper;
import com.moyuyo.dao.entity.*;
import com.moyuyo.dao.mapper.*;
import com.moyuyo.service.CommunityService;
import com.moyuyo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityLikeMapper likeMapper;
    private final CommunityCollectMapper collectMapper;
    private final UserMapper userMapper;
    private final ContentReviewMapper contentReviewMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    // 任务中心埋点：发布帖子触发"发布 1 条社区笔记 / 累计发布 10 条笔记"
    private final MissionService missionService;

    @Override
    public Page<CommunityPostVO> listPosts(String topic, int page, int size) {
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getStatus, 1)
                .orderByDesc(CommunityPostEntity::getCreateTime);
        if (topic != null && !topic.isEmpty()) {
            wrapper.eq(CommunityPostEntity::getTopic, topic);
        }

        Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return toVOPage(entityPage);
    }

    @Override
    public Page<CommunityPostVO> searchPosts(String keyword, String topic, int page, int size) {
        // 用 LIKE 模糊匹配 content。已发布(status=1) 才参与搜索。
        // 注意：避免通配符注入，对 MySQL LIKE 元字符 % _ \ 做转义。
        String safe = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getStatus, 1)
                .like(CommunityPostEntity::getContent, "%" + safe + "%")
                .orderByDesc(CommunityPostEntity::getCreateTime);
        if (topic != null && !topic.isEmpty()) {
            wrapper.eq(CommunityPostEntity::getTopic, topic);
        }
        Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return toVOPage(entityPage);
    }

    @Override
    public CommunityPostVO getPostDetail(Long postId, Long currentUserId) {
        CommunityPostEntity entity = postMapper.selectById(postId);
        if (entity == null) throw new IllegalArgumentException("帖子不存在");
        CommunityPostVO vo = toVO(entity);
        vo.setLiked(isLiked(currentUserId, postId));
        vo.setCommentList(getComments(postId));
        return vo;
    }

    @Override
    @Transactional
    public CommunityPostVO createPost(Long userId, String content, List<String> images, String topic) {
        String cleanContent = XssSanitizer.sanitizeRichText(content);
        rejectSensitiveContent(cleanContent);

        CommunityPostEntity entity = new CommunityPostEntity();
        entity.setUserId(userId);
        entity.setContent(cleanContent);
        entity.setImages(JsonUtils.toJsonArray(images));
        entity.setTopic(XssSanitizer.sanitizePlainText(topic));
        entity.setLikes(0);
        entity.setComments(0);
        // 表 mo_community_post.status 为 tinyint(1)，1=已发布 0=隐藏。历史已存值 1（PUBLISHED 简写）。
        entity.setStatus(1);
        postMapper.insert(entity);
        createReview("POST", entity.getId(), userId, cleanContent, entity.getImages());
        log.info("Post created: postId={}, userId={}", entity.getId(), userId);

        // 任务中心埋点：发布成功后触发"发布 1 条社区笔记"和累计成就进度
        try {
            missionService.incrementByKeyword(userId, "WEEKLY", "发布 1 条社区笔记", 1);
            missionService.incrementByKeyword(userId, "ACHIEVEMENT", "发布 10 条笔记", 1);
        } catch (Exception e) {
            log.warn("[community] trigger mission failed: postId={}, reason={}", entity.getId(), e.getMessage());
        }

        return toVO(entity);
    }

    @Override
    @Transactional
    public void likePost(Long userId, Long postId) {
        long count = likeMapper.selectCount(
                new LambdaQueryWrapper<CommunityLikeEntity>()
                        .eq(CommunityLikeEntity::getUserId, userId)
                        .eq(CommunityLikeEntity::getPostId, postId));
        if (count > 0) return;

        CommunityLikeEntity like = new CommunityLikeEntity();
        like.setUserId(userId);
        like.setPostId(postId);
        likeMapper.insert(like);

        CommunityPostEntity post = postMapper.selectById(postId);
        if (post != null) {
            post.setLikes(post.getLikes() == null ? 1 : post.getLikes() + 1);
            postMapper.updateById(post);
        }
    }

    @Override
    @Transactional
    public void unlikePost(Long userId, Long postId) {
        likeMapper.delete(
                new LambdaQueryWrapper<CommunityLikeEntity>()
                        .eq(CommunityLikeEntity::getUserId, userId)
                        .eq(CommunityLikeEntity::getPostId, postId));

        CommunityPostEntity post = postMapper.selectById(postId);
        if (post != null && post.getLikes() != null && post.getLikes() > 0) {
            post.setLikes(post.getLikes() - 1);
            postMapper.updateById(post);
        }
    }

    @Override
    @Transactional
    public void addComment(Long userId, Long postId, Long parentId, String content) {
        String cleanContent = XssSanitizer.sanitizeRichText(content);
        rejectSensitiveContent(cleanContent);

        CommunityCommentEntity comment = new CommunityCommentEntity();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(cleanContent);
        commentMapper.insert(comment);
        createReview("COMMENT", comment.getId(), userId, cleanContent, null);

        CommunityPostEntity post = postMapper.selectById(postId);
        if (post != null) {
            post.setComments(post.getComments() == null ? 1 : post.getComments() + 1);
            postMapper.updateById(post);
        }
    }

    @Override
    public Page<CommunityPostVO> listMyPosts(Long userId, int page, int size) {
        Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CommunityPostEntity>()
                        .eq(CommunityPostEntity::getUserId, userId)
                        .orderByDesc(CommunityPostEntity::getCreateTime));
        return toVOPage(entityPage);
    }

    @Override
    public Page<CommunityPostVO> listCollectedPosts(Long userId, int page, int size) {
        // 先分页查收藏关联,按收藏时间倒序
        Page<CommunityCollectEntity> collectPage = collectMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CommunityCollectEntity>()
                        .eq(CommunityCollectEntity::getUserId, userId)
                        .orderByDesc(CommunityCollectEntity::getCreateTime));
        if (collectPage.getRecords() == null || collectPage.getRecords().isEmpty()) {
            Page<CommunityPostVO> voPage = new Page<>(page, size, 0);
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        // 按收藏顺序组装 VO（保持收藏顺序，而不是按发布时间）
        List<Long> postIds = collectPage.getRecords().stream()
                .map(CommunityCollectEntity::getPostId).collect(Collectors.toList());
        List<CommunityPostEntity> posts = postMapper.selectBatchIds(postIds);
        Map<Long, CommunityPostEntity> postMap = posts.stream()
                .collect(Collectors.toMap(CommunityPostEntity::getId, p -> p));
        List<CommunityPostEntity> ordered = postIds.stream()
                .map(postMap::get).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        Page<CommunityPostEntity> entityPage = new Page<>(page, size, collectPage.getTotal());
        entityPage.setRecords(ordered);
        Page<CommunityPostVO> voPage = toVOPage(entityPage);
        // 当前用户视角：liked 字段标注
        if (voPage.getRecords() != null) {
            voPage.getRecords().forEach(v -> v.setLiked(isLiked(userId, v.getId())));
        }
        return voPage;
    }

    private void rejectSensitiveContent(String content) {
        List<SensitiveWordEntity> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWordEntity>()
                        .eq(SensitiveWordEntity::getStatus, "ENABLED"));
        if (new SensitiveWordFilter(words).contains(content)) {
            throw new IllegalArgumentException("内容包含敏感词，无法发布");
        }
    }

    private void createReview(String contentType, Long contentId, Long userId, String content, String images) {
        ContentReviewEntity review = new ContentReviewEntity();
        review.setContentType(contentType);
        review.setContentId(contentId);
        review.setUserId(userId);
        review.setContentExcerpt(content == null ? null : content.substring(0, Math.min(content.length(), 500)));
        review.setImages(images);
        review.setStatus("PENDING");
        review.setAutoFlag(0);
        contentReviewMapper.insert(review);
    }

    private Page<CommunityPostVO> toVOPage(Page<CommunityPostEntity> entityPage) {
        if (entityPage.getRecords() == null || entityPage.getRecords().isEmpty()) {
            Page<CommunityPostVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
            voPage.setTotal(entityPage.getTotal());
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        List<Long> userIds = entityPage.getRecords().stream()
            .map(CommunityPostEntity::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserEntity> userMap = userIds.isEmpty() ? Collections.emptyMap() :
            userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        return (Page<CommunityPostVO>) PageUtils.convertPage(entityPage, entity -> {
            CommunityPostVO vo = toVO(entity);
            UserEntity user = userMap.get(entity.getUserId());
            if (user != null) {
                vo.setUsername(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        });
    }

    private CommunityPostVO toVO(CommunityPostEntity entity) {
        CommunityPostVO vo = new CommunityPostVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setContent(entity.getContent());
        vo.setImages(JsonUtils.parseStringArray(entity.getImages()));
        vo.setTopic(entity.getTopic());
        vo.setLikes(entity.getLikes());
        vo.setComments(entity.getComments());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private boolean isLiked(Long userId, Long postId) {
        if (userId == null) return false;
        return likeMapper.selectCount(
                new LambdaQueryWrapper<CommunityLikeEntity>()
                        .eq(CommunityLikeEntity::getUserId, userId)
                        .eq(CommunityLikeEntity::getPostId, postId)) > 0;
    }

    private List<CommunityPostVO.CommentVO> getComments(Long postId) {
        List<CommunityCommentEntity> comments = commentMapper.selectList(
                new LambdaQueryWrapper<CommunityCommentEntity>()
                        .eq(CommunityCommentEntity::getPostId, postId)
                        .orderByAsc(CommunityCommentEntity::getCreateTime));
        if (comments.isEmpty()) return Collections.emptyList();
        return comments.stream().map(comment -> {
            CommunityPostVO.CommentVO vo = new CommunityPostVO.CommentVO();
            vo.setId(comment.getId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setParentId(comment.getParentId());
            vo.setCreateTime(comment.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
