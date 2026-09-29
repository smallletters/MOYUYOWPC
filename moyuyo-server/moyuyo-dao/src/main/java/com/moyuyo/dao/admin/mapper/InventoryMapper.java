package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.InventoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仓库库存关系 Mapper（mo_inventory）
 */
@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {
}