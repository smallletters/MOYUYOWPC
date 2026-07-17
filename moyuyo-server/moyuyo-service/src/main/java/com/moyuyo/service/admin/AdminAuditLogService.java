package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 管理后台审计日志服务
 */
public interface AdminAuditLogService {

  /**
   * 审计日志列表（支持筛选）
   */
  Page<Map<String, Object>> listAll(String action, String module, int page, int size);

  /**
   * 审计日志统计（按action分组）
   */
  List<Map<String, Object>> stats();
}
