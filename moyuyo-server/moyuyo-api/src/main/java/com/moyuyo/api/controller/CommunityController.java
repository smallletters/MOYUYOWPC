package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.community.CommunityCommentCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.CommunityService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "社区管理")
@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @Operation(summary = "帖子列表（公开）")
    @GetMapping("/posts")
    public Result<Page<CommunityPostVO>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String topic) {
        return Result.success(communityService.listPosts(topic, page, size));
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

    /** 发帖限流降级方法 */
    @SuppressWarnings("unused")
    private Result<CommunityPostVO> postRateLimitFallback(CommunityPostCreateRequest request, RequestNotPermitted e) {
        return Result.error(429, "发帖过于频繁，请稍后再试");
    }

    /** 评论限流降级方法 */
    @SuppressWarnings("unused")
    private Result<Void> commentRateLimitFallback(CommunityCommentCreateRequest request, RequestNotPermitted e) {
        return Result.error(429, "评论过于频繁，请稍后再试");
    }
}