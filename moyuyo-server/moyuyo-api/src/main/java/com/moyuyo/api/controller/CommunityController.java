package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.community.CommunityCommentCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.CommunityCollectEntity;
import com.moyuyo.dao.entity.CommunityTopicV2Entity;
import com.moyuyo.dao.mapper.CommunityCollectMapper;
import com.moyuyo.dao.mapper.CommunityTopicV2Mapper;
import com.moyuyo.service.CommunityService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "社区管理")
@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityCollectMapper collectMapper;
    private final CommunityTopicV2Mapper topicMapper;

    @Operation(summary = "帖子列表（公开）")
    @GetMapping("/posts")
    public Result<Page<CommunityPostVO>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String keyword) {
        // 关键字非空时走搜索；topic 单独过滤；都不传则拉全部
        if (keyword != null && !keyword.trim().isEmpty()) {
            return Result.success(communityService.searchPosts(keyword.trim(), topic, page, size));
        }
        return Result.success(communityService.listPosts(topic, page, size));
    }

    @Operation(summary = "搜索帖子（与 listPosts?keyword= 等价，便于前端直链）")
    @GetMapping("/search")
    public Result<Page<CommunityPostVO>> searchPosts(
            @RequestParam String q,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (q == null || q.trim().isEmpty()) {
            return Result.success(communityService.listPosts(topic, page, size));
        }
        return Result.success(communityService.searchPosts(q.trim(), topic, page, size));
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/posts/{id}")
    public Result<CommunityPostVO> getPostDetail(@PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(communityService.getPostDetail(id, userId));
    }

    @Operation(summary = "发布帖子")
    @PostMapping("/posts")
    @RateLimiter(name = "postCreate", fallbackMethod = "postRateLimitFallback")
    public Result<CommunityPostVO> createPost(@Valid @RequestBody CommunityPostCreateRequest request) {
        return Result.success(communityService.createPost(
                UserContextHolder.getUserId(), request.getContent(), request.getImages(), request.getTopic()));
    }

    @Operation(summary = "我的帖子")
    @GetMapping("/posts/mine")
    public Result<Page<CommunityPostVO>> myPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.listMyPosts(UserContextHolder.getUserId(), page, size));
    }

    @Operation(summary = "点赞")
    @PostMapping("/posts/{id}/like")
    public Result<Void> likePost(@PathVariable Long id) {
        communityService.likePost(UserContextHolder.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/posts/{id}/like")
    public Result<Void> unlikePost(@PathVariable Long id) {
        communityService.unlikePost(UserContextHolder.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "评论")
    @PostMapping("/posts/{postId}/comments")
    @RateLimiter(name = "commentCreate", fallbackMethod = "commentRateLimitFallback")
    public Result<Void> addComment(@PathVariable Long postId,
                                   @Valid @RequestBody CommunityCommentCreateRequest request) {
        communityService.addComment(UserContextHolder.getUserId(), postId,
                request.getParentId(), request.getContent());
        return Result.success();
    }

    /**
     * 评论限流 fallback：签名必须与 addComment 一致（参数+Throwable）。
     * 注意：fallback 方法无法访问 @PathVariable 注解（resilience4j 反射调用），
     * 因此参数名需与原方法一致；Spring 会先注入路径变量，再注入 body，最后 Throwable。
     */
    public Result<Void> commentRateLimitFallback(Long postId,
                                                 CommunityCommentCreateRequest request,
                                                 RequestNotPermitted e) {
        return Result.error(429, "评论过于频繁，请稍后再试");
    }

    /**
     * 发帖限流 fallback：与 createPost 签名一致。
     */
    public Result<CommunityPostVO> postRateLimitFallback(CommunityPostCreateRequest request,
                                                         RequestNotPermitted e) {
        return Result.error(429, "发帖过于频繁，请稍后再试");
    }

    // === 收藏 ===
    @Operation(summary = "收藏帖子")
    @PostMapping("/posts/{id}/collect")
    public Result<Void> collect(@PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        CommunityCollectEntity exist = collectMapper.selectOne(
                new LambdaQueryWrapper<CommunityCollectEntity>()
                        .eq(CommunityCollectEntity::getUserId, userId)
                        .eq(CommunityCollectEntity::getPostId, id));
        if (exist != null) return Result.success();
        CommunityCollectEntity c = new CommunityCollectEntity();
        c.setUserId(userId);
        c.setPostId(id);
        collectMapper.insert(c);
        return Result.success();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/posts/{id}/collect")
    public Result<Void> uncollect(@PathVariable Long id) {
        collectMapper.delete(new LambdaQueryWrapper<CommunityCollectEntity>()
                .eq(CommunityCollectEntity::getUserId, UserContextHolder.getUserId())
                .eq(CommunityCollectEntity::getPostId, id));
        return Result.success();
    }

    @Operation(summary = "我收藏的帖子")
    @GetMapping("/posts/collected")
    public Result<List<Long>> myCollected() {
        List<CommunityCollectEntity> list = collectMapper.selectList(
                new LambdaQueryWrapper<CommunityCollectEntity>()
                        .eq(CommunityCollectEntity::getUserId, UserContextHolder.getUserId())
                        .orderByDesc(CommunityCollectEntity::getCreateTime));
        return Result.success(list.stream().map(CommunityCollectEntity::getPostId).toList());
    }

    // === 话题广场 ===
    @Operation(summary = "话题列表（社区广场）")
    @GetMapping("/topics")
    public Result<List<CommunityTopicV2Entity>> topics() {
        return Result.success(topicMapper.selectList(
                new LambdaQueryWrapper<CommunityTopicV2Entity>()
                        .eq(CommunityTopicV2Entity::getActive, 1)
                        .orderByDesc(CommunityTopicV2Entity::getHot)
                        .orderByAsc(CommunityTopicV2Entity::getSortOrder)));
    }

    @Operation(summary = "话题详情")
    @GetMapping("/topics/{id}")
    public Result<CommunityTopicV2Entity> topicDetail(@PathVariable Long id) {
        return Result.success(topicMapper.selectById(id));
    }

    }
