package com.moyuyo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.ContentReviewEntity;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.dao.admin.mapper.ContentReviewMapper;
import com.moyuyo.dao.admin.mapper.SensitiveWordMapper;
import com.moyuyo.dao.entity.BlockEntity;
import com.moyuyo.dao.entity.DmConversationEntity;
import com.moyuyo.dao.entity.DmGroupMemberEntity;
import com.moyuyo.dao.entity.DmMessageDeleteEntity;
import com.moyuyo.dao.entity.DmMessageEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.BlockMapper;
import com.moyuyo.dao.mapper.DmConversationMapper;
import com.moyuyo.dao.mapper.DmGroupMemberMapper;
import com.moyuyo.dao.mapper.DmMessageDeleteMapper;
import com.moyuyo.dao.mapper.DmMessageMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.event.DmConversationChangedEvent;
import com.moyuyo.service.event.DmGroupMessageRecalledEvent;
import com.moyuyo.service.event.DmGroupMessageSentEvent;
import com.moyuyo.service.event.DmMessageReadEvent;
import com.moyuyo.service.event.DmMessageRecalledEvent;
import com.moyuyo.service.event.DmMessageSentEvent;
import com.moyuyo.service.impl.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户间私信服务
 *
 * 核心约束：
 * 1. 一对用户只会有一条会话（user_a < user_b + 唯一索引），并发创建时依赖唯一索引兜底
 * 2. 只有会话参与方可以读写消息，非参与方一律 409
 * 3. 任意一方拉黑另一方后禁止发送（拉黑关系存在 mo_block 表）
 * 4. 未读数按接收方维护在会话表，避免会话列表逐条 COUNT 消息
 */
@Service
@RequiredArgsConstructor
@Slf4j
// 抑制 JDT 对 MyBatis-Plus Lambda 方法引用的 @Nonnull 空值分析误报
@SuppressWarnings("null")
public class DmService {

    /** 单条消息最大长度，与前端输入限制/DB 列宽保持一致 */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /** 图片 URL 最大长度（DB content 列 2000，留出余量） */
    public static final int MAX_IMAGE_URL_LENGTH = 1000;

    /** 支持的消息类型 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("TEXT", "IMAGE", "VOICE", "FILE");

    /** 图片消息在会话列表中的预览占位（前端会按 lastMessageType 做本地化展示） */
    private static final String IMAGE_PREVIEW = "[图片]";

    /** 语音消息在会话列表中的预览占位 */
    private static final String VOICE_PREVIEW = "[语音]";

    /** 文件消息在会话列表中的预览占位 */
    private static final String FILE_PREVIEW = "[文件]";

    /** 语音时长上限（秒） */
    private static final int MAX_VOICE_DURATION_SECONDS = 300;

    /** 文件名最大长度 */
    private static final int MAX_FILE_NAME_LENGTH = 100;

    /** 文件消息大小上限（5GB，实际还受上传接口 multipart 配置限制） */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;

    /** 群聊名称最大长度 */
    private static final int GROUP_NAME_MAX_LENGTH = 50;

    /** 群成员上限（含群主） */
    private static final int GROUP_MAX_MEMBERS = 50;

    /** 群公告最大长度 */
    private static final int GROUP_ANNOUNCEMENT_MAX_LENGTH = 500;

    /** 消息撤回时间窗口：2 分钟（与微信/Douyin 的常用限制保持一致） */
    private static final long RECALL_WINDOW_SECONDS = 120;

    /** 会话列表预览截断长度 */
    private static final int PREVIEW_LENGTH = 200;

    /** 私信发送频控：每个用户每分钟最多发送条数（防脚本刷屏） */
    private static final int SEND_LIMIT_PER_MINUTE = 30;

    /** 私信频控 Redis Key 前缀 */
    private static final String SEND_RATE_KEY_PREFIX = "moyuyo:dm:send:";

    /** 会话内搜索关键词最大长度 */
    private static final int SEARCH_KEYWORD_MAX_LENGTH = 50;

    /** 敏感词缓存 TTL：避免每条消息都全表查询 */
    private static final long SENSITIVE_WORD_CACHE_MS = 60_000L;

    /** 敏感词本地缓存（volatile 保证多线程可见性，定时刷新） */
    private volatile List<SensitiveWordEntity> sensitiveWordCache = Collections.emptyList();
    private volatile long sensitiveWordCacheAt = 0L;

    private final DmConversationMapper conversationMapper;
    private final DmMessageMapper messageMapper;
    private final DmMessageDeleteMapper messageDeleteMapper;
    private final DmGroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final BlockMapper blockMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final ContentReviewMapper contentReviewMapper;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    /**
     * 获取或创建与目标用户的会话（幂等）。
     * 用于「发私信」入口：不发送任何消息，只保证会话存在。
     */
    @Transactional
    public Map<String, Object> ensureConversation(Long userId, Long targetUserId) {
        if (targetUserId == null) throw new IllegalArgumentException("目标用户无效");
        if (Objects.equals(userId, targetUserId)) throw new IllegalArgumentException("不能给自己发私信");
        UserEntity target = userMapper.selectById(targetUserId);
        if (target == null) throw new IllegalArgumentException("用户不存在");

        DmConversationEntity conv = findPair(userId, targetUserId);
        if (conv == null) {
            conv = new DmConversationEntity();
            conv.setUserA(Math.min(userId, targetUserId));
            conv.setUserB(Math.max(userId, targetUserId));
            conv.setUnreadA(0);
            conv.setUnreadB(0);
            // 新建会话也写入活跃时间，避免空会话在列表中永远排在最后
            conv.setLastMessageAt(LocalDateTime.now());
            try {
                conversationMapper.insert(conv);
            } catch (DuplicateKeyException e) {
                // 并发创建时唯一索引兜底：重新读取已存在的会话
                conv = findPair(userId, targetUserId);
                if (conv == null) throw e;
            }
        }
        // 主动打开会话视为恢复：清除自己的删除（隐藏）标记
        restoreVisible(conv, userId);
        return conversationVo(conv, userId, target);
    }

    /**
     * 创建群聊：创建人自动成为群主，同时写入成员记录。
     * 至少邀请 1 位成员，群成员上限 50（含群主）。
     */
    @Transactional
    public Map<String, Object> createGroup(Long userId, String name, List<Long> memberIds) {
        String groupName = validateGroupName(name);
        Set<Long> invited = normalizeMemberIds(memberIds);
        invited.remove(userId);
        if (invited.isEmpty()) throw new IllegalArgumentException("请至少选择一位群成员");
        if (invited.size() + 1 > GROUP_MAX_MEMBERS) {
            throw new IllegalArgumentException("群成员不能超过" + GROUP_MAX_MEMBERS + "人");
        }
        // 校验被邀请用户都存在，避免拉入不存在的账号
        List<UserEntity> users = userMapper.selectByIds(invited);
        if (users == null || users.size() != invited.size()) {
            throw new IllegalArgumentException("部分群成员不存在");
        }

        DmConversationEntity conv = new DmConversationEntity();
        conv.setType("GROUP");
        conv.setName(groupName);
        conv.setOwnerId(userId);
        // 群聊 user_a/user_b 为 NULL，未读由成员表维护
        conv.setUnreadA(0);
        conv.setUnreadB(0);
        conv.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conv);

        insertGroupMember(conv.getId(), userId, "OWNER");
        for (Long memberId : invited) {
            insertGroupMember(conv.getId(), memberId, "MEMBER");
        }
        // 通知所有成员刷新会话列表（含群主本人多端）
        notifyGroupChanged(conv.getId(), "created");
        return conversationVo(conv, userId, null);
    }

    /**
     * 修改群设置（仅群主）：群名 / 群公告 / 群头像。
     * 三个字段均为可选，传空字符串表示清空公告或头像。
     */
    @Transactional
    public Map<String, Object> updateGroup(Long userId, Long conversationId,
                                           String name, String announcement, String avatar) {
        DmConversationEntity conv = requireGroup(userId, conversationId);
        if (!Objects.equals(conv.getOwnerId(), userId)) {
            throw new IllegalStateException("仅群主可以修改群设置");
        }
        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conversationId);
        boolean changed = false;

        if (name != null) {
            String groupName = validateGroupName(name);
            uw.set(DmConversationEntity::getName, groupName);
            conv.setName(groupName);
            changed = true;
        }
        if (announcement != null) {
            String text = announcement.trim();
            if (text.length() > GROUP_ANNOUNCEMENT_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "群公告不能超过" + GROUP_ANNOUNCEMENT_MAX_LENGTH + "字");
            }
            uw.set(DmConversationEntity::getAnnouncement, text.isEmpty() ? null : text);
            conv.setAnnouncement(text.isEmpty() ? null : text);
            changed = true;
        }
        if (avatar != null) {
            String url = avatar.trim();
            if (!url.isEmpty()) {
                validateMediaUrl(url, "群头像");
            }
            uw.set(DmConversationEntity::getAvatar, url.isEmpty() ? null : url);
            conv.setAvatar(url.isEmpty() ? null : url);
            changed = true;
        }
        if (!changed) throw new IllegalArgumentException("没有可修改的内容");

        conversationMapper.update(null, uw);
        notifyGroupChanged(conversationId, "members_changed");
        return conversationVo(conv, userId, null);
    }

    /** 群详情：群名/群主/成员数 + 会话摘要 */
    public Map<String, Object> groupDetail(Long userId, Long conversationId) {
        DmConversationEntity conv = requireGroup(userId, conversationId);
        Map<String, Object> vo = conversationVo(conv, userId, null);
        vo.put("memberCount", groupMemberMapper.selectCount(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .eq(DmGroupMemberEntity::getConversationId, conversationId)));
        return vo;
    }

    /** 群成员列表（含昵称/头像/角色），仅群成员可查看 */
    public List<Map<String, Object>> listGroupMembers(Long userId, Long conversationId) {
        requireGroup(userId, conversationId);
        List<DmGroupMemberEntity> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .eq(DmGroupMemberEntity::getConversationId, conversationId)
                        .orderByAsc(DmGroupMemberEntity::getCreateTime));
        if (members == null || members.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = members.stream()
                .map(DmGroupMemberEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, u -> u));

        return members.stream().map(member -> {
            UserEntity user = userMap.get(member.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", member.getUserId());
            item.put("nickname", user == null ? "" : user.getNickname());
            item.put("avatar", user == null ? null : user.getAvatar());
            item.put("role", member.getRole());
            item.put("owner", "OWNER".equals(member.getRole()));
            item.put("isSelf", Objects.equals(member.getUserId(), userId));
            item.put("joinTime", member.getCreateTime());
            return item;
        }).toList();
    }

    /** 邀请成员（仅群主），自动忽略已在群内的用户 */
    @Transactional
    public int addGroupMembers(Long userId, Long conversationId, List<Long> memberIds) {
        DmConversationEntity conv = requireGroup(userId, conversationId);
        if (!Objects.equals(conv.getOwnerId(), userId)) {
            throw new IllegalStateException("仅群主可以邀请成员");
        }
        Set<Long> invited = normalizeMemberIds(memberIds);
        invited.remove(userId);
        if (invited.isEmpty()) return 0;

        long current = groupMemberMapper.selectCount(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .eq(DmGroupMemberEntity::getConversationId, conversationId));
        Set<Long> existing = groupMemberMapper.selectList(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .select(DmGroupMemberEntity::getUserId)
                        .eq(DmGroupMemberEntity::getConversationId, conversationId))
                .stream()
                .map(DmGroupMemberEntity::getUserId)
                .collect(Collectors.toSet());
        invited.removeAll(existing);
        if (invited.isEmpty()) return 0;
        if (current + invited.size() > GROUP_MAX_MEMBERS) {
            throw new IllegalArgumentException("群成员不能超过" + GROUP_MAX_MEMBERS + "人");
        }
        List<UserEntity> users = userMapper.selectByIds(invited);
        if (users == null || users.size() != invited.size()) {
            throw new IllegalArgumentException("部分群成员不存在");
        }

        int added = 0;
        for (Long memberId : invited) {
            if (insertGroupMember(conversationId, memberId, "MEMBER")) added += 1;
        }
        notifyGroupChanged(conversationId, "members_changed");
        return added;
    }

    /**
     * 移除成员：群主可移除他人；任何成员可移除自己（退群）。
     * 群主退群时自动把群主转让给最早加入的其他成员。
     */
    @Transactional
    public void removeGroupMember(Long userId, Long conversationId, Long targetUserId) {
        DmConversationEntity conv = requireGroup(userId, conversationId);
        if (targetUserId == null) throw new IllegalArgumentException("成员无效");
        DmGroupMemberEntity target = groupMember(conversationId, targetUserId);
        if (target == null) throw new IllegalArgumentException("该用户不在群内");

        boolean self = Objects.equals(userId, targetUserId);
        if (!self && !Objects.equals(conv.getOwnerId(), userId)) {
            throw new IllegalStateException("仅群主可以移除成员");
        }
        // 通知在删除前发送，保证被移除成员也能收到列表刷新事件
        notifyGroupChanged(conversationId, "members_changed");

        groupMemberMapper.deleteById(target.getId());

        // 群主退群：转让群主给最早加入的剩余成员
        if (Objects.equals(conv.getOwnerId(), targetUserId)) {
            DmGroupMemberEntity next = groupMemberMapper.selectOne(
                    new LambdaQueryWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conversationId)
                            .orderByAsc(DmGroupMemberEntity::getCreateTime)
                            .last("LIMIT 1"));
            if (next != null) {
                conversationMapper.update(null,
                        new LambdaUpdateWrapper<DmConversationEntity>()
                                .eq(DmConversationEntity::getId, conversationId)
                                .set(DmConversationEntity::getOwnerId, next.getUserId()));
                groupMemberMapper.update(null,
                        new LambdaUpdateWrapper<DmGroupMemberEntity>()
                                .eq(DmGroupMemberEntity::getId, next.getId())
                                .set(DmGroupMemberEntity::getRole, "OWNER"));
            }
        }
        // 群已无成员时通知已移除成员刷新列表即可
        notifyGroupChanged(conversationId, "members_changed");
    }

    /** 群名称校验：去空格后 1~50 字 */
    private String validateGroupName(String name) {
        String groupName = name == null ? "" : name.trim();
        if (groupName.isEmpty()) throw new IllegalArgumentException("群名称不能为空");
        if (groupName.length() > GROUP_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("群名称不能超过" + GROUP_NAME_MAX_LENGTH + "字");
        }
        return groupName;
    }

    /** 成员ID去重（忽略 null） */
    private Set<Long> normalizeMemberIds(List<Long> memberIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    /** 写入群成员：已存在（如被移出后重新邀请）则恢复可见并重置角色 */
    private boolean insertGroupMember(Long conversationId, Long memberId, String role) {
        DmGroupMemberEntity entity = new DmGroupMemberEntity();
        entity.setConversationId(conversationId);
        entity.setUserId(memberId);
        entity.setUnread(0);
        entity.setPinned(0);
        entity.setDeleted(0);
        entity.setRole(role);
        try {
            groupMemberMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException ignored) {
            // 已是成员：恢复可见，不重复插入
            groupMemberMapper.update(null,
                    new LambdaUpdateWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conversationId)
                            .eq(DmGroupMemberEntity::getUserId, memberId)
                            .set(DmGroupMemberEntity::getDeleted, 0));
            return false;
        }
    }

    /** 通知群内所有成员刷新会话列表 */
    private void notifyGroupChanged(Long conversationId, String action) {
        for (Long memberId : groupMemberIds(conversationId)) {
            eventPublisher.publishEvent(new DmConversationChangedEvent(
                    memberId, conversationId, action, null));
        }
    }

    /** 校验会话是群聊且当前用户在群内 */
    private DmConversationEntity requireGroup(Long userId, Long conversationId) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        if (!isGroup(conv)) throw new IllegalArgumentException("该会话不是群聊");
        return conv;
    }

    /** 会话列表：单聊 + 群聊，置顶优先 + 最后消息时间倒序，附加对方昵称/头像与未读数 */
    public Page<Map<String, Object>> listConversations(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long total = conversationMapper.countUserConversations(userId);
        List<Map<String, Object>> rows = conversationMapper.selectUserConversations(
                userId, (safePage - 1) * safeSize, safeSize);

        // 单聊需要对方昵称/头像，批量查询避免 N+1；群聊直接使用群名
        Set<Long> targetIds = rows.stream()
                .filter(row -> !isGroupType(row.get("type")))
                .map(row -> {
                    Long userA = toLong(row.get("userA"));
                    Long userB = toLong(row.get("userB"));
                    return Objects.equals(userA, userId) ? userB : userA;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserEntity> userMap = targetIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectByIds(targetIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, u -> u));

        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            records.add(conversationVoFromRow(row, userId, userMap));
        }
        Page<Map<String, Object>> voPage = new Page<>(safePage, safeSize, total);
        voPage.setRecords(records);
        return voPage;
    }

    /** 会话列表行 → VO（单聊补对方资料，群聊补群名/群主） */
    private Map<String, Object> conversationVoFromRow(
            Map<String, Object> row, Long userId, Map<Long, UserEntity> userMap) {
        boolean group = isGroupType(row.get("type"));
        Long targetId = null;
        UserEntity target = null;
        if (!group) {
            Long userA = toLong(row.get("userA"));
            Long userB = toLong(row.get("userB"));
            targetId = Objects.equals(userA, userId) ? userB : userA;
            target = userMap.get(targetId);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("type", group ? "GROUP" : "SINGLE");
        m.put("targetUserId", targetId);
        if (group) {
            String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
            String avatar = row.get("avatar") == null ? null : String.valueOf(row.get("avatar"));
            m.put("nickname", name);
            m.put("name", name);
            m.put("avatar", avatar);
            m.put("ownerId", row.get("ownerId"));
            m.put("announcement", row.get("announcement"));
        } else {
            m.put("nickname", target == null ? "" : target.getNickname());
            m.put("avatar", target == null ? null : target.getAvatar());
            m.put("ownerId", null);
        }
        m.put("lastMessage", row.get("lastMessageContent"));
        m.put("lastMessageType", row.get("lastMessageType") == null
                ? "TEXT" : String.valueOf(row.get("lastMessageType")));
        m.put("lastMessageAt", row.get("lastMessageAt"));
        m.put("unreadCount", toInt(row.get("unreadCount")));
        m.put("pinned", toInt(row.get("pinned")) > 0);
        m.put("createTime", row.get("createTime"));
        return m;
    }

    /**
     * 会话消息（分页，单页内按时间正序返回，便于聊天气泡顺序渲染）。
     * 打开会话时顺带把接收方未读消息标记为已读，并清零会话未读数。
     */
    @Transactional
    public Page<DmMessageEntity> listMessages(Long userId, Long conversationId, int page, int size) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        markReadInternal(userId, conv);
        List<Long> hiddenIds = hiddenMessageIds(userId, conversationId);

        Page<DmMessageEntity> pageObj = messageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<DmMessageEntity>()
                        .eq(DmMessageEntity::getConversationId, conversationId)
                        // 用户级删除：只对自己隐藏，不影响对方
                        .notIn(!hiddenIds.isEmpty(), DmMessageEntity::getId, hiddenIds)
                        .orderByDesc(DmMessageEntity::getCreateTime)
                        .orderByDesc(DmMessageEntity::getId));
        // 查询按倒序取最近 N 条，返回前反转为正序，前端可直接追加渲染
        List<DmMessageEntity> records = new ArrayList<>(pageObj.getRecords());
        Collections.reverse(records);
        // 群聊消息补充发送者昵称/头像，前端按消息展示不同头像
        if (isGroup(conv)) {
            enrichGroupSenders(records);
        }
        pageObj.setRecords(records);
        return pageObj;
    }

    /** 批量补充群聊消息发送者昵称/头像（transient 字段，不落库） */
    private void enrichGroupSenders(List<DmMessageEntity> records) {
        if (records == null || records.isEmpty()) return;
        Set<Long> senderIds = records.stream()
                .map(DmMessageEntity::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (senderIds.isEmpty()) return;
        Map<Long, UserEntity> userMap = userMapper.selectByIds(senderIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
        for (DmMessageEntity msg : records) {
            UserEntity sender = userMap.get(msg.getSenderId());
            if (sender != null) {
                msg.setSenderNickname(sender.getNickname());
                msg.setSenderAvatar(sender.getAvatar());
            }
        }
    }

    /**
     * 会话内消息搜索：仅匹配未撤回的文本消息，按时间倒序分页返回。
     * 空关键词返回空页，避免把整个会话拉出来。
     */
    public Page<DmMessageEntity> searchMessages(
            Long userId, Long conversationId, String keyword, int page, int size) {
        requireConversation(userId, conversationId);
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            Page<DmMessageEntity> empty = new Page<>(page, size, 0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        if (kw.length() > SEARCH_KEYWORD_MAX_LENGTH) {
            kw = kw.substring(0, SEARCH_KEYWORD_MAX_LENGTH);
        }
        List<Long> hiddenIds = hiddenMessageIds(userId, conversationId);
        return messageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<DmMessageEntity>()
                        .eq(DmMessageEntity::getConversationId, conversationId)
                        .eq(DmMessageEntity::getRecalled, 0)
                        .eq(DmMessageEntity::getContentType, "TEXT")
                        .notIn(!hiddenIds.isEmpty(), DmMessageEntity::getId, hiddenIds)
                        .like(DmMessageEntity::getContent, kw)
                        .orderByDesc(DmMessageEntity::getCreateTime)
                        .orderByDesc(DmMessageEntity::getId));
    }

    /**
     * 批量删除消息（仅对自己隐藏）：写入用户级删除记录，不影响对方会话视图。
     */
    @Transactional
    public int deleteMessages(Long userId, Long conversationId, List<Long> messageIds) {
        requireConversation(userId, conversationId);
        if (messageIds == null || messageIds.isEmpty()) return 0;

        List<DmMessageEntity> found = messageMapper.selectList(
                new LambdaQueryWrapper<DmMessageEntity>()
                        .select(DmMessageEntity::getId)
                        .eq(DmMessageEntity::getConversationId, conversationId)
                        .in(DmMessageEntity::getId, messageIds));
        int deleted = 0;
        for (DmMessageEntity msg : found) {
            DmMessageDeleteEntity record = new DmMessageDeleteEntity();
            record.setUserId(userId);
            record.setMessageId(msg.getId());
            record.setConversationId(conversationId);
            try {
                messageDeleteMapper.insert(record);
                deleted += 1;
            } catch (DuplicateKeyException ignored) {
                // 重复删除同一消息：唯一索引兜底，忽略即可
            }
        }
        // 多端同步：通知同账号其他在线端移除这些消息
        if (deleted > 0) {
            eventPublisher.publishEvent(new DmConversationChangedEvent(
                    userId, conversationId, "messages_deleted",
                    found.stream().map(DmMessageEntity::getId).toList()));
        }
        return deleted;
    }

    /**
     * 举报私信消息/用户：写入管理端内容审核队列（复用 mo_content_review），
     * contentType = DM_MESSAGE（举报某条消息）/ DM_USER（举报用户）。
     * 同一举报人对同一对象 24 小时内只允许一次，避免刷举报。
     */
    @Transactional
    public void report(Long reporterId, Long targetUserId, Long messageId,
                       String reason, String description) {
        if (targetUserId == null) throw new IllegalArgumentException("举报对象无效");
        String shortReason = reason == null ? "" : reason.trim();
        if (shortReason.isEmpty()) throw new IllegalArgumentException("举报原因不能为空");
        // reason 列为 VARCHAR(16)（按字符计），最多保留 16 个字符
        if (shortReason.length() > 16) shortReason = shortReason.substring(0, 16);

        String contentType = "DM_USER";
        Long contentId = targetUserId;
        String excerpt = "";
        if (messageId != null) {
            DmMessageEntity msg = messageMapper.selectById(messageId);
            if (msg == null) throw new IllegalArgumentException("消息不存在");
            // 只能举报自己参与的会话中的消息
            requireConversation(reporterId, msg.getConversationId());
            contentType = "DM_MESSAGE";
            contentId = messageId;
            targetUserId = msg.getSenderId();
            excerpt = (msg.getRecalled() != null && msg.getRecalled() == 1)
                    ? "[已撤回]"
                    : previewForType(msg.getContentType(), msg.getContent());
        }

        long recent = contentReviewMapper.selectCount(
                new LambdaQueryWrapper<ContentReviewEntity>()
                        .eq(ContentReviewEntity::getContentType, contentType)
                        .eq(ContentReviewEntity::getContentId, contentId)
                        .eq(ContentReviewEntity::getReporterId, reporterId)
                        .ge(ContentReviewEntity::getCreateTime, LocalDateTime.now().minusDays(1)));
        if (recent > 0) {
            throw new IllegalStateException("请勿重复举报");
        }

        ContentReviewEntity review = new ContentReviewEntity();
        review.setContentType(contentType);
        review.setContentId(contentId);
        review.setUserId(targetUserId);
        review.setReporterId(reporterId);
        review.setContentExcerpt(excerpt);
        review.setReason(shortReason);
        review.setReviewComment(description == null ? "" : description.trim());
        review.setStatus("PENDING");
        review.setAutoFlag(0);
        contentReviewMapper.insert(review);
    }

    /**
     * 发送消息：校验参与方 + 拉黑关系 + 内容合法性，写入消息并更新会话摘要/未读数
     *
     * @param contentType TEXT 文本 / IMAGE 图片 / VOICE 语音 / FILE 文件（后三者 content 为上传接口返回的 URL）
     * @param mediaDuration 语音时长（秒），仅 VOICE 必填
     * @param mediaName     文件名，仅 FILE 必填
     * @param mediaSize     文件大小（字节），仅 FILE 必填
     */
    @Transactional
    public DmMessageEntity sendMessage(Long userId, Long conversationId, String content,
                                       String contentType, Long quoteMessageId,
                                       Integer mediaDuration, String mediaName, Long mediaSize) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        // 频控放在最前面：命中后直接拒绝，避免占用后续校验与写库资源
        checkSendRate(userId);

        String type = contentType == null || contentType.isBlank()
                ? "TEXT"
                : contentType.trim().toUpperCase();
        if (!ALLOWED_CONTENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的消息类型");
        }

        String normalized = content == null ? "" : content.trim();
        switch (type) {
            case "IMAGE" -> validateMediaUrl(normalized, "图片");
            case "VOICE" -> {
                validateMediaUrl(normalized, "语音");
                if (mediaDuration == null
                        || mediaDuration < 1
                        || mediaDuration > MAX_VOICE_DURATION_SECONDS) {
                    throw new IllegalArgumentException("语音时长无效");
                }
            }
            case "FILE" -> {
                validateMediaUrl(normalized, "文件");
                // 去掉客户端可能携带的路径前缀，只保留纯文件名
                String name = mediaName == null ? "" : mediaName.trim().replace('\\', '/');
                int slash = name.lastIndexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);
                if (name.isEmpty() || name.length() > MAX_FILE_NAME_LENGTH) {
                    throw new IllegalArgumentException("文件名无效");
                }
                if (mediaSize == null || mediaSize <= 0 || mediaSize > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("文件大小无效");
                }
                mediaName = name;
            }
            default -> {
                if (normalized.isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
                if (normalized.length() > MAX_CONTENT_LENGTH) {
                    throw new IllegalArgumentException("消息内容不能超过" + MAX_CONTENT_LENGTH + "字");
                }
                // 敏感词校验：私信与社区共用同一套敏感词库（带本地缓存，避免每条消息全表查询）
                rejectSensitiveText(normalized);
            }
        }

        // 引用回复：校验被引用消息属于同一会话且未撤回，冗余保存摘要
        String quoteContent = null;
        if (quoteMessageId != null) {
            DmMessageEntity quoted = messageMapper.selectById(quoteMessageId);
            if (quoted == null
                    || !Objects.equals(quoted.getConversationId(), conversationId)
                    || (quoted.getRecalled() != null && quoted.getRecalled() == 1)) {
                throw new IllegalArgumentException("引用的消息不存在或已撤回");
            }
            quoteContent = previewForType(quoted.getContentType(), quoted.getContent());
        }

        boolean group = isGroup(conv);
        Long receiverId = group
                ? 0L
                : (Objects.equals(conv.getUserA(), userId) ? conv.getUserB() : conv.getUserA());
        // 群聊不做双方拉黑/隐私开关校验；单聊保持原有校验
        if (!group) {
            // 双向拉黑校验：我拉黑对方 或 对方拉黑我，都不允许发送
            if (isBlocked(userId, receiverId) || isBlocked(receiverId, userId)) {
                throw new IllegalStateException("无法向该用户发送私信");
            }
            // 隐私开关：对方关闭「允许私信」后，不允许再向其发送新消息（历史记录仍可查看）
            UserEntity receiver = userMapper.selectById(receiverId);
            if (receiver != null && Boolean.FALSE.equals(receiver.getAllowMessages())) {
                throw new IllegalStateException("对方已关闭私信");
            }
        }

        DmMessageEntity message = new DmMessageEntity();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setReceiverId(receiverId);
        message.setContent(normalized);
        message.setContentType(type);
        message.setQuoteMessageId(quoteMessageId);
        message.setQuoteContent(quoteContent);
        message.setReadFlag(0);
        // 媒体元信息：只写入与类型匹配的字段，避免脏数据
        if ("VOICE".equals(type)) {
            message.setMediaDuration(mediaDuration);
        } else if ("FILE".equals(type)) {
            message.setMediaName(mediaName);
            message.setMediaSize(mediaSize);
        }
        messageMapper.insert(message);

        // 原子更新会话的最后一条消息（单聊额外处理未读与删除标记）
        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conversationId)
                .set(DmConversationEntity::getLastMessageId, message.getId())
                .set(DmConversationEntity::getLastMessageType, type)
                .set(DmConversationEntity::getLastMessageContent,
                        previewForType(type, normalized))
                .set(DmConversationEntity::getLastMessageAt, LocalDateTime.now());
        if (!group) {
            uw.set(DmConversationEntity::getDeletedA, 0)
                    .set(DmConversationEntity::getDeletedB, 0);
            if (Objects.equals(conv.getUserA(), userId)) {
                uw.setSql("unread_b = unread_b + 1");
            } else {
                uw.setSql("unread_a = unread_a + 1");
            }
        }
        conversationMapper.update(null, uw);

        UserEntity sender = userMapper.selectById(userId);
        String senderNickname = sender == null ? "" : sender.getNickname();
        String senderAvatar = sender == null ? null : sender.getAvatar();
        if (group) {
            // 群成员未读 +1（发送者除外），并把隐藏该群的成员恢复显示
            List<Long> memberIds = groupMemberMapper.selectList(
                    new LambdaQueryWrapper<DmGroupMemberEntity>()
                            .select(DmGroupMemberEntity::getUserId)
                            .eq(DmGroupMemberEntity::getConversationId, conversationId))
                    .stream()
                    .map(DmGroupMemberEntity::getUserId)
                    .filter(Objects::nonNull)
                    .toList();
            groupMemberMapper.increaseUnreadForOthers(conversationId, userId);
            // @成员：给被提到的群成员写站内通知（互动分类）
            notifyGroupMentions(conv, userId, senderNickname, normalized, type);
            // 事务提交后推送给群内其他成员（发送者本人由本地消息回显）
            eventPublisher.publishEvent(new DmGroupMessageSentEvent(
                    memberIds, userId, conversationId, message, senderNickname, senderAvatar));
        } else {
            // 事务提交后由 WebSocket 监听器推送给接收方（见 DmWebSocketEventListener）
            eventPublisher.publishEvent(new DmMessageSentEvent(
                    receiverId, conversationId, message, senderNickname, senderAvatar));
        }
        return message;
    }

    /** 主动标记会话已读（前端返回消息页时可兜底调用） */
    @Transactional
    public void markRead(Long userId, Long conversationId) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        markReadInternal(userId, conv);
    }

    /** 置顶/取消置顶会话（只影响当前用户自己的列表排序） */
    @Transactional
    public Map<String, Object> pinConversation(Long userId, Long conversationId, boolean pinned) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        if (isGroup(conv)) {
            groupMemberMapper.update(null,
                    new LambdaUpdateWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conversationId)
                            .eq(DmGroupMemberEntity::getUserId, userId)
                            .set(DmGroupMemberEntity::getPinned, pinned ? 1 : 0));
            // 多端同步：同账号其他端刷新会话列表排序
            eventPublisher.publishEvent(new DmConversationChangedEvent(
                    userId, conversationId, "pin", null));
            return conversationVo(conv, userId, null);
        }
        boolean isA = Objects.equals(conv.getUserA(), userId);
        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conversationId);
        if (isA) {
            uw.set(DmConversationEntity::getPinnedA, pinned ? 1 : 0);
            conv.setPinnedA(pinned ? 1 : 0);
        } else {
            uw.set(DmConversationEntity::getPinnedB, pinned ? 1 : 0);
            conv.setPinnedB(pinned ? 1 : 0);
        }
        conversationMapper.update(null, uw);

        // 多端同步：同账号其他端刷新会话列表排序
        eventPublisher.publishEvent(new DmConversationChangedEvent(
                userId, conversationId, "pin", null));

        Long targetId = isA ? conv.getUserB() : conv.getUserA();
        return conversationVo(conv, userId, userMapper.selectById(targetId));
    }

    /**
     * 删除会话：仅对当前用户隐藏（软删除），消息明细与对方会话保持不变。
     * 收到新消息或主动打开会话时自动恢复显示。
     */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        if (isGroup(conv)) {
            groupMemberMapper.update(null,
                    new LambdaUpdateWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conversationId)
                            .eq(DmGroupMemberEntity::getUserId, userId)
                            .set(DmGroupMemberEntity::getDeleted, 1)
                            .set(DmGroupMemberEntity::getPinned, 0)
                            .set(DmGroupMemberEntity::getUnread, 0));
            eventPublisher.publishEvent(new DmConversationChangedEvent(
                    userId, conversationId, "conversation_deleted", null));
            return;
        }
        boolean isA = Objects.equals(conv.getUserA(), userId);
        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conversationId);
        if (isA) {
            uw.set(DmConversationEntity::getDeletedA, 1)
                    .set(DmConversationEntity::getPinnedA, 0)
                    .set(DmConversationEntity::getUnreadA, 0);
        } else {
            uw.set(DmConversationEntity::getDeletedB, 1)
                    .set(DmConversationEntity::getPinnedB, 0)
                    .set(DmConversationEntity::getUnreadB, 0);
        }
        conversationMapper.update(null, uw);
        // 多端同步：同账号其他端移除该会话
        eventPublisher.publishEvent(new DmConversationChangedEvent(
                userId, conversationId, "conversation_deleted", null));
    }

    /**
     * 撤回消息
     *
     * 限制：
     * 1. 只能撤回自己发送的消息
     * 2. 只能撤回 2 分钟内的消息
     * 3. 撤回后清空 content，并把会话预览回退到上一条正常消息
     */
    @Transactional
    public DmMessageEntity recallMessage(Long userId, Long conversationId, Long messageId) {
        DmConversationEntity conv = requireConversation(userId, conversationId);
        DmMessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !Objects.equals(message.getConversationId(), conversationId)) {
            throw new IllegalArgumentException("消息不存在");
        }
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new IllegalStateException("无权撤回该消息");
        }
        if (message.getRecalled() != null && message.getRecalled() == 1) {
            throw new IllegalStateException("消息已撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        if (message.getCreateTime() == null
                || message.getCreateTime().isBefore(now.minusSeconds(RECALL_WINDOW_SECONDS))) {
            throw new IllegalStateException("超过撤回时间");
        }

        // 标记撤回并清空正文，保证原内容无法再从接口读取
        messageMapper.update(null,
                new LambdaUpdateWrapper<DmMessageEntity>()
                        .eq(DmMessageEntity::getId, messageId)
                        .set(DmMessageEntity::getRecalled, 1)
                        .set(DmMessageEntity::getRecallTime, now)
                        .set(DmMessageEntity::getContent, ""));
        message.setRecalled(1);
        message.setRecallTime(now);
        message.setContent("");

        // 群聊：除发送者外成员未读回退；单聊按接收方未读状态回退
        if (isGroup(conv)) {
            groupMemberMapper.decreaseUnreadForOthers(conversationId, userId);
        } else if (message.getReadFlag() != null && message.getReadFlag() == 0) {
            LambdaUpdateWrapper<DmConversationEntity> unreadW = new LambdaUpdateWrapper<>();
            unreadW.eq(DmConversationEntity::getId, conversationId);
            if (Objects.equals(message.getReceiverId(), conv.getUserA())) {
                unreadW.setSql("unread_a = GREATEST(unread_a - 1, 0)");
            } else {
                unreadW.setSql("unread_b = GREATEST(unread_b - 1, 0)");
            }
            conversationMapper.update(null, unreadW);
        }

        // 会话预览回退：取最近一条未撤回消息；若已无消息则清空预览
        DmMessageEntity latest = messageMapper.selectOne(
                new LambdaQueryWrapper<DmMessageEntity>()
                        .eq(DmMessageEntity::getConversationId, conversationId)
                        .eq(DmMessageEntity::getRecalled, 0)
                        .orderByDesc(DmMessageEntity::getCreateTime)
                        .orderByDesc(DmMessageEntity::getId)
                        .last("LIMIT 1"));
        LambdaUpdateWrapper<DmConversationEntity> convW = new LambdaUpdateWrapper<>();
        convW.eq(DmConversationEntity::getId, conversationId);
        if (latest == null) {
            convW.set(DmConversationEntity::getLastMessageId, null)
                    .set(DmConversationEntity::getLastMessageType, null)
                    .set(DmConversationEntity::getLastMessageContent, null)
                    .set(DmConversationEntity::getLastMessageAt, null);
        } else {
            String preview = previewForType(latest.getContentType(), latest.getContent());
            convW.set(DmConversationEntity::getLastMessageId, latest.getId())
                    .set(DmConversationEntity::getLastMessageType, latest.getContentType())
                    .set(DmConversationEntity::getLastMessageContent, preview)
                    .set(DmConversationEntity::getLastMessageAt, latest.getCreateTime());
        }
        conversationMapper.update(null, convW);

        // 事务提交后推送：群聊推送给全部成员，单聊推给双方（多端同步）
        if (isGroup(conv)) {
            eventPublisher.publishEvent(new DmGroupMessageRecalledEvent(
                    groupMemberIds(conversationId), conversationId, messageId, userId));
        } else {
            eventPublisher.publishEvent(new DmMessageRecalledEvent(
                    message.getReceiverId(), userId, conversationId, messageId));
        }
        return message;
    }

    /** 当前用户私信总未读数（单聊 + 群聊） */
    public long countUnread(Long userId) {
        Long single = conversationMapper.sumUnread(userId);
        Long group = groupMemberMapper.sumUnread(userId);
        return (single == null ? 0L : single) + (group == null ? 0L : group);
    }

    /** 群成员ID列表（用于 WebSocket 广播） */
    private List<Long> groupMemberIds(Long conversationId) {
        return groupMemberMapper.selectList(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .select(DmGroupMemberEntity::getUserId)
                        .eq(DmGroupMemberEntity::getConversationId, conversationId))
                .stream()
                .map(DmGroupMemberEntity::getUserId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 群聊 @成员：按成员昵称匹配文本中的「@昵称」，给被提到的成员写互动通知。
     * 通知失败不影响消息发送主流程。
     */
    private void notifyGroupMentions(DmConversationEntity conv, Long senderId,
                                     String senderNickname, String content, String type) {
        if (!"TEXT".equals(type) || content == null || !content.contains("@")) return;
        try {
            List<DmGroupMemberEntity> members = groupMemberMapper.selectList(
                    new LambdaQueryWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conv.getId()));
            Set<Long> memberIds = members.stream()
                    .map(DmGroupMemberEntity::getUserId)
                    .filter(id -> id != null && !Objects.equals(id, senderId))
                    .collect(Collectors.toSet());
            if (memberIds.isEmpty()) return;

            Map<Long, UserEntity> userMap = userMapper.selectByIds(memberIds).stream()
                    .collect(Collectors.toMap(UserEntity::getId, u -> u));
            String groupName = conv.getName() == null ? "" : conv.getName();
            String snippet = content.length() > 120
                    ? content.substring(0, 120) + "..." : content;

            for (Long memberId : memberIds) {
                UserEntity user = userMap.get(memberId);
                String nickname = user == null ? null : user.getNickname();
                if (nickname == null || nickname.isBlank()) continue;
                // 昵称可能包含空格，直接按完整昵称匹配，避免误切分
                if (!content.contains("@" + nickname)) continue;
                notificationService.saveNotification(
                        memberId,
                        "activity",
                        "群聊中有人@你",
                        (senderNickname == null ? "" : senderNickname)
                                + " 在群「" + groupName + "」中提到了你：" + snippet,
                        conv.getId());
            }
        } catch (Exception e) {
            log.warn("[dm] notify group mentions failed: conversationId={}, error={}",
                    conv.getId(), e.getMessage());
        }
    }

    /** 查询两人之间的会话（user_a 固定为较小 ID） */
    private DmConversationEntity findPair(Long userId, Long targetUserId) {
        return conversationMapper.selectOne(
                new LambdaQueryWrapper<DmConversationEntity>()
                        .eq(DmConversationEntity::getUserA, Math.min(userId, targetUserId))
                        .eq(DmConversationEntity::getUserB, Math.max(userId, targetUserId)));
    }

    /** 校验当前用户是会话参与方（单聊参与者 / 群成员），否则 409 */
    private DmConversationEntity requireConversation(Long userId, Long conversationId) {
        DmConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new IllegalArgumentException("会话不存在");
        if (isGroup(conv)) {
            long member = groupMemberMapper.selectCount(
                    new LambdaQueryWrapper<DmGroupMemberEntity>()
                            .eq(DmGroupMemberEntity::getConversationId, conversationId)
                            .eq(DmGroupMemberEntity::getUserId, userId));
            if (member == 0) throw new IllegalStateException("无权访问该会话");
        } else if (!Objects.equals(conv.getUserA(), userId)
                && !Objects.equals(conv.getUserB(), userId)) {
            throw new IllegalStateException("无权访问该会话");
        }
        return conv;
    }

    /** 将接收方未读消息置为已读，并清零会话未读列；群聊只清空成员级未读 */
    private void markReadInternal(Long userId, DmConversationEntity conv) {
        if (isGroup(conv)) {
            DmGroupMemberEntity member = groupMember(conv.getId(), userId);
            int before = member == null || member.getUnread() == null ? 0 : member.getUnread();
            groupMemberMapper.clearUnread(conv.getId(), userId);
            groupMemberMapper.restoreVisible(conv.getId(), userId);
            if (before > 0) {
                eventPublisher.publishEvent(new DmConversationChangedEvent(
                        userId, conv.getId(), "read", null));
            }
            return;
        }
        // 先查出本次会被标记为已读的未读消息，用于给原发送方推送「已读」回执
        List<DmMessageEntity> unreadMessages = messageMapper.selectList(
                new LambdaQueryWrapper<DmMessageEntity>()
                        .select(DmMessageEntity::getId, DmMessageEntity::getSenderId)
                        .eq(DmMessageEntity::getConversationId, conv.getId())
                        .eq(DmMessageEntity::getReceiverId, userId)
                        .eq(DmMessageEntity::getReadFlag, 0));

        messageMapper.update(null,
                new LambdaUpdateWrapper<DmMessageEntity>()
                        .eq(DmMessageEntity::getConversationId, conv.getId())
                        .eq(DmMessageEntity::getReceiverId, userId)
                        .eq(DmMessageEntity::getReadFlag, 0)
                        .set(DmMessageEntity::getReadFlag, 1));

        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conv.getId());
        if (Objects.equals(conv.getUserA(), userId)) {
            uw.set(DmConversationEntity::getUnreadA, 0);
        } else {
            uw.set(DmConversationEntity::getUnreadB, 0);
        }
        conversationMapper.update(null, uw);

        // 有消息真正从未读变为已读时，才推送回执（避免空事件刷屏）
        if (!unreadMessages.isEmpty()) {
            Set<Long> senderIds = unreadMessages.stream()
                    .map(DmMessageEntity::getSenderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!senderIds.isEmpty()) {
                eventPublisher.publishEvent(new DmMessageReadEvent(conv.getId(), userId, senderIds));
            }
            // 多端同步：通知同账号其他在线端刷新未读数
            eventPublisher.publishEvent(new DmConversationChangedEvent(
                    userId, conv.getId(), "read", null));
        }
    }

    /** 恢复当前用户对会话的可见性：主动打开会话时清除删除（隐藏）标记 */
    private void restoreVisible(DmConversationEntity conv, Long userId) {
        if (isGroup(conv)) {
            groupMemberMapper.restoreVisible(conv.getId(), userId);
            return;
        }
        boolean isA = Objects.equals(conv.getUserA(), userId);
        int deleted = isA
                ? (conv.getDeletedA() == null ? 0 : conv.getDeletedA())
                : (conv.getDeletedB() == null ? 0 : conv.getDeletedB());
        if (deleted == 0) return;

        LambdaUpdateWrapper<DmConversationEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(DmConversationEntity::getId, conv.getId());
        if (isA) {
            uw.set(DmConversationEntity::getDeletedA, 0);
            conv.setDeletedA(0);
        } else {
            uw.set(DmConversationEntity::getDeletedB, 0);
            conv.setDeletedB(0);
        }
        conversationMapper.update(null, uw);
    }

    /** 敏感词校验：私信与社区共用同一套敏感词库/匹配规则（带 60s 本地缓存，减少全表查询） */
    private void rejectSensitiveText(String text) {
        if (new SensitiveWordFilter(enabledSensitiveWords()).contains(text)) {
            throw new IllegalArgumentException("内容包含敏感词，无法发布");
        }
    }

    /**
     * 启用的敏感词列表（本地缓存 60s）。
     * 管理端修改敏感词后最多延迟 1 分钟生效，换取私信高频发送时不再逐条查全表。
     */
    private synchronized List<SensitiveWordEntity> enabledSensitiveWords() {
        long now = System.currentTimeMillis();
        if (now - sensitiveWordCacheAt < SENSITIVE_WORD_CACHE_MS) {
            return sensitiveWordCache;
        }
        List<SensitiveWordEntity> fresh = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWordEntity>()
                        .eq(SensitiveWordEntity::getStatus, "ENABLED"));
        sensitiveWordCache = fresh == null ? Collections.emptyList() : fresh;
        sensitiveWordCacheAt = now;
        return sensitiveWordCache;
    }

    /** 发送频控：用户维度分钟级计数；Redis 异常时 fail-open，避免聊天整体不可用 */
    private void checkSendRate(Long userId) {
        String key = SEND_RATE_KEY_PREFIX + userId;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
        } catch (Exception e) {
            log.error("私信发送频控 fail-open（Redis 不可用）, userId={}", userId, e);
            return;
        }
        if (count != null && count > SEND_LIMIT_PER_MINUTE) {
            throw new IllegalStateException("发送过于频繁，请稍后再试");
        }
    }

    /** 当前用户在该会话中删除（隐藏）的消息ID列表 */
    private List<Long> hiddenMessageIds(Long userId, Long conversationId) {
        return messageDeleteMapper.selectList(
                new LambdaQueryWrapper<DmMessageDeleteEntity>()
                        .select(DmMessageDeleteEntity::getMessageId)
                        .eq(DmMessageDeleteEntity::getUserId, userId)
                        .eq(DmMessageDeleteEntity::getConversationId, conversationId))
                .stream()
                .map(DmMessageDeleteEntity::getMessageId)
                .toList();
    }

    /** 是否已拉黑（单向） */
    private boolean isBlocked(Long userId, Long targetId) {
        return blockMapper.selectCount(
                new LambdaQueryWrapper<BlockEntity>()
                        .eq(BlockEntity::getUserId, userId)
                        .eq(BlockEntity::getTargetId, targetId)) > 0;
    }

    /** 媒体 URL 校验（图片/语音/文件）：只允许本站上传路径或 http(s) 外链，拒绝 javascript:/data: 等协议 */
    private void validateMediaUrl(String url, String label) {
        if (url.isEmpty()) throw new IllegalArgumentException(label + "地址不能为空");
        if (url.length() > MAX_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException(label + "地址过长");
        }
        boolean allowedPrefix = url.startsWith("/uploads/")
                || url.startsWith("http://")
                || url.startsWith("https://");
        if (!allowedPrefix || url.contains(" ") || url.contains("\n") || url.contains("\r")) {
            throw new IllegalArgumentException(label + "地址无效");
        }
    }

    /** 会话预览：媒体消息统一用占位文案，文本消息按长度截断 */
    private String previewForType(String contentType, String content) {
        if (contentType == null) return previewOf(content);
        return switch (contentType.toUpperCase()) {
            case "IMAGE" -> IMAGE_PREVIEW;
            case "VOICE" -> VOICE_PREVIEW;
            case "FILE" -> FILE_PREVIEW;
            default -> previewOf(content);
        };
    }

    /** 会话预览截断，避免把整段长消息写进列表冗余字段 */
    private String previewOf(String content) {
        if (content == null) return "";
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "..." : content;
    }

    /** 会话 VO：id/对方或群信息/最后消息/未读数 */
    private Map<String, Object> conversationVo(DmConversationEntity conv, Long userId, UserEntity target) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", conv.getId());
        if (isGroup(conv)) {
            DmGroupMemberEntity member = groupMember(conv.getId(), userId);
            String name = conv.getName() == null ? "" : conv.getName();
            m.put("type", "GROUP");
            m.put("targetUserId", null);
            m.put("nickname", name);
            m.put("name", name);
            m.put("avatar", conv.getAvatar());
            m.put("ownerId", conv.getOwnerId());
            m.put("announcement", conv.getAnnouncement());
            m.put("unreadCount", member == null || member.getUnread() == null ? 0 : member.getUnread());
            m.put("pinned", member != null && member.getPinned() != null && member.getPinned() == 1);
        } else {
            Long targetId = Objects.equals(conv.getUserA(), userId) ? conv.getUserB() : conv.getUserA();
            m.put("type", "SINGLE");
            m.put("targetUserId", targetId);
            m.put("nickname", target == null ? "" : target.getNickname());
            m.put("avatar", target == null ? null : target.getAvatar());
            m.put("ownerId", null);
            m.put("unreadCount", Objects.equals(conv.getUserA(), userId)
                    ? (conv.getUnreadA() == null ? 0 : conv.getUnreadA())
                    : (conv.getUnreadB() == null ? 0 : conv.getUnreadB()));
            m.put("pinned", Objects.equals(conv.getUserA(), userId)
                    ? (conv.getPinnedA() != null && conv.getPinnedA() == 1)
                    : (conv.getPinnedB() != null && conv.getPinnedB() == 1));
        }
        m.put("lastMessage", conv.getLastMessageContent());
        m.put("lastMessageType", conv.getLastMessageType() == null ? "TEXT" : conv.getLastMessageType());
        m.put("lastMessageAt", conv.getLastMessageAt());
        m.put("createTime", conv.getCreateTime());
        return m;
    }

    /** 查询群成员记录（不存在返回 null） */
    private DmGroupMemberEntity groupMember(Long conversationId, Long userId) {
        return groupMemberMapper.selectOne(
                new LambdaQueryWrapper<DmGroupMemberEntity>()
                        .eq(DmGroupMemberEntity::getConversationId, conversationId)
                        .eq(DmGroupMemberEntity::getUserId, userId)
                        .last("LIMIT 1"));
    }

    /** 会话是否为群聊 */
    private boolean isGroup(DmConversationEntity conv) {
        return conv != null && "GROUP".equalsIgnoreCase(conv.getType());
    }

    private boolean isGroupType(Object type) {
        return type != null && "GROUP".equalsIgnoreCase(String.valueOf(type));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
