package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.DmMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DmMessageMapper extends BaseMapper<DmMessageEntity> {

    /**
     * 私信图片 URL 列表：管理端图片空间需要排除用户聊天图片，
     * 避免聊天附件混入商品素材库。
     */
    @Select("SELECT DISTINCT content FROM mo_dm_message "
            + "WHERE content_type = 'IMAGE' AND content IS NOT NULL "
            + "AND content LIKE '/uploads/%'")
    List<String> selectImageContents();

    /**
     * 判断指定发送方是否在该会话发过消息。
     * 用于陌生人防骚扰的快速判定：对方已回复则不再限制。
     */
    @Select("SELECT id FROM mo_dm_message "
            + "WHERE conversation_id = #{conversationId} AND sender_id = #{senderId} LIMIT 1")
    Long selectAnyId(@Param("conversationId") Long conversationId,
                     @Param("senderId") Long senderId);

    /**
     * 取指定发送方在会话中的前 limit 条消息 ID。
     * 只取上限条数即可判断是否超限，避免大会话全量 COUNT。
     */
    @Select("SELECT id FROM mo_dm_message "
            + "WHERE conversation_id = #{conversationId} AND sender_id = #{senderId} "
            + "LIMIT #{limit}")
    List<Long> selectIds(@Param("conversationId") Long conversationId,
                         @Param("senderId") Long senderId,
                         @Param("limit") int limit);
}
