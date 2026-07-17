package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 管理后台订单运营服务
 */
public interface AdminOrderOpsService {

  /**
   * 导出列表（分页）
   */
  Page<Map<String, Object>> listExport(String status, int page, int size);

  /**
   * 订单运营统计
   */
  Map<String, Object> stats();

  /**
   * 批量发货
   */
  void batchShip(java.util.List<Long> ids, String carrier, String trackingNo);

  /**
   * 更新备注
   */
  void updateRemark(Long id, String remark);
}
