package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.common.utils.XssSanitizer;
import com.moyuyo.dao.entity.*;
import com.moyuyo.dao.mapper.*;
import com.moyuyo.service.CommunityService;
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
    private final UserMapper userMapper;

    @Override
    public Page<CommunityPostVO> listPosts(String topic, int page, int size) {
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getStatus, "PUBLISHED")
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
        CommunityPostEntity entity = new CommunityPostEntity();
        entity.setUserId(userId);
        // 净化富文本，防止存储型 XSS
        entity.setContent(XssSanitizer.sanitizeRichText(content));
        entity.setImages(JsonUtils.toJsonArray(images));
        entity.setTopic(XssSanitizer.sanitizePlainText(topic));
        entity.setLikes(0);
        entity.setComments(0);
        entity.setStatus("PUBLISHED");
        postMapper.insert(entity);
        log.info("Post created: postId={}, userId={}", entity.getId(), userId);
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
        CommunityCommentEntity comment = new CommunityCommentEntity();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        // 净化评论内容，防止存储型 XSS
        comment.setContent(XssSanitizer.sanitizeRichText(content));
        commentMapper.insert(comment);

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

    private Page<CommunityPostVO> toVOPage(Page<CommunityPostEntity> entityPage) {
        // 避免 NPE
        if (entityPage.getRecords() == null || entityPage.getRecords().isEmpty()) {
            Page<CommunityPostVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
            voPage.setTotal(entityPage.getTotal());
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        // 批量查询用户信息
        List<Long> userIds = entityPage.getRecords().stream()
            .map(CommunityPostEntity::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserEntity> userMap = userIds.isEmpty() ? Collections.emptyMap() :
            userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        // 分页转换 + 富化用户信息
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

        List<Long> userIds = comments.stream().map(CommunityCommentEntity::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getNickname));

        return comments.stream().map(c -> {
            CommunityPostVO.CommentVO cv = new CommunityPostVO.CommentVO();
            cv.setId(c.getId());
            cv.setUserId(c.getUserId());
            cv.setUsername(nameMap.getOrDefault(c.getUserId(), "User"));
            cv.setContent(c.getContent());
            cv.setParentId(c.getParentId());
            cv.setCreateTime(c.getCreateTime());
            return cv;
        }).collect(Collectors.toList());
    }

}
