package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.exchange.ExchangeApplyRequest;
import com.moyuyo.common.dto.exchange.ExchangeVO;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.ExchangeEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.mapper.ExchangeMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 换货单状态机实现：
 * APPLIED → APPROVED → SHIPPED_BACK → RESHIPPED → COMPLETED
 * 任意阶段可 CANCELLED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeMapper exchangeMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public ExchangeVO applyExchange(Long userId, ExchangeApplyRequest request) {
        // 1) 校验订单归属
        OrderEntity order = orderMapper.selectById(request.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }

        // 2) 校验订单状态允许换货：已发货/已收货/已完成
        String status = order.getStatus();
        if (!OrderStatusEnum.SHIPPED.name().equals(status)
                && !OrderStatusEnum.RECEIVED.name().equals(status)
                && !OrderStatusEnum.COMPLETED.name().equals(status)) {
            throw new IllegalStateException("当前订单状态不允许申请换货");
        }

        // 3) 校验 SKU 属于本订单
        OrderItemEntity oldItem = findOrderItem(request.getOrderId(), request.getOldSkuId());
        if (oldItem == null) {
            throw new IllegalArgumentException("原 SKU 不属于该订单");
        }
        if (oldItem.getQuantity() < request.getOldQuantity()) {
            throw new IllegalArgumentException("换货数量超过购买数量");
        }

        // 4) 同一订单不能同时存在进行中的换货单
        long inProgress = exchangeMapper.selectCount(
                new LambdaQueryWrapper<ExchangeEntity>()
                        .eq(ExchangeEntity::getOrderId, request.getOrderId())
                        .in(ExchangeEntity::getStatus, "APPLIED", "APPROVED", "SHIPPED_BACK", "RESHIPPED"));
        if (inProgress > 0) {
            throw new IllegalStateException("该订单已有进行中的换货申请");
        }

        // 5) 生成换货单号
        String exchangeNo = "EXC" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        // 6) 落库
        ExchangeEntity entity = new ExchangeEntity();
        entity.setOrderId(request.getOrderId());
        entity.setExchangeNo(exchangeNo);
        entity.setOldSkuId(request.getOldSkuId());
        entity.setOldQuantity(request.getOldQuantity());
        entity.setNewSkuId(request.getNewSkuId());
        entity.setNewQuantity(request.getNewQuantity());
        entity.setReason(request.getReason());
        entity.setDescription(request.getDescription());
        entity.setImages(request.getImages());
        entity.setStatus("APPLIED");
        exchangeMapper.insert(entity);

        // 7) 订单状态机进入 EXCHANGING
        order.setStatus(OrderStatusEnum.EXCHANGING.name());
        orderMapper.updateById(order);

        log.info("Exchange applied: exchangeNo={}, orderId={}, userId={}",
                exchangeNo, request.getOrderId(), userId);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void approveExchange(Long exchangeId, Long operatorId) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if (!"APPLIED".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许审核通过");
        }
        entity.setStatus("APPROVED");
        entity.setApproveTime(LocalDateTime.now());
        exchangeMapper.updateById(entity);
        log.info("Exchange approved: exchangeId={}, operatorId={}", exchangeId, operatorId);
    }

    @Override
    @Transactional
    public void rejectExchange(Long exchangeId, Long operatorId, String reason) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if (!"APPLIED".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许拒绝");
        }
        entity.setStatus("CANCELLED");
        entity.setDescription((reason == null ? "" : reason) + (entity.getDescription() == null ? "" : "\n" + entity.getDescription()));
        entity.setCancelTime(LocalDateTime.now());
        exchangeMapper.updateById(entity);

        // 订单恢复原状态（取最后非换货的状态简化处理：恢复为 RECEIVED 或 COMPLETED）
        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null && OrderStatusEnum.EXCHANGING.name().equals(order.getStatus())) {
            // 默认恢复为已收货
            order.setStatus(OrderStatusEnum.RECEIVED.name());
            orderMapper.updateById(order);
        }
        log.info("Exchange rejected: exchangeId={}, operatorId={}, reason={}", exchangeId, operatorId, reason);
    }

    @Override
    @Transactional
    public void fillReturnShipping(Long exchangeId, String carrier, String trackingNo) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许录入回寄物流");
        }
        if (carrier == null || trackingNo == null) {
            throw new IllegalArgumentException("承运商与物流单号不能为空");
        }
        entity.setStatus("SHIPPED_BACK");
        entity.setCarrier(carrier);
        entity.setTrackingNo(trackingNo);
        exchangeMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void reship(Long exchangeId, String carrier, String trackingNo, Long operatorId) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if (!"SHIPPED_BACK".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许重新发货");
        }
        if (carrier == null || trackingNo == null) {
            throw new IllegalArgumentException("承运商与物流单号不能为空");
        }
        entity.setStatus("RESHIPPED");
        entity.setReshipCarrier(carrier);
        entity.setReshipTracking(trackingNo);
        exchangeMapper.updateById(entity);
        log.info("Exchange reshipped: exchangeId={}, operatorId={}, tracking={}", exchangeId, operatorId, trackingNo);
    }

    @Override
    @Transactional
    public void completeExchange(Long exchangeId, Long operatorId) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if (!"RESHIPPED".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许完成");
        }
        entity.setStatus("COMPLETED");
        entity.setCompleteTime(LocalDateTime.now());
        exchangeMapper.updateById(entity);

        // 订单进入 EXCHANGED
        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatusEnum.EXCHANGED.name());
            orderMapper.updateById(order);
        }
        log.info("Exchange completed: exchangeId={}, operatorId={}", exchangeId, operatorId);
    }

    @Override
    @Transactional
    public void cancelExchange(Long exchangeId, Long operatorId, String reason) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        if ("COMPLETED".equals(entity.getStatus()) || "CANCELLED".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不允许取消");
        }
        entity.setStatus("CANCELLED");
        entity.setCancelTime(LocalDateTime.now());
        if (reason != null && !reason.isEmpty()) {
            entity.setDescription(reason + (entity.getDescription() == null ? "" : "\n" + entity.getDescription()));
        }
        exchangeMapper.updateById(entity);

        // 订单恢复
        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null && OrderStatusEnum.EXCHANGING.name().equals(order.getStatus())) {
            order.setStatus(OrderStatusEnum.RECEIVED.name());
            orderMapper.updateById(order);
        }
        log.info("Exchange cancelled: exchangeId={}, operatorId={}, reason={}", exchangeId, operatorId, reason);
    }

    @Override
    public ExchangeVO getExchangeDetail(Long exchangeId) {
        ExchangeEntity entity = exchangeMapper.selectById(exchangeId);
        if (entity == null) throw new IllegalArgumentException("换货单不存在");
        return toVO(entity);
    }

    @Override
    public IPage<ExchangeVO> listAll(int page, int size, String status) {
        LambdaQueryWrapper<ExchangeEntity> wrapper = new LambdaQueryWrapper<ExchangeEntity>()
                .orderByDesc(ExchangeEntity::getApplyTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ExchangeEntity::getStatus, status.toUpperCase());
        }
        IPage<ExchangeEntity> entityPage = exchangeMapper.selectPage(new Page<>(page, size), wrapper);
        return PageUtils.convertPage(entityPage, this::toVO);
    }

    @Override
    public IPage<ExchangeVO> listUserExchanges(Long userId, int page, int size) {
        // mo_exchange 表无 user_id 列，通过 order.user_id 间接过滤
        List<Long> userOrderIds = orderMapper.selectList(
                new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getUserId, userId))
                .stream().map(OrderEntity::getId).toList();
        if (userOrderIds.isEmpty()) {
            IPage<ExchangeEntity> empty = new Page<>(page, size);
            empty.setRecords(java.util.Collections.emptyList());
            return PageUtils.convertPage(empty, this::toVO);
        }
        IPage<ExchangeEntity> entityPage = exchangeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExchangeEntity>()
                        .in(ExchangeEntity::getOrderId, userOrderIds)
                        .orderByDesc(ExchangeEntity::getApplyTime));
        return PageUtils.convertPage(entityPage, this::toVO);
    }

    // ============= 私有方法 =============

    private OrderItemEntity findOrderItem(Long orderId, Long skuId) {
        List<OrderItemEntity> items = orderItemMapper.selectByOrderId(orderId);
        if (items == null) return null;
        for (OrderItemEntity item : items) {
            if (item.getSkuId() != null && item.getSkuId().equals(skuId)) {
                return item;
            }
        }
        return null;
    }

    private ExchangeVO toVO(ExchangeEntity entity) {
        return new ExchangeVO(
                entity.getId(), entity.getOrderId(), entity.getExchangeNo(),
                entity.getOldSkuId(), entity.getOldQuantity(),
                entity.getNewSkuId(), entity.getNewQuantity(),
                entity.getReason(), entity.getDescription(), entity.getImages(),
                entity.getStatus(), entity.getCarrier(), entity.getTrackingNo(),
                entity.getReshipCarrier(), entity.getReshipTracking(),
                entity.getApplyTime(), entity.getApproveTime(),
                entity.getCompleteTime(), entity.getCancelTime());
    }
}
