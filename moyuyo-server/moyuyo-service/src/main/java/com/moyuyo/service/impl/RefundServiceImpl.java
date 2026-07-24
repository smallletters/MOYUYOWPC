package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public RefundVO applyRefund(Long userId, RefundApplyRequest request) {
        OrderEntity order = orderMapper.selectById(request.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!OrderStatusEnum.PAID.name().equals(order.getStatus()) && !OrderStatusEnum.RECEIVED.name().equals(order.getStatus())) {
            throw new IllegalStateException("当前订单状态不允许申请退款");
        }

        long pendingCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundEntity>()
                        .eq(RefundEntity::getOrderId, request.getOrderId())
                        .in(RefundEntity::getStatus, "PENDING", "APPROVED"));
        if (pendingCount > 0) {
            throw new IllegalStateException("该订单已有进行中的退款申请");
        }

        BigDecimal refundAmount = request.getAmount();
        if ("FULL".equals(request.getType())) {
            refundAmount = order.getPayAmount();
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0
                || refundAmount.compareTo(order.getPayAmount()) > 0) {
            throw new IllegalArgumentException("退款金额不合法");
        }

        String refundNo = "RFN" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        RefundEntity entity = new RefundEntity();
        entity.setOrderId(request.getOrderId());
        entity.setRefundNo(refundNo);
        entity.setType(request.getType());
        entity.setAmount(refundAmount);
        entity.setReason(request.getReason());
        entity.setDescription(request.getDescription());
        entity.setImages(request.getImages());
        entity.setStatus("PENDING");
        refundMapper.insert(entity);

        log.info("Refund applied: refundNo={}, orderId={}, userId={}", refundNo, request.getOrderId(), userId);
        return toRefundVO(entity);
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId, Long operatorId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        if (!"PENDING".equals(entity.getStatus())) throw new IllegalStateException("当前状态不允许审核");

        entity.setStatus("APPROVED");
        refundMapper.updateById(entity);

        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatusEnum.REFUNDING.name());
            orderMapper.updateById(order);
        }
        log.info("Refund approved: refundId={}, operatorId={}", refundId, operatorId);
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundId, Long operatorId, String reason) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        if (!"PENDING".equals(entity.getStatus())) throw new IllegalStateException("当前状态不允许拒绝");

        entity.setStatus("REJECTED");
        refundMapper.updateById(entity);
        log.info("Refund rejected: refundId={}, operatorId={}, reason={}", refundId, operatorId, reason);
    }

    @Override
    @Transactional
    public void completeRefund(Long refundId, Long operatorId, String transactionId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        if (!"APPROVED".equals(entity.getStatus())) throw new IllegalStateException("当前状态不允许完成退款");

        entity.setStatus("COMPLETED");
        entity.setCompleteTime(LocalDateTime.now());
        refundMapper.updateById(entity);

        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatusEnum.REFUNDED.name());
            orderMapper.updateById(order);
        }
        log.info("Refund completed: refundId={}, operatorId={}, transactionId={}", refundId, operatorId, transactionId);
    }

    @Override
    public RefundVO getRefundDetail(Long refundId, Long userId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        return toRefundVO(entity);
    }

    @Override
    public IPage<RefundVO> listUserRefunds(Long userId, int page, int size) {
        IPage<RefundEntity> entityPage = refundMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RefundEntity>()
                        .eq(RefundEntity::getOrderId, userId)
                        .orderByDesc(RefundEntity::getCreateTime));
        return toRefundVOPage(entityPage);
    }

    @Override
    public IPage<RefundVO> listAllRefunds(int page, int size, String status) {
        LambdaQueryWrapper<RefundEntity> wrapper = new LambdaQueryWrapper<RefundEntity>()
                .orderByDesc(RefundEntity::getCreateTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RefundEntity::getStatus, status);
        }
        IPage<RefundEntity> entityPage = refundMapper.selectPage(new Page<>(page, size), wrapper);
        return toRefundVOPage(entityPage);
    }

    private RefundVO toRefundVO(RefundEntity entity) {
        return new RefundVO(
                entity.getId(), entity.getOrderId(), entity.getRefundNo(),
                entity.getType(), entity.getAmount(), entity.getReason(),
                entity.getDescription(), entity.getImages(), entity.getStatus(),
                entity.getCreateTime(), entity.getCompleteTime());
    }

    private IPage<RefundVO> toRefundVOPage(IPage<RefundEntity> entityPage) {
        return PageUtils.convertPage(entityPage, this::toRefundVO);
    }
}
