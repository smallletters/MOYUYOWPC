package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台限时抢购服务
 */
public interface AdminFlashSaleService {

  /**
   * 抢购活动列表（全部）
   */
  List<Map<String, Object>> listAll();

  /**
   * 抢购活动分页列表
   */
  Map<String, Object> listPage(int page, int size);

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

  /**
   * 秒杀活动统计数据
   */
  Map<String, Object> getStats();

  /**
   * 获取单个秒杀活动详情
   */
  Map<String, Object> getDetail(Long id);
}
