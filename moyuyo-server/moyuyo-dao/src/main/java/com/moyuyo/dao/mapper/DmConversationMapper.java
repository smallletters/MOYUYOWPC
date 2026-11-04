package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.DmConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DmConversationMapper extends BaseMapper<DmConversationEntity> {

    /**
     * 汇总当前用户的单聊未读数。
     * 群聊未读由 DmGroupMemberMapper#sumUnread 单独统计。
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN user_a = #{userId} THEN unread_a ELSE unread_b END), 0) "
            + "FROM mo_dm_conversation WHERE type = 'SINGLE' "
            + "AND (user_a = #{userId} OR user_b = #{userId})")
    Long sumUnread(@Param("userId") Long userId);

    /**
     * 当前用户的会话列表（单聊 + 已加入的群聊），置顶优先 + 最后消息时间倒序。
     * 单聊未读/置顶取 unread_a/b、pinned_a/b；群聊取成员表 unread/pinned。
     */
    @Select("""
            SELECT c.id AS id,
                   c.type AS type,
                   c.name AS name,
                   c.owner_id AS ownerId,
                   c.announcement AS announcement,
                   c.avatar AS avatar,
                   c.user_a AS userA,
                   c.user_b AS userB,
                   c.last_message_id AS lastMessageId,
                   c.last_message_type AS lastMessageType,
                   c.last_message_content AS lastMessageContent,
                   c.last_message_at AS lastMessageAt,
                   c.create_time AS createTime,
                   CASE WHEN c.type = 'GROUP' THEN m.pinned
                        WHEN c.user_a = #{userId} THEN c.pinned_a ELSE c.pinned_b END AS pinned,
                   CASE WHEN c.type = 'GROUP' THEN m.unread
                        WHEN c.user_a = #{userId} THEN c.unread_a ELSE c.unread_b END AS unreadCount
            FROM mo_dm_conversation c
            LEFT JOIN mo_dm_group_member m
                   ON c.type = 'GROUP' AND m.conversation_id = c.id AND m.user_id = #{userId}
            WHERE (c.type = 'GROUP' AND m.id IS NOT NULL AND m.deleted = 0)
               OR (c.type = 'SINGLE'
                   AND ((c.user_a = #{userId} AND c.deleted_a = 0)
                        OR (c.user_b = #{userId} AND c.deleted_b = 0)))
            ORDER BY pinned DESC, COALESCE(c.last_message_at, c.create_time) DESC, c.create_time DESC
            LIMIT #{offset}, #{size}
            """)
    List<Map<String, Object>> selectUserConversations(@Param("userId") Long userId,
                                                      @Param("offset") int offset,
                                                      @Param("size") int size);

    /** 当前用户可见会话总数（与 selectUserConversations 的 WHERE 保持一致） */
    @Select("""
            SELECT COUNT(*)
            FROM mo_dm_conversation c
            LEFT JOIN mo_dm_group_member m
                   ON c.type = 'GROUP' AND m.conversation_id = c.id AND m.user_id = #{userId}
            WHERE (c.type = 'GROUP' AND m.id IS NOT NULL AND m.deleted = 0)
               OR (c.type = 'SINGLE'
                   AND ((c.user_a = #{userId} AND c.deleted_a = 0)
                        OR (c.user_b = #{userId} AND c.deleted_b = 0)))
            """)
    long countUserConversations(@Param("userId") Long userId);

    /**
     * 群头像 URL 列表：管理端图片空间需要排除聊天相关图片，
     * 避免群头像混入商品素材库（旧数据可能落在根目录）。
     */
    @Select("SELECT avatar FROM mo_dm_conversation "
            + "WHERE type = 'GROUP' AND avatar IS NOT NULL AND avatar LIKE '/uploads/%'")
    List<String> selectGroupAvatars();
}
