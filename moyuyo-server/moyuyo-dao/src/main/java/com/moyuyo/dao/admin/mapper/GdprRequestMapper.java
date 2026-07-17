package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.GdprRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * GDPR 用户请求 Mapper
 */
@Mapper
public interface GdprRequestMapper extends BaseMapper<GdprRequestEntity> {
}
