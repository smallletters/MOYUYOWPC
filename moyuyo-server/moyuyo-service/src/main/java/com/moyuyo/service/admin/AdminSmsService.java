package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 管理后台短信服务
 */
public interface AdminSmsService {

  /**
   * 发送记录列表（分页、筛选）
   */
  Page<Map<String, Object>> listAll(String phone, String status, int page, int size);

  /**
   * 短信统计（今日发送数/成功率）
   */
  Map<String, Object> stats();
}
