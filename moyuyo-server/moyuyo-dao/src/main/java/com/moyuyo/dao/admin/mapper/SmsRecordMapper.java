package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.SmsRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信发送记录 Mapper
 */
@Mapper
public interface SmsRecordMapper extends BaseMapper<SmsRecordEntity> {
}
