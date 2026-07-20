package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.BlacklistEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 黑名单 Mapper
 */
@Mapper
public interface BlacklistMapper extends BaseMapper<BlacklistEntity> {
}
