package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台积分服务
 */
public interface AdminPointsService {

  /**
   * 积分活动列表
   */
  List<Map<String, Object>> listActivities();

  /**
   * 创建积分活动
   */
  void createActivity(Map<String, Object> data);

  /**
   * 更新积分活动（按活动ID/type更新对应积分记录）
   */
  void updateActivity(String id, Map<String, Object> data);

  /**
   * 积分流水列表
   */
  Map<String, Object> listLogs(int page, int size, Long userId);

  /**
   * 积分兑换记录分页列表（关联用户昵称、邮箱与生成的 0 元积分订单）
   *
   * @param orderStatus 关联订单状态过滤（PENDING_SHIP / SHIPPED / COMPLETED / CANCELLED 等），为空不过滤。
   *                    兑换记录自身 status 不随履约变化，履约状态以订单为准。
   * @param userId      用户 ID 过滤，为空不过滤
   */
  Map<String, Object> listExchanges(int page, int size, String orderStatus, Long userId);

  /**
   * 删除积分活动（按活动ID/type删除对应的积分流水记录）
   */
  void deleteActivity(String type);

  /**
   * 积分统计
   */
  Map<String, Object> getStats();

  /**
   * 查询用户积分（余额 + 历史记录）
   */
  Map<String, Object> getUserPoints(Long userId);

  /**
   * 手动调整用户积分（正数为增加，负数为扣减）
   */
  void adjustPoints(Long userId, int amount, String reason);
}
