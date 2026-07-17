package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.PushRecordEntity;

import java.util.List;
import java.util.Map;

/**
 * 推送管理服务
 */
public interface PushManageService {

  /**
   * 推送记录列表
   */
  List<PushRecordEntity> listRecords(String status, String type);

  /**
   * 创建推送记录
   */
  void create(PushRecordEntity entity);

  /**
   * 更新推送记录
   */
  void update(PushRecordEntity entity);

  /**
   * 发送推送
   */
  void send(Long id);

  /**
   * 取消推送
   */
  void cancel(Long id);

  /**
   * 删除推送记录
   */
  void delete(Long id);

  /**
   * 获取推送统计
   */
  Map<String, Object> getStats();
}
