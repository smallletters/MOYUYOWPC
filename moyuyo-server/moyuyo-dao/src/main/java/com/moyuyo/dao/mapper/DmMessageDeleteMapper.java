package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.DmMessageDeleteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户私信消息删除记录 Mapper
 */
@Mapper
public interface DmMessageDeleteMapper extends BaseMapper<DmMessageDeleteEntity> {
}
