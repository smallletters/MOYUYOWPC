package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.dto.aftersales.AfterSalesCreateRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;

import java.util.Map;

/**
 * 售后（退货退款/仅退款/换货）服务
 * <p>
 * 状态机：PENDING → APPROVED → RETURNING → RECEIVED → COMPLETED
 * 分支：PENDING → REJECTED；PENDING/APPROVED → CANCELLED
 */
public interface AfterSalesService {

  /** C 端：申请售后 */
  AfterSalesVO createAfterSales(Long userId, AfterSalesCreateRequest request);

  /** C 端：我的售后列表（orderId 非空时只看该订单，用于分次退货后查看完整历史） */
  IPage<AfterSalesVO> listAfterSales(Long userId, int page, int size, String status, Long orderId);

  /** C 端：售后详情 */
  AfterSalesVO getAfterSalesDetail(Long id, Long userId);

  /**
   * C 端：某订单最近一条售后记录
   * <p>
   * 订单详情页据此决定是否展示"售后记录"入口：无售后时返回 null，入口不展示。
   */
  AfterSalesVO getLatestAfterSales(Long orderId, Long userId);

  /**
   * 某订单各订单行已申请售后的数量（排除已拒绝/已取消），供 APP 计算分次退货的剩余可退数量。
   *
   * @return key = orderItemId，value = 已申请数量；无售后的订单行不在 map 中
   */
  java.util.Map<Long, Integer> getAppliedQuantityByOrder(Long orderId, Long userId);

  /**
   * 某订单各订单行已申请售后的退款金额合计（排除已拒绝/已取消），供 APP 计算剩余可退金额。
   *
   * @return key = orderItemId，value = 已申请金额
   */
  java.util.Map<Long, java.math.BigDecimal> getAppliedAmountByOrder(Long orderId, Long userId);

  /** C 端：填写退货物流（审核通过后寄回） */
  AfterSalesVO submitReturnLogistics(Long userId, Long id, String company, String trackingNo);

  /** C 端：取消售后（待审核/待寄回可取消） */
  AfterSalesVO cancelAfterSales(Long userId, Long id);

  /** 管理端：售后列表 */
  IPage<AfterSalesVO> listAllAfterSales(int page, int size, String status, String type);

  /** 管理端：售后详情 */
  AfterSalesVO getAfterSalesDetailForAdmin(Long id);

  /** 管理端：审核通过（决定是否需要寄回） */
  AfterSalesVO approveAfterSales(Long id, Long operatorId, String deliveryType, String remark);

  /** 管理端：拒绝售后 */
  AfterSalesVO rejectAfterSales(Long id, Long operatorId, String reason);

  /** 管理端：确认收到退货 */
  AfterSalesVO confirmReceived(Long id, Long operatorId);

  /**
   * 管理端：代用户补录/修正退货运单号（客服兜底）
   * <p>
   * 允许在 APPROVED（待寄回）与 RETURNING（已寄回）两种状态下操作；
   * 用户填错运单号时由客服更正，避免必须改库。
   */
  AfterSalesVO adminSubmitReturnLogistics(Long id, Long operatorId, String company, String trackingNo);

  /**
   * 超时自动关闭（定时任务调用）
   * <p>
   * - 待审核超过 pendingDays 天未处理 → 自动关闭，避免商家长期拖延；
   * - 待寄回超过 returnDays 天未寄回 → 自动关闭，释放"进行中"占用。
   *
   * @return 本次关闭的售后单数量
   */
  int closeTimeoutAfterSales(int pendingDays, int returnDays);

  /**
   * 管理端：完成售后
   * <p>
   * 退货退款/仅退款：调用退款服务原路退款，并回写退款单号；
   * 换货：仅流转为已完成，换货物流记录在 remark。
   *
   * @param mode          AUTO（自动原路退）/ MANUAL（人工录入三方流水号），空默认 AUTO
   * @param transactionId MANUAL 模式必填
   */
  Map<String, Object> completeAfterSales(Long id, Long operatorId, String mode, String transactionId);
}
