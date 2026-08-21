package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客服会话消息 Mapper
 */
@Mapper
public interface CsMessageMapper extends BaseMapper<CsMessageEntity> {
}
