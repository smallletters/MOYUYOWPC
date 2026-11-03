package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.MinorVerificationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * GDPR 未成年人年龄验证记录 Mapper
 */
@Mapper
public interface MinorVerificationMapper extends BaseMapper<MinorVerificationEntity> {
}
