package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 管理后台满意度服务
 */
public interface AdminSatisfactionService {

  /**
   * 评价列表（分页、筛选）
   */
  Page<Map<String, Object>> listAll(Integer score, String category, int page, int size);

  /**
   * 满意度统计（平均分/分布统计）
   */
  Map<String, Object> stats();
}
