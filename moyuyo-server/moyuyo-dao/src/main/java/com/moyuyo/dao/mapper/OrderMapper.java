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

  /**
   * 漏斗分析：统计已下单用户数与已支付用户数（最近 days 天）
   * 返回 map：{orderUsers, paidUsers}，key 不存在视为 0
   */
  Map<String, Object> selectFunnelCounts(@Param("since") java.time.LocalDateTime since);

  /**
   * 流失分析：按 cancel_reason 分组统计指定时间范围内的取消订单数
   * 仅统计非空、非"用户主动取消"等运营盲区原因，包含金额总和便于排序
   */
  List<Map<String, Object>> selectChurnReasons(@Param("since") java.time.LocalDateTime since);

  /**
   * 复购率分析：在指定时间窗口内下过单的用户中，复购用户（≥2 单）的占比
   * 返回 map：{payUsers, repurchaseUsers}
   */
  Map<String, Object> selectRepurchaseCounts(@Param("since") java.time.LocalDateTime since);
}
