package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.OrderPriceModifyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单改价记录 Mapper
 */
@Mapper
public interface OrderPriceModifyMapper extends BaseMapper<OrderPriceModifyEntity> {
}
