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
import com.moyuyo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final NotificationService notificationService;
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
        // 通配符注入防护：转义 MySQL LIKE 元字符 % _ \
        String safe = keyword == null ? "" : keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getStatus, 1);
        // 多字段搜索:content 包含 + topic 包含 + 发布者 username 包含
        // 用 OR 组合,任何一个字段命中即返回
        if (!safe.isEmpty()) {
            wrapper.and(w -> w
                    .like(CommunityPostEntity::getContent, "%" + safe + "%")
                    .or().like(CommunityPostEntity::getTopic, "%" + safe + "%")
                    // username 字段在 mo_community_post 不存在,需要 join mo_user
                    // 为保持实现简单,这里只用 content + topic 命中;username 命中在另一条 SQL 查 userId 列表后并入
            );
        }
        wrapper.orderByDesc(CommunityPostEntity::getCreateTime);
        if (topic != null && !topic.isEmpty()) {
            wrapper.eq(CommunityPostEntity::getTopic, topic);
        }
        Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size), wrapper);

        // 额外合并:搜索词命中 username 的用户发布的帖子
        if (!safe.isEmpty() && page == 1) {
            java.util.Set<Long> extraUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<UserEntity>().like(UserEntity::getNickname, "%" + safe + "%"))
                    .stream().map(UserEntity::getId).collect(Collectors.toSet());
            if (!extraUserIds.isEmpty()) {
                // 取这部分用户发布的帖子 id(已发布 status=1,且不重复)
                java.util.Set<Long> existingIds = entityPage.getRecords().stream()
                        .map(CommunityPostEntity::getId).collect(Collectors.toSet());
                List<CommunityPostEntity> extra = postMapper.selectList(
                        new LambdaQueryWrapper<CommunityPostEntity>()
                                .eq(CommunityPostEntity::getStatus, 1)
                                .in(CommunityPostEntity::getUserId, extraUserIds)
                                .orderByDesc(CommunityPostEntity::getCreateTime)
                                .last("LIMIT " + size));
                List<CommunityPostEntity> merged = new java.util.ArrayList<>(entityPage.getRecords());
                for (CommunityPostEntity e : extra) {
                    if (!existingIds.contains(e.getId())) merged.add(e);
                }
                entityPage.setRecords(merged);
                entityPage.setTotal((long) Math.max(entityPage.getTotal(), merged.size()));
            }
        }
        return toVOPage(entityPage);
    }

    @Override
    public CommunityPostVO getPostDetail(Long postId, Long currentUserId) {
        CommunityPostEntity entity = postMapper.selectById(postId);
        if (entity == null) throw new IllegalArgumentException("帖子不存在");
        CommunityPostVO vo = toVO(entity);
        vo.setLiked(isLiked(currentUserId, postId));
        vo.setCollected(isCollected(currentUserId, postId));
        // 一次性把帖主 + 所有评论作者都加载到 userMap，避免 N+1 查询
        List<CommunityCommentEntity> comments = listComments(postId);
        Map<Long, UserEntity> userMap = loadUserMap(Stream.concat(
                Stream.of(entity.getUserId()),
                comments.stream().map(CommunityCommentEntity::getUserId)).collect(Collectors.toList()));
        fillPostUserInfo(vo, userMap);
        vo.setCommentList(toCommentVOs(comments, userMap));
        return vo;
    }

    @Override
    @Transactional
    public CommunityPostVO createPost(Long userId, String content, List<String> images, String video, String cover, String topic, java.time.LocalDateTime scheduledAt) {
        String cleanContent = XssSanitizer.sanitizeRichText(content);
        rejectSensitiveContent(cleanContent);

        CommunityPostEntity entity = new CommunityPostEntity();
        entity.setUserId(userId);
        entity.setContent(cleanContent);
        // 视频与图片互斥：有视频则清空图片数组，避免冗余存储
        entity.setImages((video != null && !video.isBlank()) ? null : JsonUtils.toJsonArray(images));
        // video 走 XssSanitizer.sanitizeUrl（仅允许 http/https），防止恶意 javascript: 等协议
        entity.setVideo(XssSanitizer.sanitizeUrl(video));
        // cover 仅视频帖有意义；与 video 同样走 URL 白名单
        entity.setCover(XssSanitizer.sanitizeUrl(cover));
        entity.setTopic(XssSanitizer.sanitizePlainText(topic));
        entity.setLikes(0);
        entity.setComments(0);
        // 状态判定：
        // - scheduledAt 为未来时间：status=3 待发布，存储 scheduled_at 由定时任务到点切换
        // - 否则：status=1 立即发布
        boolean isScheduled = scheduledAt != null && scheduledAt.isAfter(java.time.LocalDateTime.now());
        entity.setStatus(isScheduled ? 3 : 1);
        entity.setScheduledAt(isScheduled ? scheduledAt : null);
        postMapper.insert(entity);
        createReview("POST", entity.getId(), userId, cleanContent, entity.getImages());
        log.info("Post created: postId={}, userId={}, hasVideo={}, scheduled={}",
                entity.getId(), userId, entity.getVideo() != null, isScheduled);

        // 解析 @ 提及并给被 @ 的用户发通知(排除作者自己 + 重复用户)
        notifyMentionedUsers(userId, cleanContent, entity.getId(), "POST", "有人 @ 了你");

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

        // 评论中 @ 提及用户,发通知(排除作者 + 排除帖子原作者,避免骚扰)
        notifyMentionedUsers(userId, cleanContent, comment.getId(), "COMMENT", "评论中 @ 了你");
    }

    @Override
    public Page<CommunityPostVO> listMyPosts(Long userId, int page, int size) {
        Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CommunityPostEntity>()
                        .eq(CommunityPostEntity::getUserId, userId)
                        .orderByDesc(CommunityPostEntity::getCreateTime));
        return toVOPage(entityPage);
    }

    /**
     * 定时发布扫描:
     * - 查询 status=3 且 scheduled_at <= now 的帖子
     * - 批量改 status=1,清空 scheduled_at(可选保留,这里保留以便排查)
     * - 每分钟跑一次(由 ScheduledCommunityTask 触发)
     * 注意:这里不依赖 @Scheduled 注解,以便单测时直接调用。
     */
    @Override
    public int publishScheduledPosts() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<CommunityPostEntity> duePosts = postMapper.selectList(
                new LambdaQueryWrapper<CommunityPostEntity>()
                        .eq(CommunityPostEntity::getStatus, 3)
                        .isNotNull(CommunityPostEntity::getScheduledAt)
                        .le(CommunityPostEntity::getScheduledAt, now));
        if (duePosts == null || duePosts.isEmpty()) {
            return 0;
        }
        for (CommunityPostEntity p : duePosts) {
            p.setStatus(1);
            postMapper.updateById(p);
            log.info("[community-schedule] post published: postId={}, scheduledAt={}", p.getId(), p.getScheduledAt());
        }
        return duePosts.size();
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
        Page<CommunityPostVO> voPage = toVOPage(entityPage, userId);
        // 当前用户视角：liked / collected 字段标注
        if (voPage.getRecords() != null) {
            voPage.getRecords().forEach(v -> {
                v.setLiked(isLiked(userId, v.getId()));
                v.setCollected(isCollected(userId, v.getId()));
            });
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

    /**
     * 解析内容中的 @提及，给被 @ 的用户发通知。
     * 排除作者本人，避免自己 @ 自己。
     */
    private void notifyMentionedUsers(Long authorUserId, String content, Long contentId, String contentType, String message) {
        if (content == null || content.isEmpty()) return;
        // 匹配 @ 后跟非空白字符（用户名）
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@([^\\s@]+)").matcher(content);
        java.util.Set<String> mentionedNames = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            mentionedNames.add(matcher.group(1));
        }
        if (mentionedNames.isEmpty()) return;
        // 根据昵称反查用户
        List<UserEntity> users = userMapper.selectList(
                new LambdaQueryWrapper<UserEntity>().in(UserEntity::getNickname, mentionedNames));
        if (users == null || users.isEmpty()) return;
        for (UserEntity u : users) {
            if (Objects.equals(u.getId(), authorUserId)) continue; // 排除作者自己
            try {
                // title = 内容类型,content = 提示语,relatedId = 内容 ID（点击通知跳转用）
                notificationService.saveNotification(u.getId(), contentType, message, content, contentId);
            } catch (Exception e) {
                log.warn("[community] notify mentioned user failed: userId={}, reason={}", u.getId(), e.getMessage());
            }
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
        return toVOPage(entityPage, null);
    }

    /**
     * toVOPage 重载:userId 用于后续 liked/collected 标记。
     * 当前调用 listPosts/searchPosts/listMyPosts/listCollectedPosts 都传 null(匿名视角),
     * detail 用 setLiked + setCollected 单独处理,后续如果想给列表页加 collected 状态可传 userId。
     */
    private Page<CommunityPostVO> toVOPage(Page<CommunityPostEntity> entityPage, Long userId) {
        if (entityPage.getRecords() == null || entityPage.getRecords().isEmpty()) {
            Page<CommunityPostVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
            voPage.setTotal(entityPage.getTotal());
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        List<Long> userIds = entityPage.getRecords().stream()
            .map(CommunityPostEntity::getUserId).collect(Collectors.toList());
        Map<Long, UserEntity> userMap = loadUserMap(userIds);

        return (Page<CommunityPostVO>) PageUtils.convertPage(entityPage, entity -> {
            CommunityPostVO vo = toVO(entity);
            fillPostUserInfo(vo, userMap);
            return vo;
        });
    }

    private CommunityPostVO toVO(CommunityPostEntity entity) {
        CommunityPostVO vo = new CommunityPostVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setContent(entity.getContent());
        vo.setImages(JsonUtils.parseStringArray(entity.getImages()));
        vo.setVideo(entity.getVideo());
        vo.setCover(entity.getCover());
        vo.setScheduledAt(entity.getScheduledAt());
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

    /**
     * 当前用户是否已收藏该帖子。匿名访问(传 null)统一返回 false。
     * 注:mo_community_collect 没有 @TableLogic deleted 字段(物理删除 + uk_post_user 唯一约束),
     *     直接 count(*) 判断即可。
     */
    private boolean isCollected(Long userId, Long postId) {
        if (userId == null) return false;
        return collectMapper.selectCount(
                new LambdaQueryWrapper<CommunityCollectEntity>()
                        .eq(CommunityCollectEntity::getUserId, userId)
                        .eq(CommunityCollectEntity::getPostId, postId)) > 0;
    }

    private List<CommunityCommentEntity> listComments(Long postId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<CommunityCommentEntity>()
                        .eq(CommunityCommentEntity::getPostId, postId)
                        .orderByAsc(CommunityCommentEntity::getCreateTime));
    }

    /**
     * 批量加载用户 Map：传入 userId 列表，返回 id -> UserEntity。
     * 注意 userMapper 依赖 MoyuyoApplication 主 Bean，启动时已注入。
     */
    private Map<Long, UserEntity> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) return Collections.emptyMap();
        log.info("[community-debug] loadUserMap ids={}", distinct);
        List<UserEntity> users = userMapper.selectBatchIds(distinct);
        log.info("[community-debug] loadUserMap found {} users for ids={}", users == null ? 0 : users.size(), distinct);
        return users.stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));
    }

    /** 把 userMap 中的用户名/头像回填到帖子 VO */
    private void fillPostUserInfo(CommunityPostVO vo, Map<Long, UserEntity> userMap) {
        if (vo == null || vo.getUserId() == null) return;
        UserEntity user = userMap.get(vo.getUserId());
        if (user != null) {
            vo.setUsername(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
    }

    /** 把 userMap 中的用户名/头像回填到评论 VO 列表 */
    private List<CommunityPostVO.CommentVO> toCommentVOs(List<CommunityCommentEntity> comments, Map<Long, UserEntity> userMap) {
        if (comments == null || comments.isEmpty()) return Collections.emptyList();
        return comments.stream().map(comment -> {
            CommunityPostVO.CommentVO vo = new CommunityPostVO.CommentVO();
            vo.setId(comment.getId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setParentId(comment.getParentId());
            vo.setCreateTime(comment.getCreateTime());
            UserEntity user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setUsername(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
