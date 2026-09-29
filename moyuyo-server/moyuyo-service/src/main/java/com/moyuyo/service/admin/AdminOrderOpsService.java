package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;

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

  /**
   * 拉取单个订单的打印详情（含收货人/商品/承运商），用于打印预览。
   * <p>
   * 与 listPrint 不同：本方法返回的是"单条详情 + 商品明细 + 承运商名称"，
   * 适配 OrderPrint.vue 的"单笔打印快递单"流程。
   *
   * @param orderId 订单 id
   * @return 单条订单的打印详情 Map；订单不存在时返回 null
   */
  Map<String, Object> getPrintDetail(Long orderId);

  /**
   * 取订单的快递面单（按 carrier.code 路由到对应 SDK，目前支持 yanwen）。
   * <p>
   * 内部会调用对应 SDK 取 base64 PDF/PNG，并自动记录一次打印日志（type=SHIPPING_LABEL）。
   *
   * @param orderId   订单 id
   * @param carrierId 承运商 id（mo_carrier.id）
   * @return 燕文面单响应
   */
  YanWenLabelResponse fetchShippingLabel(Long orderId, Long carrierId);

  /** 燕文 SDK 是否启用（用于前端判断按钮可用性） */
  boolean isYanwenEnabled();

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
