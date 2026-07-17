package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

  /**
   * 按用户分页查询订单，可按订单状态筛选
   */
  Page<OrderEntity> selectPageByUserId(IPage<OrderEntity> page,
                                       @Param("userId") Long userId,
                                       @Param("status") String status);

  /**
   * RFM 分析：按用户统计最近购买天数、购买次数、总消费金额
   */
  List<Map<String, Object>> selectRfmData();
}
