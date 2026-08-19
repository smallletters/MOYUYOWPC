package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.InventoryBatchEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存批次 Mapper（管理后台 / 库存管理使用）
 */
@Mapper
public interface InventoryBatchMapper extends BaseMapper<InventoryBatchEntity> {
}
