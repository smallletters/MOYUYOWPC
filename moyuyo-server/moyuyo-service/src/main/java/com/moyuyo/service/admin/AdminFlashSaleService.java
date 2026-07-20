package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台限时抢购服务
 */
public interface AdminFlashSaleService {

  /**
   * 抢购活动列表
   */
  List<Map<String, Object>> listAll();

  /**
   * 创建抢购活动
   */
  void create(Map<String, Object> data);

  /**
   * 更新抢购活动
   */
  void update(Map<String, Object> data);

  /**
   * 删除抢购活动
   */
  void delete(Long id);

  /**
   * 更新活动状态
   */
  void updateStatus(Long id, String status);
}
