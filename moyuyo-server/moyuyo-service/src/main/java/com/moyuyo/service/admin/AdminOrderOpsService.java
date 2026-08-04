package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.List;
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
   * 创建导出任务
   */
  Map<String, Object> createExportTask(Map<String, Object> body);

  /**
   * 构建导出文件内容（CSV 字节）
   */
  byte[] buildExportFile(String exportId);

  /**
   * 批量发货
   */
  void batchShip(List<Long> ids, String carrier, String trackingNo);

  /**
   * 更新备注
   */
  void updateRemark(Long id, String remark);

  // ==================== 订单打印 ====================

  /** 订单打印列表 */
  Page<Map<String, Object>> listPrint(String printType, int page, int size);

  /** 创建打印记录 */
  void recordPrint(Long orderId, String printType, String templateName, String paperSize, String operator);

  // ==================== 订单改价 ====================

  /** 改价记录列表 */
  Page<Map<String, Object>> listPriceModify(String keyword, Long orderId, int page, int size);

  /** 创建改价记录 */
  void createPriceModify(Long orderId, String orderNo, BigDecimal originalAmount, BigDecimal adjustAmount,
                         String reason, String reasonType, String operator);

  // ==================== 订单拦截 ====================

  /** 拦截记录列表 */
  Page<Map<String, Object>> listIntercept(String status, int page, int size);

  /** 创建拦截 */
  void createIntercept(Long orderId, String interceptType, String reason, String reasonTemplate, String operator);

  /** 解除拦截 */
  void releaseIntercept(Long interceptId, String releaseReason, String releaseOperator);

  // ==================== 订单监控 ====================

  /** 异常订单监控数据 */
  Map<String, Object> getMonitorData();

  /** 异常订单列表 */
  Page<Map<String, Object>> listAbnormalOrders(String abnormalType, int page, int size);
}
