package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.DmGroupMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DmGroupMemberMapper extends BaseMapper<DmGroupMemberEntity> {

    /** 当前用户所有群的未读总数（已隐藏的群不计入） */
    @Select("SELECT COALESCE(SUM(unread), 0) FROM mo_dm_group_member "
            + "WHERE user_id = #{userId} AND deleted = 0")
    Long sumUnread(@Param("userId") Long userId);

    /** 群内除发送者外的成员未读 +1，并把被删除（隐藏）的群重新恢复 */
    @Update("UPDATE mo_dm_group_member SET unread = unread + 1, deleted = 0 "
            + "WHERE conversation_id = #{conversationId} AND user_id <> #{senderId}")
    int increaseUnreadForOthers(@Param("conversationId") Long conversationId,
                                @Param("senderId") Long senderId);

    /** 撤回群消息：除发送者外成员未读回退 1（下限 0） */
    @Update("UPDATE mo_dm_group_member SET unread = GREATEST(unread - 1, 0) "
            + "WHERE conversation_id = #{conversationId} AND user_id <> #{senderId}")
    int decreaseUnreadForOthers(@Param("conversationId") Long conversationId,
                                @Param("senderId") Long senderId);

    /** 清空当前用户在指定群的未读 */
    @Update("UPDATE mo_dm_group_member SET unread = 0 "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int clearUnread(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId);

    /** 恢复当前用户对群的可见性（收到新消息/主动打开时） */
    @Update("UPDATE mo_dm_group_member SET deleted = 0 "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int restoreVisible(@Param("conversationId") Long conversationId,
                       @Param("userId") Long userId);
}
