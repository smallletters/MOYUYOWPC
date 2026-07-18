package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据导出请求 Mapper
 */
@Mapper
public interface DataExportRequestMapper extends BaseMapper<DataExportRequestEntity> {
}
