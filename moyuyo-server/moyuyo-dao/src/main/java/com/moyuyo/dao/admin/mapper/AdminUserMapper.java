package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员用户 Mapper
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {
}
