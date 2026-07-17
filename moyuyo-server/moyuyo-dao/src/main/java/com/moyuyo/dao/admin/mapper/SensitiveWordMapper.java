package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordEntity> {
}
