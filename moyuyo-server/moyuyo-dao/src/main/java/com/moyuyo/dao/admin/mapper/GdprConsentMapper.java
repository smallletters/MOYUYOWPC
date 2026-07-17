package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.GdprConsentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * GDPR 用户同意记录 Mapper
 */
@Mapper
public interface GdprConsentMapper extends BaseMapper<GdprConsentEntity> {
}
