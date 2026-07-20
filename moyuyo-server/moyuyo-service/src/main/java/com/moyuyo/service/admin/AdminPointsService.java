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
   * 积分流水列表
   */
  Map<String, Object> listLogs(int page, int size, Long userId);

  /**
   * 积分统计
   */
  Map<String, Object> getStats();
}
