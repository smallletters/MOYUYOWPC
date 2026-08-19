package com.moyuyo.service;

import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;

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
}
