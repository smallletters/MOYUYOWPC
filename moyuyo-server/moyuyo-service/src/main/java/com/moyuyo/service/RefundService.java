package com.moyuyo.service;

import com.moyuyo.common.dto.admin.order.OrderRefundRequest;
import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;

import java.util.List;
import java.util.Map;

public interface RefundService {

    RefundVO applyRefund(Long userId, RefundApplyRequest request);

    void approveRefund(Long refundId, Long operatorId);

    void rejectRefund(Long refundId, Long operatorId, String reason);

    void completeRefund(Long refundId, Long operatorId, String transactionId);

    RefundVO getRefundDetail(Long refundId, Long userId);

    com.baomidou.mybatisplus.core.metadata.IPage<RefundVO> listUserRefunds(Long userId, int page, int size);

    com.baomidou.mybatisplus.core.metadata.IPage<RefundVO> listAllRefunds(int page, int size, String status);

    /**
     * 管理后台退款列表查询（支持 status + type 双维度过滤）。
     *
     * @param page  页码
     * @param size  每页大小
     * @param status 退款业务状态（PENDING/APPROVED/REJECTED/COMPLETED/REFUNDING），可选
     * @param type   退款类型（FULL/PARTIAL/REFUND_ONLY/REFUND_RETURN/EXCHANGE），可选
     * @return 退款分页结果
     */
    com.baomidou.mybatisplus.core.metadata.IPage<RefundVO> listAllRefunds(int page, int size, String status, String type);

    /**
     * 按 type 维度统计各业务状态（精确计数）。
     * <p>
     * 用于管理后台退款列表 chip 角标，避免前端基于当前页数据近似聚合导致的偏差。
     *
     * @param type 退款类型（FULL/PARTIAL/REFUND_ONLY/REFUND_RETURN/EXCHANGE），可选；null/空表示不过滤
     * @return Map&lt;String, Long&gt; key=PENDING/APPROVED/REJECTED/COMPLETED/REFUNDING，value=数量；缺省键为 0
     */
    java.util.Map<String, Long> countRefundsByStatus(String type);

    /**
     * 查询某订单的全部退款记录（管理后台订单详情用）。
     *
     * @param orderId 订单 ID
     * @return 退款记录列表，按创建时间倒序
     */
    List<RefundVO> listOrderRefunds(Long orderId);

    /**
     * 管理后台在订单详情页直接发起退款（对齐 WooCommerce 退款操作）。
     * <p>
     * 一次完成「创建退款单 → 审核通过 → 调用渠道退款/手动录入流水号」，
     * AUTO 模式下真实调用 Stripe / PayPal 等渠道退款 API，失败则整体回滚。
     * 部分退款完成后订单状态恢复为退款前状态；全额退款则置为 REFUNDED。
     *
     * @param orderId    订单 ID
     * @param request    退款参数
     * @param operatorId 操作管理员 ID
     * @return 退款结果（refundId / refundNo / amount / transactionId / status）
     */
    Map<String, Object> adminRefundOrder(Long orderId, OrderRefundRequest request, Long operatorId);
}
