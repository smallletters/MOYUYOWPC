package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.enums.RefundChannelEnum;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.RefundChannelService;
import com.moyuyo.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundChannelService refundChannelService;
    private final MemberService memberService;
    private final PointsLogMapper pointsLogMapper;
    /** 全局复用的 Jackson 实例 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public RefundVO applyRefund(Long userId, RefundApplyRequest request) {
        // 1) 订单归属与状态校验
        OrderEntity order = orderMapper.selectById(request.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!OrderStatusEnum.PAID.name().equals(order.getStatus())
                && !OrderStatusEnum.RECEIVED.name().equals(order.getStatus())
                && !OrderStatusEnum.SHIPPED.name().equals(order.getStatus())
                && !OrderStatusEnum.COMPLETED.name().equals(order.getStatus())) {
            throw new IllegalStateException("当前订单状态不允许申请退款");
        }

        // 2) 同一订单不能同时存在进行中的退款申请
        long pendingCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundEntity>()
                        .eq(RefundEntity::getOrderId, request.getOrderId())
                        .in(RefundEntity::getStatus, "PENDING", "APPROVED"));
        if (pendingCount > 0) {
            throw new IllegalStateException("该订单已有进行中的退款申请");
        }

        // 3) 拆单校验：若 type=PARTIAL 则 items 必填
        BigDecimal refundAmount = request.getAmount();
        String itemsJson = null;
        if ("PARTIAL".equalsIgnoreCase(request.getType())) {
            List<RefundApplyRequest.RefundItem> items = request.getItems();
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("拆单退款必须指定退款子项");
            }
            // 加载订单子项用于校验
            List<OrderItemEntity> orderItems = orderItemMapper.selectByOrderId(request.getOrderId());
            if (orderItems == null || orderItems.isEmpty()) {
                throw new IllegalStateException("订单无子项记录");
            }
            Map<Long, OrderItemEntity> itemMap = new HashMap<>();
            for (OrderItemEntity oi : orderItems) {
                itemMap.put(oi.getSkuId(), oi);
            }

            BigDecimal itemsTotal = BigDecimal.ZERO;
            int itemsQtyTotal = 0;
            for (RefundApplyRequest.RefundItem ri : items) {
                OrderItemEntity oi = itemMap.get(ri.getSkuId());
                if (oi == null) {
                    throw new IllegalArgumentException("退款子项 SKU 不属于该订单: " + ri.getSkuId());
                }
                if (ri.getQuantity() == null || ri.getQuantity() <= 0) {
                    throw new IllegalArgumentException("子项数量必须为正数: " + ri.getSkuId());
                }
                if (ri.getQuantity() > oi.getQuantity()) {
                    throw new IllegalArgumentException(
                            "SKU " + ri.getSkuId() + " 退款数量(" + ri.getQuantity()
                                    + ") 超过购买数量(" + oi.getQuantity() + ")");
                }
                if (ri.getAmount() == null || ri.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("子项退款金额必须为正数: " + ri.getSkuId());
                }
                // 单子项退款金额不能超过其 subtotal * (qty/qty)
                BigDecimal singleMax = oi.getSubtotal()
                        .multiply(BigDecimal.valueOf(ri.getQuantity()))
                        .divide(BigDecimal.valueOf(oi.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
                if (ri.getAmount().compareTo(singleMax) > 0) {
                    throw new IllegalArgumentException(
                            "SKU " + ri.getSkuId() + " 退款金额超过可退金额上限");
                }
                itemsTotal = itemsTotal.add(ri.getAmount());
                itemsQtyTotal += ri.getQuantity();
            }

            // 请求 amount 必须等于 items 合计
            if (refundAmount == null) {
                refundAmount = itemsTotal;
            } else if (refundAmount.compareTo(itemsTotal) != 0) {
                throw new IllegalArgumentException(
                        "请求退款金额(" + refundAmount + ") 与子项合计(" + itemsTotal + ") 不一致");
            }
            try {
                itemsJson = objectMapper.writeValueAsString(items);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("子项 JSON 序列化失败", e);
            }
        } else if ("FULL".equalsIgnoreCase(request.getType())) {
            if (refundAmount == null) {
                refundAmount = order.getPayAmount();
            }
        } else {
            throw new IllegalArgumentException("不支持的退款类型: " + request.getType());
        }

        // 4) 金额上限校验（总金额 ≤ 订单实付）
        if (refundAmount.compareTo(order.getPayAmount()) > 0) {
            throw new IllegalArgumentException("退款金额超过订单实付金额");
        }

        // 5) 生成退款单
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
        entity.setItems(itemsJson);
        entity.setStatus("PENDING");
        refundMapper.insert(entity);

        log.info("Refund applied: refundNo={}, orderId={}, userId={}, amount={}, items={}",
                refundNo, request.getOrderId(), userId, refundAmount, itemsJson != null);
        return toRefundVO(entity);
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId, Long operatorId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        if (!"PENDING".equals(entity.getStatus())) throw new IllegalStateException("当前状态不允许审核");

        // #9：审核前校验退款金额不超过订单实付金额，防止审批时已被篡改
        OrderEntity order = orderMapper.selectById(entity.getOrderId());
        if (order != null && entity.getAmount() != null
                && entity.getAmount().compareTo(order.getPayAmount()) > 0) {
            throw new IllegalStateException("退款金额超过订单实付金额，请联系管理员");
        }

        entity.setStatus("APPROVED");
        refundMapper.updateById(entity);

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
        // #10：拒绝原因持久化，便于申诉查询
        entity.setRejectReason(reason);
        entity.setRejectOperatorId(operatorId);
        entity.setRejectTime(LocalDateTime.now());
        refundMapper.updateById(entity);
        log.info("Refund rejected: refundId={}, operatorId={}, reason={}", refundId, operatorId, reason);
    }

    @Override
    @Transactional
    public void completeRefund(Long refundId, Long operatorId, String transactionId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        if (!"APPROVED".equals(entity.getStatus())) throw new IllegalStateException("当前状态不允许完成退款");

        OrderEntity order = orderMapper.selectById(entity.getOrderId());

        // #3：自动调用第三方支付渠道退款 API
        // 优先级：手动录入的 transactionId（财务已线下处理）> 自动调用三方
        String finalTransactionId = transactionId;
        if (finalTransactionId == null || finalTransactionId.trim().isEmpty() || "AUTO".equalsIgnoreCase(finalTransactionId)) {
            // 自动模式：根据订单支付渠道调用对应三方退款 API
            RefundChannelEnum channel = RefundChannelEnum.fromValue(order == null ? null : order.getPayChannel());
            String payTransactionId = order == null ? null : order.getPayTransactionId();
            String orderNo = order == null ? entity.getRefundNo() : order.getOrderNo();
            try {
                finalTransactionId = refundChannelService.refund(
                        channel, payTransactionId, orderNo, entity.getAmount(), entity.getRefundNo());
                log.info("Auto refund success: refundId={}, channel={}, transactionId={}",
                        refundId, channel, finalTransactionId);
            } catch (Exception e) {
                log.error("Auto refund failed: refundId={}, channel={}", refundId, channel, e);
                throw new IllegalStateException("第三方退款调用失败，请改用手动模式录入流水号: " + e.getMessage());
            }
        }

        entity.setStatus("COMPLETED");
        entity.setCompleteTime(LocalDateTime.now());
        // #1：记录完成人与第三方流水号，便于财务对账
        entity.setCompleteOperatorId(operatorId);
        entity.setTransactionId(finalTransactionId);
        refundMapper.updateById(entity);

        if (order != null) {
            order.setStatus(OrderStatusEnum.REFUNDED.name());
            orderMapper.updateById(order);

            // 退款联动积分：原路返还抵扣积分 + 按比例扣回 REWARD 奖励积分
            try {
                reversePointsOnRefund(order, entity.getAmount());
            } catch (Exception e) {
                // 积分联动失败不影响退款主流程，但需记录便于对账
                log.error("[refund] 积分联动失败 refundId={}, orderNo={}, reason={}",
                        refundId, order.getOrderNo(), e.getMessage(), e);
            }
        }
        log.info("Refund completed: refundId={}, operatorId={}, transactionId={}",
                refundId, operatorId, finalTransactionId);
    }

    @Override
    public RefundVO getRefundDetail(Long refundId, Long userId) {
        RefundEntity entity = refundMapper.selectById(refundId);
        if (entity == null) throw new IllegalArgumentException("退款申请不存在");
        return toRefundVO(entity);
    }

    @Override
    public IPage<RefundVO> listUserRefunds(Long userId, int page, int size) {
        // 阻塞项 #6 越权修复：原实现用 orderId 列当 userId 过滤，会返回其他用户的退款数据。
        // mo_refund 表无 user_id 列，必须通过关联 mo_order.user_id 过滤。
        // 1) 先查该用户全部订单 ID
        List<Long> userOrderIds = orderMapper.selectList(
                new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getUserId, userId))
                .stream().map(OrderEntity::getId).toList();
        if (userOrderIds.isEmpty()) {
            // 用户无订单时返回空分页，避免 IN () 语法错误
            IPage<RefundEntity> empty = new Page<>(page, size);
            empty.setRecords(java.util.Collections.emptyList());
            return toRefundVOPage(empty);
        }
        IPage<RefundEntity> entityPage = refundMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RefundEntity>()
                        .in(RefundEntity::getOrderId, userOrderIds)
                        .orderByDesc(RefundEntity::getCreateTime));
        return toRefundVOPage(entityPage);
    }

    @Override
    public IPage<RefundVO> listAllRefunds(int page, int size, String status) {
        // 向下兼容旧调用方：type=null，交给新重载处理
        return listAllRefunds(page, size, status, null);
    }

    @Override
    public IPage<RefundVO> listAllRefunds(int page, int size, String status, String type) {
        LambdaQueryWrapper<RefundEntity> wrapper = new LambdaQueryWrapper<RefundEntity>()
                .orderByDesc(RefundEntity::getCreateTime);
        // 业务状态过滤：仅允许已知枚举值，避免任意 SQL 片段注入
        if (status != null && !status.isEmpty()) {
            String normalizedStatus = status.trim().toUpperCase();
            if (isValidRefundStatus(normalizedStatus)) {
                wrapper.eq(RefundEntity::getStatus, normalizedStatus);
            }
            // 非法 status 静默忽略，避免空查询影响列表展示
        }
        // 退款类型过滤：仅允许已知枚举值（FULL/PARTIAL/REFUND_ONLY/REFUND_RETURN/EXCHANGE）
        if (type != null && !type.isEmpty()) {
            String normalizedType = type.trim().toUpperCase();
            if (isValidRefundType(normalizedType)) {
                wrapper.eq(RefundEntity::getType, normalizedType);
            }
        }
        IPage<RefundEntity> entityPage = refundMapper.selectPage(new Page<>(page, size), wrapper);
        return toRefundVOPage(entityPage);
    }

    /** 退款业务状态白名单 */
    private static final java.util.Set<String> REFUND_STATUS_WHITELIST = java.util.Set.of(
            "PENDING", "APPROVED", "REJECTED", "COMPLETED", "REFUNDING");

    /** 退款类型白名单 */
    private static final java.util.Set<String> REFUND_TYPE_WHITELIST = java.util.Set.of(
            "FULL", "PARTIAL", "REFUND_ONLY", "REFUND_RETURN", "EXCHANGE");

    private boolean isValidRefundStatus(String s) {
        return REFUND_STATUS_WHITELIST.contains(s);
    }

    private boolean isValidRefundType(String t) {
        return REFUND_TYPE_WHITELIST.contains(t);
    }

    @Override
    public Map<String, Long> countRefundsByStatus(String type) {
        // 初始化全状态为 0，保证前端 chip 不会因为缺键显示空白
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (String s : REFUND_STATUS_WHITELIST) {
            result.put(s, 0L);
        }
        // 构建 GROUP BY 查询；type 非法值视为不过滤（与 listAllRefunds 保持一致语义）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
                        .select("status", "COUNT(*) AS cnt")
                        .groupBy("status");
        if (type != null && !type.isEmpty()) {
            String normalizedType = type.trim().toUpperCase();
            if (isValidRefundType(normalizedType)) {
                wrapper.eq("type", normalizedType);
            }
        }
        List<Map<String, Object>> rows = refundMapper.selectMaps(wrapper);
        for (Map<String, Object> row : rows) {
            Object statusObj = row.get("status");
            Object cntObj = row.get("cnt");
            if (statusObj == null || cntObj == null) continue;
            String status = statusObj.toString();
            long cnt = ((Number) cntObj).longValue();
            // 仅写入已知状态键，避免数据库里的脏数据污染返回
            if (REFUND_STATUS_WHITELIST.contains(status)) {
                result.put(status, cnt);
            }
        }
        return result;
    }

    private RefundVO toRefundVO(RefundEntity entity) {
        return new RefundVO(
                entity.getId(), entity.getOrderId(), entity.getRefundNo(),
                entity.getType(), entity.getAmount(), entity.getReason(),
                entity.getDescription(), entity.getImages(), entity.getItems(), entity.getStatus(),
                entity.getCreateTime(), entity.getCompleteTime());
    }

    private IPage<RefundVO> toRefundVOPage(IPage<RefundEntity> entityPage) {
        return PageUtils.convertPage(entityPage, this::toRefundVO);
    }

    /**
     * 退款完成时联动积分：
     * 1. 已抵扣积分（points_used）按退款比例原路返还：USER 收到 +returnUsed
     * 2. 该订单已发放的 REWARD 奖励积分按比例扣回：USER 扣减 -clawback
     * <p>
     * 任一金额按四舍五入取整；扣回金额 clamp 到用户当前可用余额（防止负数）。
     */
    private void reversePointsOnRefund(OrderEntity order, BigDecimal refundAmount) {
        if (order == null || refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.signum() <= 0) {
            return;
        }
        // 退款比例：refundAmount / payAmount，最大 1.0（避免运营事故导致扣回超过发放）
        double ratio = Math.min(1.0,
                refundAmount.doubleValue() / payAmount.doubleValue());

        // 1) 原路返还抵扣积分
        int used = order.getPointsUsed() == null ? 0 : order.getPointsUsed();
        int returnUsed = (int) Math.round(used * ratio);
        if (returnUsed > 0) {
            memberService.addPoints(
                    order.getUserId(),
                    returnUsed,
                    "REFUND_RETURN",
                    order.getOrderNo(),
                    String.format("退款返还抵扣积分：订单 %s 退款比例 %.2f%%，返还 %d 积分",
                            order.getOrderNo(), ratio * 100, returnUsed));
            log.info("[refund] 原路返还抵扣积分: userId={}, orderNo={}, return={}",
                    order.getUserId(), order.getOrderNo(), returnUsed);
        }

        // 2) 按比例扣回 REWARD 奖励积分（查询该订单历史正向 REWARD 流水总额）
        List<PointsLogEntity> rewardLogs = pointsLogMapper.selectList(
                new LambdaQueryWrapper<PointsLogEntity>()
                        .eq(PointsLogEntity::getUserId, order.getUserId())
                        .eq(PointsLogEntity::getBizNo, order.getOrderNo())
                        .eq(PointsLogEntity::getType, "REWARD")
                        .gt(PointsLogEntity::getChangeValue, 0));
        int totalRewarded = rewardLogs.stream()
                .mapToInt(PointsLogEntity::getChangeValue).sum();
        int clawback = (int) Math.round(totalRewarded * ratio);
        if (clawback > 0) {
            memberService.addPoints(
                    order.getUserId(),
                    -clawback,
                    "REFUND_CLAWBACK",
                    order.getOrderNo(),
                    String.format("退款扣回奖励积分：订单 %s 退款比例 %.2f%%，扣回 %d 积分（原发放 %d）",
                            order.getOrderNo(), ratio * 100, clawback, totalRewarded));
            log.info("[refund] 按比例扣回 REWARD: userId={}, orderNo={}, clawback={}, totalRewarded={}",
                    order.getUserId(), order.getOrderNo(), clawback, totalRewarded);
        }
    }
}
