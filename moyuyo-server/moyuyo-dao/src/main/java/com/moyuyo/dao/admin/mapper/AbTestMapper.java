package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.AbTestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * A/B 测试 Mapper
 */
@Mapper
public interface AbTestMapper extends BaseMapper<AbTestEntity> {
}
