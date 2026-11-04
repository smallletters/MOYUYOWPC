package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.DmMessageEntity;
import org.apache.ibatis.annotations.Mapper;
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
}
