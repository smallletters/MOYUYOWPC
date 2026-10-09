package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.order.OrderExportCreateRequest;
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
  Map<String, Object> createExportTask(OrderExportCreateRequest body);

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

  /**
   * 记录一次订单打印。
   * <p>
   * 按 (orderId, printType, templateName, paperSize) 4 字段维度累加 print_count：
   * 已存在则 print_count = print_count + 1；不存在则插入 print_count = 1。
   * 由数据库唯一索引 uk_order_print_dim + INSERT ... ON DUPLICATE KEY UPDATE 保证。
   * <p>
   * 依赖 Flyway 迁移：V20260929_04__print_log_unique_index.sql。
   */
  void recordPrint(Long orderId, String printType, String templateName, String paperSize, String operator);

  // ==================== 打印模板 CRUD ====================

  /** 查询所有打印模板（按 sortOrder 升序） */
  List<Map<String, Object>> listPrintTemplates();

  /** 更新打印模板（按 ID 局部更新，name/paperSize/description/contentTemplate/isDefault/sortOrder） */
  void updatePrintTemplate(Long id, String name, String paperSize, String description, String contentTemplate,
                           Boolean isDefault, Integer sortOrder);

  /** 设置默认模板（同一时刻仅一个模板为默认） */
  void setDefaultPrintTemplate(Long id);

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
   * <b>该方法仅用于"预览 / 取号"，不再自动累加打印日志</b>（之前的设计缺陷：用户预览一次就会 +1，
   * 实际打印次数被虚高）。调用方在确认打印后，应单独调用 {@link #recordShippingLabelLog} 累加日志。
   *
   * @param orderId   订单 id
   * @param carrierId 承运商 id（mo_carrier.id）
   * @return 燕文面单响应
   */
  YanWenLabelResponse fetchShippingLabel(Long orderId, Long carrierId);

  /**
   * 单独累加一次燕文面单打印日志（与 recordPrint 共用唯一索引 uk_order_print_dim，
   * 但 printType='SHIPPING_LABEL' / templateName='燕文电子面单' / paperSize='thermal-100'）。
   * <p>
   * 由前端在用户实际点击"打印"按钮后调用，避免"预览"动作被误记为一次打印。
   */
  void recordShippingLabelLog(Long orderId);

  /** 燕文 SDK 是否启用（用于前端判断按钮可用性） */
  boolean isYanwenEnabled();

  /**
   * P0（路径B）：仅创建燕文运单（不取面单）。
   * <p>
   * 用于：① 提前建运单，运营手工核对后再打单；② 调试 createOrder 请求/响应；
   *      ③ 想拿 waybillNumber 但暂不打面单的场景。
   * <p>
   * 写回 mo_order.tracking_number + shipping_carrier（事务内）。
   *
   * @param orderId   订单 id
   * @param carrierId 承运商 id（可空，会按 order.shippingCarrier 反查）
   * @return 燕文返回的运单号
   */
  String createYanwenWaybill(Long orderId, Long carrierId);

  // ==================== 打印设置（服务端持久化） ====================

  /**
   * 获取订单打印设置（纸张/份数/方向/边距等），从 mo_system_config 读取。
   * 默认值与前端一致。
   */
  Map<String, Object> getPrintSettings();

  /**
   * 保存订单打印设置到 mo_system_config（key='order_print_settings'）。
   * body 由前端完整传过来，键值直接覆盖。
   */
  void savePrintSettings(Map<String, Object> body);

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

  // ==================== 订单时间轴 ====================

  /**
   * 聚合订单的全生命周期事件流（按事件时间升序）。
   * <p>
   * 数据来源：
   *   - mo_order：创建、支付（pay_time）、发货（deliver_time）、收货（received_time）、状态变更等
   *   - mo_logistics：物流节点（shipped_at / received_at）
   *   - mo_order_intercept：拦截（create_time / release_time）
   *   - mo_order_price_modify：改价（create_time）
   *   - mo_order_print_log：打印（create_time）
   * <p>
   * 每个事件为 { time, type, title, detail }，前端按 time 排序渲染。
   *
   * @param orderId 订单 id
   * @param limit   单次返回上限（默认 200，由 moyuyo.order-ops.timeline-limit 配置）
   * @param offset  跳过的事件数（用于分页，默认 0，最大 1,000,000）
   * @return 时间轴分页结果 Map，含 events / total / limit / offset 四键：
   *   - events: List<Map>，按时间升序（可能为空）
   *   - total:  int 订单的总事件数（包含被分页跳过的）
   *   - limit:  int 实际生效的 limit
   *   - offset: int 实际生效的 offset
   *   订单不存在时 total=0、events=空数组，由 controller 判 404。
   */
  java.util.Map<String, Object> getOrderTimeline(Long orderId, Integer limit, Integer offset);

  /** 异常订单列表 */
  Page<Map<String, Object>> listAbnormalOrders(String abnormalType, int page, int size);
}
