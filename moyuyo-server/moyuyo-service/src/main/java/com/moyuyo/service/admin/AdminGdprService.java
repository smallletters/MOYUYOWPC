package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 管理后台 GDPR 合规服务
 */
public interface AdminGdprService {

  /**
   * 用户同意记录列表（分页）
   */
  Page<Map<String, Object>> listConsents(Long userId, int page, int size);

  /**
   * 数据主体请求列表（分页）
   */
  Page<Map<String, Object>> listRequests(String status, int page, int size);

  /**
   * 处理数据请求
   */
  void processRequest(Long id, String result, String note);
}
