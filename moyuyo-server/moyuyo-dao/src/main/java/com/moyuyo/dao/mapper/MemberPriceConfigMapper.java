package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.MemberPriceConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员等级价格折扣配置 Mapper
 */
@Mapper
public interface MemberPriceConfigMapper extends BaseMapper<MemberPriceConfigEntity> {
}
