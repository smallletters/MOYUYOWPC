package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FeedbackEntity;

import java.util.Map;

/**
 * 管理后台 - 投诉管理服务接口
 */
public interface AdminComplaintService {

  /**
   * 分页查询投诉列表
   */
  Page<FeedbackEntity> listAll(String status, String type, int page, int size);

  /**
   * 投诉统计
   */
  Map<String, Object> stats();

  /**
   * 处理投诉
   */
  void handle(Long id, String result, String note);
}
