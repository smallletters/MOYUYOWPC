package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.admin.order.OrderRefundRequest;
import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.admin.entity.FinanceRecordEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
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
import com.moyuyo.service.InviteService;
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
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
@SuppressWarnings("null")
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundChannelService refundChannelService;
    private final MemberService memberService;
    /** 退款联动：扣回由该订单触发的邀请首单奖励 */
    private final InviteService inviteService;
    private final PointsLogMapper pointsLogMapper;
    // 财务流水 Mapper：退款完成时写入 mo_finance_record
    private final FinanceRecordMapper financeRecordMapper;
    /** 全局复用的 Jackson 实例（由 Spring Boot 自动注入） */
    private final ObjectMapper objectMapper;

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

        // AUTO 是历史内部哨兵值，禁止作为人工录入的第三方流水号，避免手动退款被误判为自动退款
        if (transactionId != null && "AUTO".equalsIgnoreCase(transactionId.trim())) {
            throw new IllegalArgumentException("第三方退款流水号不能使用保留值 AUTO，如需自动退款请留空");
        }

        // 对订单行加锁，串行化同一订单的多笔退款完成操作，避免并发完成导致累计金额超限
        OrderEntity order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getId, entity.getOrderId())
                        .last("FOR UPDATE"));

        // 累计退款上限校验：防止「后台直退 + 用户分次退款」等多笔部分退款合计超过订单实付金额。
        // 只统计已完成退款，当前退款单此时仍为 APPROVED，尚未计入。
        if (order != null && entity.getAmount() != null && order.getPayAmount() != null) {
            BigDecimal completedBefore = sumCompletedRefundAmount(order.getId());
            if (completedBefore.add(entity.getAmount()).compareTo(order.getPayAmount()) > 0) {
                throw new IllegalStateException("累计退款金额超过订单实付金额，禁止退款");
            }
        }

        // 原子抢占：仅 APPROVED 可进入完成流程(PROCESSING→COMPLETED)。
        // 防止并发重复点击/Webhook 重试导致"多次三方退款 + 多次积分返还/扣回"。
        // 若第三方调用失败抛出异常，事务回滚自动恢复 APPROVED 供重试。
        int claimed = refundMapper.update(null,
            new LambdaUpdateWrapper<RefundEntity>()
                .eq(RefundEntity::getId, refundId)
                .eq(RefundEntity::getStatus, "APPROVED")
                .set(RefundEntity::getStatus, "PROCESSING"));
        if (claimed == 0) {
            throw new IllegalStateException("退款单已被处理或当前状态不允许完成退款");
        }

        // #3：自动调用第三方支付渠道退款 API
        // 约定：transactionId 为空 = 自动退款；非空 = 财务/管理员已线下退款，仅录入流水号
        String finalTransactionId = transactionId;
        if (finalTransactionId == null || finalTransactionId.trim().isEmpty()) {
            // 自动模式：根据订单支付渠道调用对应三方退款 API
            RefundChannelEnum channel = RefundChannelEnum.fromValue(order == null ? null : order.getPayChannel());
            String payTransactionId = order == null ? null : order.getPayTransactionId();
            String orderNo = order == null ? entity.getRefundNo() : order.getOrderNo();
            String payCurrency = order == null ? null : order.getCurrency();
            try {
                finalTransactionId = refundChannelService.refund(
                        channel, payTransactionId, orderNo, entity.getAmount(), entity.getRefundNo(),
                        payCurrency);
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

            // 邀请奖励联动：仅当该订单是触发邀请首单奖励的订单，且本次为「全额退完」时才扣回双方积分。
            // 部分退款按比例扣回邀请奖励不合理（原先只要发生任何退款就全额扣回），这里改为累计退款额判断。
            BigDecimal totalCompleted = sumCompletedRefundAmount(order.getId());
            boolean fullyRefunded = order.getPayAmount() == null
                    || totalCompleted.compareTo(order.getPayAmount()) >= 0;
            if (fullyRefunded) {
                // 邀请奖励联动：仅当该订单是触发邀请首单奖励的订单时，扣回双方各 200 积分
                try {
                    inviteService.revokeInviteRewardOnRefund(
                            order.getId(), order.getOrderNo(), order.getUserId());
                } catch (Exception e) {
                    log.error("[refund] 邀请积分扣回失败 refundId={}, orderNo={}, reason={}",
                            refundId, order.getOrderNo(), e.getMessage(), e);
                }
            }

            // 同步写入交易流水（mo_finance_record），供后台"交易流水"实时展示
            try {
                FinanceRecordEntity record = new FinanceRecordEntity();
                record.setOrderNo(order.getOrderNo());
                record.setType("REFUND");
                record.setChannel(order.getPayChannel());
                record.setAmount(entity.getAmount() != null ? entity.getAmount() : BigDecimal.ZERO);
                record.setStatus("SUCCESS");
                record.setCreateTime(LocalDateTime.now());
                financeRecordMapper.insert(record);
            } catch (Exception e) {
                log.error("[finance-record] 写入 REFUND 流水失败 refundId={}, orderNo={}, reason={}",
                        refundId, order.getOrderNo(), e.getMessage(), e);
            }
        }
        log.info("Refund completed: refundId={}, operatorId={}, transactionId={}",
                refundId, operatorId, finalTransactionId);
    }

    @Override
    public List<RefundVO> listOrderRefunds(Long orderId) {
        // 管理后台订单详情：展示该订单全部退款记录（含被拒绝的，便于追溯）
        return refundMapper.selectList(
                        new LambdaQueryWrapper<RefundEntity>()
                                .eq(RefundEntity::getOrderId, orderId)
                                .orderByDesc(RefundEntity::getCreateTime))
                .stream().map(this::toRefundVO).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> adminRefundOrder(Long orderId, OrderRefundRequest request, Long operatorId) {
        if (request == null) {
            throw new IllegalArgumentException("退款参数不能为空");
        }
        // 0) 订单行锁：同一订单的并发退款串行化，避免多次点击/多人操作导致超额退款
        OrderEntity order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getId, orderId)
                        .last("FOR UPDATE"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        // 1) 状态与金额校验（与 WooCommerce 一致：已支付且未全额退款的订单均可退）
        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        boolean refundableStatus = OrderStatusEnum.PAID.name().equals(status)
                || OrderStatusEnum.PENDING_SHIP.name().equals(status)
                || OrderStatusEnum.SHIPPED.name().equals(status)
                || OrderStatusEnum.RECEIVED.name().equals(status)
                || OrderStatusEnum.COMPLETED.name().equals(status)
                // 兜底：历史部分退款可能把订单置为 REFUNDED，只要仍有剩余可退金额就允许继续退
                || OrderStatusEnum.REFUNDED.name().equals(status);
        if (!refundableStatus) {
            throw new IllegalStateException("当前订单状态不允许退款: " + order.getStatus());
        }
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.signum() <= 0) {
            throw new IllegalStateException("订单实付金额为 0，无需退款");
        }

        // 2) 同一订单不允许存在进行中的退款申请（含用户侧提交的退款）
        long pendingCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundEntity>()
                        .eq(RefundEntity::getOrderId, orderId)
                        .in(RefundEntity::getStatus, "PENDING", "APPROVED", "PROCESSING"));
        if (pendingCount > 0) {
            throw new IllegalStateException("该订单已有进行中的退款申请，请先在退款管理中处理");
        }

        // 3) 计算剩余可退金额 = 实付金额 - 已退/退款中金额
        BigDecimal refunded = sumRefundAmount(orderId);
        BigDecimal refundable = payAmount.subtract(refunded);
        if (refundable.signum() <= 0) {
            throw new IllegalStateException("该订单已全额退款，不能重复退款");
        }

        BigDecimal amount = request.getAmount() == null ? refundable
                : request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("退款金额必须大于 0");
        }
        if (amount.compareTo(refundable) > 0) {
            throw new IllegalArgumentException("退款金额超过剩余可退金额（剩余 " + refundable.toPlainString() + "）");
        }

        // 4) 退款方式：AUTO=调用原支付渠道；MANUAL=录入线下已退流水号
        String mode = request.getMode() == null || request.getMode().isBlank()
                ? "AUTO" : request.getMode().trim().toUpperCase();
        if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
            throw new IllegalArgumentException("不支持的退款方式: " + request.getMode());
        }
        String manualTransactionId = null;
        if ("MANUAL".equals(mode)) {
            manualTransactionId = request.getTransactionId() == null ? "" : request.getTransactionId().trim();
            if (manualTransactionId.isEmpty()) {
                throw new IllegalArgumentException("手动退款必须填写第三方退款流水号");
            }
            // AUTO 是内部自动退款的保留值，不能作为人工流水号，避免手动退款被误判为自动退款
            if ("AUTO".equalsIgnoreCase(manualTransactionId)) {
                throw new IllegalArgumentException("第三方退款流水号不能使用保留值 AUTO，请填写渠道后台的真实退款交易号");
            }
        } else {
            // 自动原路退回：必须已配置真实渠道密钥且订单存在第三方支付流水号，
            // 否则渠道会降级为本地假流水号，管理后台会误以为"已真实退款"
            RefundChannelEnum channel = RefundChannelEnum.fromValue(order.getPayChannel());
            if (channel == RefundChannelEnum.OTHER) {
                throw new IllegalStateException("未知支付渠道，请使用手动模式录入第三方退款流水号");
            }
            if (!refundChannelService.isAutoRefundSupported(channel)) {
                throw new IllegalStateException("支付渠道 " + channel
                        + " 未配置真实退款密钥，无法自动退款；请配置对应密钥或改用手动模式");
            }
            // 钱包/积分为内部账户退款，无需第三方流水号；其余渠道必须有三方交易号
            boolean externalChannel = channel != RefundChannelEnum.WALLET
                    && channel != RefundChannelEnum.POINTS;
            if (externalChannel
                    && (order.getPayTransactionId() == null || order.getPayTransactionId().isBlank())) {
                throw new IllegalStateException("订单缺少第三方支付流水号，无法自动退款，请改用手动模式");
            }
        }

        // 5) 生成退款单（全额退款记 FULL，其余记 PARTIAL）
        String refundNo = "RFN" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        RefundEntity entity = new RefundEntity();
        entity.setOrderId(orderId);
        entity.setRefundNo(refundNo);
        entity.setType(refunded.signum() == 0 && amount.compareTo(payAmount) == 0 ? "FULL" : "PARTIAL");
        entity.setAmount(amount);
        entity.setReason(request.getReason() == null || request.getReason().isBlank()
                ? "管理员退款" : request.getReason().trim());
        entity.setDescription(request.getDescription());
        entity.setStatus("PENDING");
        refundMapper.insert(entity);

        // 6) 复用既有审核 + 完成链路：审核置 APPROVED/REFUNDING，完成时调用三方退款
        String originalStatus = order.getStatus();
        approveRefund(entity.getId(), operatorId);
        // 自动模式传入 null（约定为空即自动退款），避免使用可被人工输入复用的 "AUTO" 哨兵值
        completeRefund(entity.getId(), operatorId,
                "MANUAL".equals(mode) ? manualTransactionId : null);

        // 7) 部分退款不改变原订单状态（completeRefund 统一置 REFUNDED，这里恢复）
        BigDecimal totalAfter = refunded.add(amount);
        if (totalAfter.compareTo(payAmount) < 0 && !OrderStatusEnum.REFUNDED.name().equals(originalStatus)) {
            orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
                    .eq(OrderEntity::getId, orderId)
                    .set(OrderEntity::getStatus, originalStatus));
        }

        RefundEntity fresh = refundMapper.selectById(entity.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("refundId", fresh != null ? fresh.getId() : entity.getId());
        result.put("refundNo", fresh != null ? fresh.getRefundNo() : refundNo);
        result.put("amount", amount.toPlainString());
        result.put("transactionId", fresh != null ? fresh.getTransactionId() : null);
        result.put("status", fresh != null ? fresh.getStatus() : "COMPLETED");
        result.put("refundedTotal", totalAfter.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        result.put("refundableAmount", payAmount.subtract(totalAfter).max(BigDecimal.ZERO).toPlainString());
        result.put("message", "退款已完成");
        log.info("[admin-refund] 订单退款完成 orderId={}, refundNo={}, amount={}, mode={}, operatorId={}",
                orderId, refundNo, amount, mode, operatorId);
        return result;
    }

    /** 统计订单已发生（退款中/已完成）的退款金额合计 */
    private BigDecimal sumRefundAmount(Long orderId) {
        List<Map<String, Object>> rows = refundMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
                        .select("COALESCE(SUM(amount), 0) AS amt")
                        .eq("order_id", orderId)
                        .in("status", "COMPLETED", "PROCESSING"));
        if (rows == null || rows.isEmpty()) return BigDecimal.ZERO;
        Object amt = rows.get(0).get("amt");
        return amt == null ? BigDecimal.ZERO : new BigDecimal(amt.toString());
    }

    /** 统计订单「已完成」的退款金额合计（用于累计退款上限与全额判定） */
    private BigDecimal sumCompletedRefundAmount(Long orderId) {
        if (orderId == null) return BigDecimal.ZERO;
        List<Map<String, Object>> rows = refundMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
                        .select("COALESCE(SUM(amount), 0) AS amt")
                        .eq("order_id", orderId)
                        .eq("status", "COMPLETED"));
        if (rows == null || rows.isEmpty()) return BigDecimal.ZERO;
        Object amt = rows.get(0).get("amt");
        return amt == null ? BigDecimal.ZERO : new BigDecimal(amt.toString());
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
