package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.community.CommunityCommentCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostCreateRequest;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.dao.admin.mapper.SensitiveWordMapper;
import com.moyuyo.dao.entity.CommunityCollectEntity;
import com.moyuyo.dao.entity.CommunityTopicV2Entity;
import com.moyuyo.dao.mapper.CommunityCollectMapper;
import com.moyuyo.dao.mapper.CommunityTopicV2Mapper;
import com.moyuyo.service.CommunityService;
import com.moyuyo.service.impl.SensitiveWordFilter;
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
// 类级抑制：MyBatis-Plus 的 LambdaQueryWrapper.eq(OrderItem::getXxx, ...) 等方法引用
// 在 Eclipse JDT 类型推导下，函数描述符是 Function<Entity, Object>，与 lombok @Data 生成的
// @Nonnull Entity 参数不严格匹配 → 报 67109822 (severity 4, info 级)；
// javac 编译时无此告警，且运行时无影响。这里统一在类级别抑制，避免逐行加注解。
@SuppressWarnings("null")
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityCollectMapper collectMapper;
    private final CommunityTopicV2Mapper topicMapper;
    private final SensitiveWordMapper sensitiveWordMapper;

    @Operation(summary = "帖子列表（公开）")
    @GetMapping("/posts")
    public Result<Page<CommunityPostVO>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        // 指定 userId 时只拉该用户帖子(用于他人 profile),与列表/搜索互斥
        if (userId != null) {
            return Result.success(communityService.listPostsByUser(userId, page, size));
        }
        // 关键字非空时走搜索；topic 单独过滤；都不传则拉全部
        if (keyword != null && !keyword.trim().isEmpty()) {
            return Result.success(communityService.searchPosts(keyword.trim(), topic, page, size));
        }
        return Result.success(communityService.listPosts(topic, page, size));
    }

    @Operation(summary = "搜索帖子（与 listPosts?keyword= 等价，便于前端直链）")
    @GetMapping("/search")
    public Result<Page<CommunityPostVO>> searchPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 兼容两种参数名：前端默认用 keyword，旧调用可能用 q
        String term = (keyword != null && !keyword.trim().isEmpty())
                ? keyword.trim()
                : (q != null ? q.trim() : null);
        if (term == null || term.isEmpty()) {
            return Result.success(communityService.listPosts(topic, page, size));
        }
        return Result.success(communityService.searchPosts(term, topic, page, size));
    }

    @Operation(summary = "帖子详情")
    // 用 {id:\\d+} 约束 {id} 只匹配数字，避免与字面量路由 /posts/liked /posts/mine
    // /posts/collected /posts/{id}/like 等冲突；不加正则时 Spring 会先匹配 {id}，
    // 然后因 "liked"/"mine"/"collected" 转 Long 失败抛 400 参数类型错误
    @GetMapping("/posts/{id:\\d+}")
    public Result<CommunityPostVO> getPostDetail(@PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(communityService.getPostDetail(id, userId));
    }

    @Operation(summary = "发布帖子")
    @PostMapping("/posts")
    @RateLimiter(name = "postCreate", fallbackMethod = "postRateLimitFallback")
    public Result<CommunityPostVO> createPost(@Valid @RequestBody CommunityPostCreateRequest request) {
        return Result.success(communityService.createPost(
                UserContextHolder.getUserId(), request.getPetId(), request.getContent(), request.getImages(),
                request.getVideo(), request.getCover(), request.getTopic(), request.getScheduledAt()));
    }

    @Operation(summary = "我的帖子（可带 petId 按宠物过滤，用于宠物记忆树）")
    @GetMapping("/posts/mine")
    public Result<Page<CommunityPostVO>> myPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long petId) {
        return Result.success(communityService.listMyPosts(UserContextHolder.getUserId(), petId, page, size));
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

    /**
     * 删除帖子（仅作者本人）。
     * 业务约束：
     *  - 仅作者本人可调（他人调返回 403）
     *  - 仅允许删除已发布(status=1)的帖子；定时待发布(status=3)的撤回走另一路径
     *  - 级联清理：评论(逻辑删除) / 点赞(物理删除) / 收藏(物理删除) / 审核记录保留
     */
    @Operation(summary = "删除帖子(仅作者本人)")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        communityService.deletePost(UserContextHolder.getUserId(), id);
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

    @Operation(summary = "我收藏的帖子（分页 VO）")
    @GetMapping("/posts/collected")
    public Result<Page<CommunityPostVO>> myCollected(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.listCollectedPosts(
                UserContextHolder.getUserId(), page, size));
    }

    /**
     * 当前用户点赞过的帖子（按点赞时间倒序，分页 VO）。
     * 用于"我的"页 → 个人中心 → 点赞 Tab。
     * 返回的 VO 中 liked 字段恒为 true（同一份 like 表筛选而来），
     * collected 字段反映当前用户是否同时收藏了该帖。
     */
    @Operation(summary = "我点赞的帖子（分页 VO）")
    @GetMapping("/posts/liked")
    public Result<Page<CommunityPostVO>> myLiked(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.listLikedPosts(
                UserContextHolder.getUserId(), page, size));
    }

    /**
     * 实时敏感词检查(用户端):前端发布时调用,不阻断,仅返回命中列表给前端展示。
     * 注意:这是软提示,真正的拒绝仍然在 createPost/addComment 服务端执行。
     */
    @Operation(summary = "实时敏感词检查(不阻断,仅返回命中词)")
    @GetMapping("/sensitive-check")
    public Result<java.util.List<String>> sensitiveCheck(@RequestParam String text) {
        if (text == null || text.isEmpty()) {
            return Result.success(java.util.List.of());
        }
        // 复用 admin mapper 加载启用词
        java.util.List<SensitiveWordEntity> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWordEntity>().eq(SensitiveWordEntity::getStatus, "ENABLED"));
        java.util.List<String> hits = new SensitiveWordFilter(words).findHits(text);
        return Result.success(hits);
    }

    // === 话题广场 ===
    @Operation(summary = "话题列表（社区广场）")
    @GetMapping("/topics")
    public Result<List<CommunityTopicV2Entity>> topics(
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<CommunityTopicV2Entity> wrapper = new LambdaQueryWrapper<CommunityTopicV2Entity>()
                .eq(CommunityTopicV2Entity::getActive, 1);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String safe = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.like(CommunityTopicV2Entity::getName, "%" + safe + "%");
        }
        wrapper.orderByDesc(CommunityTopicV2Entity::getHot)
                .orderByAsc(CommunityTopicV2Entity::getSortOrder);
        return Result.success(topicMapper.selectList(wrapper));
    }

    @Operation(summary = "话题详情")
    @GetMapping("/topics/{id}")
    public Result<CommunityTopicV2Entity> topicDetail(@PathVariable Long id) {
        return Result.success(topicMapper.selectById(id));
    }

    }
