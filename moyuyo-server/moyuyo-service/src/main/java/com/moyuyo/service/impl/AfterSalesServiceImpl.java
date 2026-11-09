package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.order.OrderRefundRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesCreateRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.AfterSalesEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.AfterSalesMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.service.AfterSalesService;
import com.moyuyo.service.NotificationService;
import com.moyuyo.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSalesServiceImpl implements AfterSalesService {

  /** 售后时效：签收/发货后 30 天（对齐亚马逊） */
  private static final int AFTER_SALES_WINDOW_DAYS = 30;

  /** 允许的售后类型 */
  private static final Set<String> ALLOWED_TYPES =
      Set.of("RETURN_REFUND", "REFUND_ONLY", "EXCHANGE");

  /** 进行中的售后状态：同一订单项不允许重复申请 */
  private static final Set<String> ACTIVE_STATUSES =
      Set.of("PENDING", "APPROVED", "RETURNING", "RECEIVED");

  private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final AfterSalesMapper afterSalesMapper;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final RefundMapper refundMapper;
  private final RefundService refundService;
  private final NotificationService notificationService;
  /** 退货入库：确认收货时按售后数量回补 SKU/商品库存 */
  private final ProductSkuMapper productSkuMapper;
  private final ProductMapper productMapper;

  // ==================================================================
  // C 端
  // ==================================================================

  @Override
  @Transactional
  public AfterSalesVO createAfterSales(Long userId, AfterSalesCreateRequest request) {
    String type = normalizeType(request.getType());
    if (!ALLOWED_TYPES.contains(type)) {
      throw new IllegalArgumentException("不支持的售后类型: " + request.getType());
    }

    // 1) 订单行锁：同一订单并发申请串行化，避免重复生成售后单
    OrderEntity order = orderMapper.selectByIdForUpdate(request.getOrderId());
    if (order == null || !Objects.equals(order.getUserId(), userId)) {
      throw new IllegalArgumentException("订单不存在");
    }
    validateOrderStatus(order, type);
    validateAfterSalesWindow(order, type);

    // 2) 商品行校验（退货退款/换货必须指定商品）
    OrderItemEntity orderItem = null;
    if (request.getOrderItemId() != null) {
      orderItem = orderItemMapper.selectById(request.getOrderItemId());
      if (orderItem == null || !Objects.equals(orderItem.getOrderId(), order.getId())) {
        throw new IllegalArgumentException("商品不属于该订单");
      }
    } else if ("RETURN_REFUND".equals(type) || "EXCHANGE".equals(type)) {
      throw new IllegalArgumentException("退货退款/换货必须选择商品");
    }

    // 3) 防重复：
    //    - 整单级售后：只要订单存在任何进行中的售后，就不允许再提交，否则商品级在途申请
    //      不会被计入剩余可退金额，可能出现"商品退一半 + 整单再退一次"的重复赔付；
    //    - 商品级售后：整单级在途 或 同一商品行在途 都拦截；
    //      同一商品行允许分次退货，但必须等上一条处理完成，避免在途金额重复计算。
    if (orderItem == null) {
      if (afterSalesMapper.selectCount(new LambdaQueryWrapper<AfterSalesEntity>()
          .eq(AfterSalesEntity::getOrderId, order.getId())
          .in(AfterSalesEntity::getStatus, ACTIVE_STATUSES)) > 0) {
        throw new IllegalStateException("该订单已有进行中的售后申请，请等待处理完成");
      }
    } else {
      if (afterSalesMapper.selectCount(new LambdaQueryWrapper<AfterSalesEntity>()
          .eq(AfterSalesEntity::getOrderId, order.getId())
          .isNull(AfterSalesEntity::getOrderItemId)
          .in(AfterSalesEntity::getStatus, ACTIVE_STATUSES)) > 0) {
        throw new IllegalStateException("该订单已有进行中的售后申请，请等待处理完成");
      }
      if (afterSalesMapper.selectCount(new LambdaQueryWrapper<AfterSalesEntity>()
          .eq(AfterSalesEntity::getOrderId, order.getId())
          .eq(AfterSalesEntity::getOrderItemId, orderItem.getId())
          .in(AfterSalesEntity::getStatus, ACTIVE_STATUSES)) > 0) {
        throw new IllegalStateException("该商品已有进行中的售后申请，请等待处理完成后再申请剩余商品");
      }
    }

    // 3.1) 已存在进行中的退款单时不允许再申请售后，防止退款与售后重复赔付
    if (refundMapper.selectCount(new LambdaQueryWrapper<RefundEntity>()
        .eq(RefundEntity::getOrderId, order.getId())
        .in(RefundEntity::getStatus, "PENDING", "APPROVED", "PROCESSING")) > 0) {
      throw new IllegalStateException("该订单已有进行中的退款申请，请先等待处理完成");
    }

    // 4) 数量与金额校验（扣除该商品行历史已申请的数量与金额，支持分次退货）
    int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
    if (quantity <= 0) {
      throw new IllegalArgumentException("售后数量必须大于 0");
    }
    // 整单仅退款无商品行，数量固定为 1
    if (orderItem == null) {
      quantity = 1;
    } else {
      int boughtQuantity = orderItem.getQuantity() == null ? 1 : orderItem.getQuantity();
      int appliedQuantity = sumAppliedQuantity(order.getId(), orderItem.getId());
      int remainingQuantity = boughtQuantity - appliedQuantity;
      if (remainingQuantity <= 0) {
        throw new IllegalStateException("该商品已全部申请售后，无可退数量");
      }
      if (quantity > remainingQuantity) {
        throw new IllegalArgumentException(
            "售后数量(" + quantity + ") 超过剩余可退数量(" + remainingQuantity + ")");
      }
    }

    BigDecimal amount;
    if ("EXCHANGE".equals(type)) {
      // 换货不涉及退款
      amount = BigDecimal.ZERO;
    } else {
      amount = resolveRefundAmount(order, orderItem, quantity, request.getAmount());
    }

    // 5) 生成售后单
    AfterSalesEntity entity = new AfterSalesEntity();
    entity.setAfterSaleNo(generateAfterSaleNo());
    entity.setOrderId(order.getId());
    entity.setOrderItemId(orderItem == null ? null : orderItem.getId());
    entity.setUserId(userId);
    entity.setType(type);
    // 退货退款/换货默认需寄回；仅退款默认无需寄回（审核时可由管理员调整）
    entity.setDeliveryType("REFUND_ONLY".equals(type) ? "NO_RETURN" : "RETURN_SHIP");
    entity.setQuantity(quantity);
    entity.setReason(request.getReason());
    entity.setAmount(amount);
    entity.setDescription(request.getDescription());
    entity.setImages(request.getImages());
    entity.setStatus("PENDING");
    if (orderItem != null) {
      entity.setSkuId(orderItem.getSkuId());
      entity.setProductId(orderItem.getProductId());
      entity.setProductName(orderItem.getProductName());
      entity.setSkuSpec(orderItem.getSkuSpec());
      entity.setMainImage(orderItem.getMainImage());
    }
    afterSalesMapper.insert(entity);

    log.info("[after-sales] 申请提交 afterSaleNo={}, orderId={}, type={}, itemId={}, amount={}",
        entity.getAfterSaleNo(), order.getId(), type, entity.getOrderItemId(), amount);
    notifyUserSafely(userId, "售后申请已提交",
        "您的售后申请（" + entity.getAfterSaleNo() + "）已提交，商家将尽快审核", entity.getId());
    return toVO(entity, order.getOrderNo());
  }

  @Override
  public IPage<AfterSalesVO> listAfterSales(Long userId, int page, int size, String status, Long orderId) {
    LambdaQueryWrapper<AfterSalesEntity> wrapper = new LambdaQueryWrapper<AfterSalesEntity>()
        .eq(AfterSalesEntity::getUserId, userId);
    // 订单维度查询：分次退货后用户可查看该订单的全部售后记录
    if (orderId != null) {
      wrapper.eq(AfterSalesEntity::getOrderId, orderId);
    }
    if (status != null && !status.isBlank()) {
      wrapper.in(AfterSalesEntity::getStatus, expandStatusFilter(status));
    }
    wrapper.orderByDesc(AfterSalesEntity::getCreateTime);
    return toVOPage(afterSalesMapper.selectPage(new Page<>(page, size), wrapper));
  }

  @Override
  public AfterSalesVO getAfterSalesDetail(Long id, Long userId) {
    AfterSalesEntity entity = afterSalesMapper.selectById(id);
    if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
      throw new IllegalArgumentException("售后申请不存在");
    }
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  public AfterSalesVO getLatestAfterSales(Long orderId, Long userId) {
    if (orderId == null || userId == null) {
      return null;
    }
    AfterSalesEntity entity = afterSalesMapper.selectOne(
        new LambdaQueryWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getOrderId, orderId)
            .eq(AfterSalesEntity::getUserId, userId)
            .orderByDesc(AfterSalesEntity::getCreateTime)
            .last("LIMIT 1"));
    return entity == null ? null : toVO(entity, findOrderNo(orderId));
  }

  @Override
  public Map<Long, Integer> getAppliedQuantityByOrder(Long orderId, Long userId) {
    Map<Long, Integer> result = new HashMap<>();
    if (orderId == null || userId == null) {
      return result;
    }
    List<Map<String, Object>> rows = afterSalesMapper.selectMaps(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AfterSalesEntity>()
            .select("order_item_id AS itemId", "COALESCE(SUM(quantity), 0) AS qty")
            .eq("order_id", orderId)
            .eq("user_id", userId)
            .isNotNull("order_item_id")
            .notIn("status", "REJECTED", "CANCELLED")
            .groupBy("order_item_id"));
    for (Map<String, Object> row : rows) {
      Object itemId = row.get("itemId");
      Object qty = row.get("qty");
      if (itemId == null || qty == null) continue;
      result.put(((Number) itemId).longValue(), new BigDecimal(qty.toString()).intValue());
    }
    return result;
  }

  @Override
  public Map<Long, BigDecimal> getAppliedAmountByOrder(Long orderId, Long userId) {
    Map<Long, BigDecimal> result = new HashMap<>();
    if (orderId == null || userId == null) {
      return result;
    }
    List<Map<String, Object>> rows = afterSalesMapper.selectMaps(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AfterSalesEntity>()
            .select("order_item_id AS itemId", "COALESCE(SUM(amount), 0) AS amt")
            .eq("order_id", orderId)
            .eq("user_id", userId)
            .isNotNull("order_item_id")
            .notIn("status", "REJECTED", "CANCELLED")
            .groupBy("order_item_id"));
    for (Map<String, Object> row : rows) {
      Object itemId = row.get("itemId");
      Object amt = row.get("amt");
      if (itemId == null || amt == null) continue;
      result.put(((Number) itemId).longValue(), new BigDecimal(amt.toString()));
    }
    return result;
  }

  @Override
  @Transactional
  public AfterSalesVO submitReturnLogistics(Long userId, Long id, String company, String trackingNo) {
    AfterSalesEntity entity = requireOwned(id, userId);
    // 待寄回与已寄回都允许填写：已寄回时视为更正填错的运单号
    if (!"RETURN_SHIP".equals(entity.getDeliveryType())
        || (!"APPROVED".equals(entity.getStatus()) && !"RETURNING".equals(entity.getStatus()))) {
      throw new IllegalStateException("当前售后状态不允许填写退货物流");
    }
    entity.setReturnCompany(company.trim());
    entity.setReturnTrackingNo(trackingNo.trim());
    if (entity.getReturnTime() == null) {
      entity.setReturnTime(LocalDateTime.now());
    }
    entity.setStatus("RETURNING");
    afterSalesMapper.updateById(entity);
    log.info("[after-sales] 用户填写退货物流 afterSaleNo={}, company={}, trackingNo={}",
        entity.getAfterSaleNo(), company, trackingNo);
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public AfterSalesVO cancelAfterSales(Long userId, Long id) {
    AfterSalesEntity entity = requireOwned(id, userId);
    String current = entity.getStatus();
    if (!"PENDING".equals(current) && !"APPROVED".equals(current)) {
      throw new IllegalStateException("当前售后状态不允许取消");
    }
    // 条件更新：只允许从刚才读到的状态取消，避免与商家审核/拒绝并发时互相覆盖
    LocalDateTime now = LocalDateTime.now();
    int updated = afterSalesMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getId, id)
            .eq(AfterSalesEntity::getUserId, userId)
            .eq(AfterSalesEntity::getStatus, current)
            .set(AfterSalesEntity::getStatus, "CANCELLED")
            .set(AfterSalesEntity::getCompleteTime, now));
    if (updated == 0) {
      throw new IllegalStateException("售后状态已变更，请刷新后重试");
    }
    entity.setStatus("CANCELLED");
    entity.setCompleteTime(now);
    log.info("[after-sales] 用户取消售后 afterSaleNo={}", entity.getAfterSaleNo());
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  // ==================================================================
  // 管理端
  // ==================================================================

  @Override
  public IPage<AfterSalesVO> listAllAfterSales(int page, int size, String status, String type) {
    LambdaQueryWrapper<AfterSalesEntity> wrapper = new LambdaQueryWrapper<AfterSalesEntity>()
        .orderByDesc(AfterSalesEntity::getCreateTime);
    if (status != null && !status.isBlank()) {
      wrapper.in(AfterSalesEntity::getStatus, expandStatusFilter(status));
    }
    if (type != null && !type.isBlank()) {
      String normalized = normalizeType(type);
      if (ALLOWED_TYPES.contains(normalized)) {
        wrapper.eq(AfterSalesEntity::getType, normalized);
      }
    }
    return toVOPage(afterSalesMapper.selectPage(new Page<>(page, size), wrapper));
  }

  @Override
  public AfterSalesVO getAfterSalesDetailForAdmin(Long id) {
    AfterSalesEntity entity = afterSalesMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("售后申请不存在");
    }
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public AfterSalesVO approveAfterSales(Long id, Long operatorId, String deliveryType, String remark) {
    AfterSalesEntity entity = requireEntity(id);
    if (!"PENDING".equals(entity.getStatus())) {
      throw new IllegalStateException("当前售后状态不允许审核");
    }

    String type = normalizeType(entity.getType());
    String resolvedDelivery = resolveDeliveryType(type, deliveryType);
    String nextStatus = "NO_RETURN".equals(resolvedDelivery) ? "RECEIVED" : "APPROVED";
    LocalDateTime approveTime = LocalDateTime.now();
    // 条件更新（PENDING → APPROVED/RECEIVED）：防止重复点击/并发审核导致重复处理与重复通知
    int updated = afterSalesMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getId, id)
            .eq(AfterSalesEntity::getStatus, "PENDING")
            .set(AfterSalesEntity::getStatus, nextStatus)
            .set(AfterSalesEntity::getDeliveryType, resolvedDelivery)
            .set(AfterSalesEntity::getApproveTime, approveTime)
            .set(AfterSalesEntity::getOperatorId, operatorId)
            .set(remark != null && !remark.isBlank(), AfterSalesEntity::getRemark,
                remark == null ? null : remark.trim()));
    if (updated == 0) {
      throw new IllegalStateException("售后状态已变更，请刷新后重试");
    }
    entity.setDeliveryType(resolvedDelivery);
    entity.setApproveTime(approveTime);
    entity.setOperatorId(operatorId);
    if (remark != null && !remark.isBlank()) {
      entity.setRemark(remark.trim());
    }
    entity.setStatus(nextStatus);
    log.info("[after-sales] 审核通过 afterSaleNo={}, deliveryType={}, operatorId={}",
        entity.getAfterSaleNo(), resolvedDelivery, operatorId);
    String approveHint = "RETURN_SHIP".equals(resolvedDelivery)
        ? "审核已通过，请在 7 天内寄回商品并填写退货运单号"
        : "审核已通过，退款将尽快原路退回";
    notifyUserSafely(entity.getUserId(), "售后审核通过",
        "售后单 " + entity.getAfterSaleNo() + "：" + approveHint, entity.getId());
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public AfterSalesVO rejectAfterSales(Long id, Long operatorId, String reason) {
    AfterSalesEntity entity = requireEntity(id);
    if (!"PENDING".equals(entity.getStatus())) {
      throw new IllegalStateException("当前售后状态不允许拒绝");
    }
    String rejectReason = reason == null || reason.isBlank() ? "商家拒绝售后" : reason.trim();
    LocalDateTime now = LocalDateTime.now();
    int updated = afterSalesMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getId, id)
            .eq(AfterSalesEntity::getStatus, "PENDING")
            .set(AfterSalesEntity::getStatus, "REJECTED")
            .set(AfterSalesEntity::getRejectReason, rejectReason)
            .set(AfterSalesEntity::getRejectTime, now)
            .set(AfterSalesEntity::getOperatorId, operatorId));
    if (updated == 0) {
      throw new IllegalStateException("售后状态已变更，请刷新后重试");
    }
    entity.setStatus("REJECTED");
    entity.setRejectReason(rejectReason);
    entity.setRejectTime(now);
    entity.setOperatorId(operatorId);
    log.info("[after-sales] 拒绝售后 afterSaleNo={}, reason={}", entity.getAfterSaleNo(), reason);
    notifyUserSafely(entity.getUserId(), "售后申请未通过",
        "售后单 " + entity.getAfterSaleNo() + " 未通过审核：" + entity.getRejectReason(), entity.getId());
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public AfterSalesVO confirmReceived(Long id, Long operatorId) {
    AfterSalesEntity entity = requireEntity(id);
    LocalDateTime now = LocalDateTime.now();
    // 原子抢占（RETURNING → RECEIVED）：重复点击/并发收货时只有一次能成功，
    // 否则库存会被重复回补（restoreStock 非幂等）
    int claimed = afterSalesMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getId, id)
            .eq(AfterSalesEntity::getStatus, "RETURNING")
            .set(AfterSalesEntity::getStatus, "RECEIVED")
            .set(AfterSalesEntity::getReceiveTime, now)
            .set(AfterSalesEntity::getOperatorId, operatorId));
    if (claimed == 0) {
      throw new IllegalStateException("当前售后状态不允许确认收货");
    }
    entity.setStatus("RECEIVED");
    entity.setReceiveTime(now);
    entity.setOperatorId(operatorId);

    // 退货入库：退回的商品回到可售库存，避免退货后持续少卖
    restoreStock(entity);

    log.info("[after-sales] 确认收货 afterSaleNo={}", entity.getAfterSaleNo());
    notifyUserSafely(entity.getUserId(), "商家已收到退货",
        "售后单 " + entity.getAfterSaleNo() + " 商家已确认收货，将尽快为您处理退款", entity.getId());
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public AfterSalesVO adminSubmitReturnLogistics(Long id, Long operatorId, String company,
                                                 String trackingNo) {
    AfterSalesEntity entity = requireEntity(id);
    if (!"RETURN_SHIP".equals(entity.getDeliveryType())
        || (!"APPROVED".equals(entity.getStatus()) && !"RETURNING".equals(entity.getStatus()))) {
      throw new IllegalStateException("当前售后状态不允许补录退货物流");
    }
    entity.setReturnCompany(company.trim());
    entity.setReturnTrackingNo(trackingNo.trim());
    if (entity.getReturnTime() == null) {
      entity.setReturnTime(LocalDateTime.now());
    }
    entity.setStatus("RETURNING");
    entity.setOperatorId(operatorId);
    afterSalesMapper.updateById(entity);
    log.info("[after-sales] 客服补录退货物流 afterSaleNo={}, company={}, trackingNo={}, operatorId={}",
        entity.getAfterSaleNo(), company, trackingNo, operatorId);
    notifyUserSafely(entity.getUserId(), "退货物流已更新",
        "售后单 " + entity.getAfterSaleNo() + " 退货物流已更新为 " + company + " " + trackingNo,
        entity.getId());
    return toVO(entity, findOrderNo(entity.getOrderId()));
  }

  @Override
  @Transactional
  public int closeTimeoutAfterSales(int pendingDays, int returnDays) {
    int closed = 0;
    LocalDateTime now = LocalDateTime.now();
    // 1) 待审核超时：以"提交时间"为准（商家长期不处理）
    if (pendingDays > 0) {
      closed += closeTimeoutBatch("PENDING", now.minusDays(pendingDays),
          "商家超时未审核，系统自动关闭", false);
    }
    // 2) 待寄回超时：以"审核通过时间"为准（历史数据 approve_time 为空时回退 create_time），
    //    若按创建时间计算，早期申请刚审核通过就会被立刻关闭
    if (returnDays > 0) {
      closed += closeTimeoutBatch("APPROVED", now.minusDays(returnDays),
          "超时未寄回，系统自动关闭", true);
    }
    return closed;
  }

  /**
   * 按状态 + 时间阈值批量关闭。
   *
   * @param byApproveTime true 用 approveTime（审核通过时间）作为基准，false 用 createTime
   */
  private int closeTimeoutBatch(String status, LocalDateTime threshold, String remark,
                                boolean byApproveTime) {
    LambdaQueryWrapper<AfterSalesEntity> query = new LambdaQueryWrapper<AfterSalesEntity>()
        .eq(AfterSalesEntity::getStatus, status)
        .last("LIMIT 200");
    if (byApproveTime) {
      // 历史 APPROVED 数据没有 approve_time，回退 create_time 兜底，避免永远不被关闭
      query.and(w -> w.isNotNull(AfterSalesEntity::getApproveTime)
          .lt(AfterSalesEntity::getApproveTime, threshold)
          .or()
          .isNull(AfterSalesEntity::getApproveTime)
          .lt(AfterSalesEntity::getCreateTime, threshold));
    } else {
      query.lt(AfterSalesEntity::getCreateTime, threshold);
    }
    List<AfterSalesEntity> expired = afterSalesMapper.selectList(query);
    if (expired.isEmpty()) {
      return 0;
    }
    int closed = 0;
    for (AfterSalesEntity entity : expired) {
      try {
        // 状态条件更新：并发下已被审核/取消的记录不会被覆盖；
        // remark 仅在为空时写入，避免反复追加导致超长
        int affected = afterSalesMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
                .eq(AfterSalesEntity::getId, entity.getId())
                .eq(AfterSalesEntity::getStatus, status)
                .set(AfterSalesEntity::getStatus, "CANCELLED")
                .setSql("remark = CASE WHEN remark IS NULL OR remark = '' THEN '" + remark
                    + "' ELSE remark END")
                .set(AfterSalesEntity::getCompleteTime, LocalDateTime.now()));
        if (affected == 0) {
          continue;
        }
        closed++;
        notifyUserSafely(entity.getUserId(), "售后申请已自动关闭",
            "售后单 " + entity.getAfterSaleNo() + " " + remark + "，如需售后请重新提交", entity.getId());
      } catch (Exception e) {
        // 单条失败不影响其余售后单的关闭
        log.warn("[after-sales] 超时关闭失败 afterSaleNo={}, reason={}",
            entity.getAfterSaleNo(), e.getMessage());
      }
    }
    return closed;
  }

  /**
   * 退货入库：按售后数量回补 SKU 或商品库存。
   * <p>
   * 仅"需寄回"的售后（退货退款/换货）在确认收货时回补；仅退款无实物退回，不回补。
   * 调用点已由状态机保证只执行一次（RETURNING → RECEIVED）。
   */
  private void restoreStock(AfterSalesEntity entity) {
    if (!"RETURN_SHIP".equals(entity.getDeliveryType())) {
      return;
    }
    int quantity = entity.getQuantity() == null || entity.getQuantity() <= 0
        ? 1 : entity.getQuantity();
    try {
      if (entity.getSkuId() != null) {
        int affected = productSkuMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductSkuEntity>()
                .eq(ProductSkuEntity::getId, entity.getSkuId())
                .setSql("stock = stock + " + quantity));
        if (affected == 0) {
          log.warn("[after-sales] 退货入库失败，SKU 不存在 skuId={}, afterSaleNo={}",
              entity.getSkuId(), entity.getAfterSaleNo());
        }
      } else if (entity.getProductId() != null) {
        int affected = productMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductEntity>()
                .eq(ProductEntity::getId, entity.getProductId())
                .setSql("stock = stock + " + quantity));
        if (affected == 0) {
          log.warn("[after-sales] 退货入库失败，商品不存在 productId={}, afterSaleNo={}",
              entity.getProductId(), entity.getAfterSaleNo());
        }
      }
      log.info("[after-sales] 退货入库完成 afterSaleNo={}, skuId={}, productId={}, quantity={}",
          entity.getAfterSaleNo(), entity.getSkuId(), entity.getProductId(), quantity);
    } catch (Exception e) {
      // 库存回补失败不回滚收货状态，记录日志供人工核对
      log.error("[after-sales] 退货入库异常 afterSaleNo={}, reason={}",
          entity.getAfterSaleNo(), e.getMessage(), e);
    }
  }

  @Override
  @Transactional
  public Map<String, Object> completeAfterSales(Long id, Long operatorId, String mode, String transactionId) {
    AfterSalesEntity entity = requireEntity(id);
    String type = normalizeType(entity.getType());

    // 原子抢占（RECEIVED → COMPLETED）：并发/重复点击时只有一次能进入完成流程。
    // 若后续退款或写库失败，事务回滚会把状态还原为 RECEIVED，可安全重试。
    // 不加这层保护时，两个并发请求会各自调用一次退款，造成重复退款。
    int claimed = afterSalesMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSalesEntity>()
            .eq(AfterSalesEntity::getId, id)
            .eq(AfterSalesEntity::getStatus, "RECEIVED")
            .set(AfterSalesEntity::getStatus, "COMPLETED"));
    if (claimed == 0) {
      throw new IllegalStateException("EXCHANGE".equals(type)
          ? "换货需先确认收到退回商品"
          : "当前售后状态不允许完成退款（需先确认收货）");
    }
    entity.setStatus("COMPLETED");
    entity.setOperatorId(operatorId);

    Map<String, Object> result = new HashMap<>();
    if ("EXCHANGE".equals(type)) {
      // 换货：无需退款，商家寄出换货商品后直接完成
      entity.setCompleteTime(LocalDateTime.now());
      afterSalesMapper.updateById(entity);
      result.put("afterSaleId", entity.getId());
      result.put("afterSaleNo", entity.getAfterSaleNo());
      result.put("status", entity.getStatus());
      result.put("message", "换货已完成");
      log.info("[after-sales] 换货完成 afterSaleNo={}", entity.getAfterSaleNo());
      notifyUserSafely(entity.getUserId(), "换货已完成",
          "售后单 " + entity.getAfterSaleNo() + " 换货已完成，请留意物流信息", entity.getId());
      return result;
    }

    if (entity.getAmount() == null || entity.getAmount().signum() <= 0) {
      throw new IllegalStateException("售后退款金额异常，无法退款");
    }

    OrderRefundRequest refundRequest = new OrderRefundRequest();
    refundRequest.setAmount(entity.getAmount());
    refundRequest.setReason("售后退款: " + (entity.getReason() == null ? type : entity.getReason()));
    refundRequest.setDescription(entity.getDescription());
    refundRequest.setMode(mode);
    refundRequest.setTransactionId(transactionId);

    // 复用退款服务的「审核 + 完成」链路：自动原路退回/人工流水号、积分扣回、财务流水全部覆盖
    Map<String, Object> refundResult =
        refundService.adminRefundOrder(entity.getOrderId(), refundRequest, operatorId);

    entity.setCompleteTime(LocalDateTime.now());
    entity.setOperatorId(operatorId);
    entity.setRefundId(toLong(refundResult.get("refundId")));
    entity.setRefundNo(refundResult.get("refundNo") == null
        ? null : refundResult.get("refundNo").toString());
    afterSalesMapper.updateById(entity);

    result.putAll(refundResult);
    result.put("afterSaleId", entity.getId());
    result.put("afterSaleNo", entity.getAfterSaleNo());
    result.put("status", entity.getStatus());
    result.put("message", "售后退款已完成");
    log.info("[after-sales] 完成退款 afterSaleNo={}, refundNo={}, amount={}",
        entity.getAfterSaleNo(), entity.getRefundNo(), entity.getAmount());
    notifyUserSafely(entity.getUserId(), "售后退款已完成",
        "售后单 " + entity.getAfterSaleNo() + " 已退款 " + entity.getAmount().toPlainString()
            + "，退款将原路退回，请留意到账通知", entity.getId());
    return result;
  }

  /**
   * 售后通知：站内信 + App 推送（尽力而为，绝不阻断售后主流程）
   * <p>
   * 通知类型统一使用 ORDER，与 APP 通知中心「订单」Tab 对齐。
   */
  private void notifyUserSafely(Long userId, String title, String content, Long relatedId) {
    try {
      // bizType=AFTER_SALE：APP 点击通知时跳转到售后详情，而不是订单详情
      notificationService.notifyUser(userId, "ORDER", "AFTER_SALE", title, content, relatedId);
    } catch (Exception e) {
      log.warn("[after-sales] 发送售后通知失败 userId={}, relatedId={}, reason={}",
          userId, relatedId, e.getMessage());
    }
  }

  // ==================================================================
  // 校验与工具
  // ==================================================================

  /** 售后类型归一：兼容历史 REFUND/RETURN 数据 */
  private String normalizeType(String type) {
    if (type == null) return "";
    String normalized = type.trim().toUpperCase();
    return switch (normalized) {
      case "RETURN", "RETURN_REFUND" -> "RETURN_REFUND";
      case "REFUND", "REFUND_ONLY" -> "REFUND_ONLY";
      case "EXCHANGE" -> "EXCHANGE";
      default -> normalized;
    };
  }

  /** 状态筛选归一：processing 同时匹配审核通过/已寄回/已收货，closed 匹配拒绝/取消 */
  private Set<String> expandStatusFilter(String status) {
    String key = status.trim().toUpperCase();
    return switch (key) {
      case "ALL" -> Set.of("PENDING", "APPROVED", "RETURNING", "RECEIVED",
          "COMPLETED", "REJECTED", "CANCELLED");
      case "PROCESSING" -> Set.of("APPROVED", "RETURNING", "RECEIVED");
      case "CLOSED" -> Set.of("REJECTED", "CANCELLED");
      case "COMPLETED" -> Set.of("COMPLETED");
      default -> Set.of(key);
    };
  }

  private void validateOrderStatus(OrderEntity order, String type) {
    OrderStatusEnum status = OrderStatusEnum.fromValue(order.getStatus());
    if (status == null) {
      throw new IllegalStateException("当前订单状态不允许申请售后");
    }
    boolean refundOnlyAllowed = status == OrderStatusEnum.PAID
        || status == OrderStatusEnum.PENDING_SHIP
        || status == OrderStatusEnum.SHIPPED
        || status == OrderStatusEnum.RECEIVED
        || status == OrderStatusEnum.COMPLETED
        || status == OrderStatusEnum.REFUNDED;
    boolean returnAllowed = status == OrderStatusEnum.SHIPPED
        || status == OrderStatusEnum.RECEIVED
        || status == OrderStatusEnum.COMPLETED;
    if ("REFUND_ONLY".equals(type)) {
      if (!refundOnlyAllowed) {
        throw new IllegalStateException("当前订单状态不允许申请退款");
      }
      return;
    }
    if (!returnAllowed) {
      throw new IllegalStateException("商品尚未发货，请选择「仅退款」");
    }
  }

  /** 30 天售后时效：以签收时间优先，其次发货时间，最后支付时间 */
  private void validateAfterSalesWindow(OrderEntity order, String type) {
    // 未发货的仅退款不受签收时效限制
    if ("REFUND_ONLY".equals(type) && order.getDeliverTime() == null) {
      return;
    }
    LocalDateTime baseTime = order.getReceivedTime() != null ? order.getReceivedTime()
        : order.getDeliverTime() != null ? order.getDeliverTime() : order.getPaidAt();
    if (baseTime == null) {
      return;
    }
    if (LocalDateTime.now().isAfter(baseTime.plusDays(AFTER_SALES_WINDOW_DAYS))) {
      throw new IllegalStateException("已超过 " + AFTER_SALES_WINDOW_DAYS + " 天售后申请时效");
    }
  }

  /**
   * 计算并校验退款金额：
   * - 按商品：max = 商品小计 × (本次数量 / 购买数量) - 该商品行历史已申请售后金额
   * - 整单仅退款：max = 订单剩余可退金额
   * - 同时受订单剩余可退金额约束，防止与退款单叠加超额
   */
  private BigDecimal resolveRefundAmount(OrderEntity order, OrderItemEntity orderItem,
                                         int quantity, BigDecimal requestAmount) {
    BigDecimal orderRefundable = remainingRefundable(order);
    if (orderRefundable.signum() <= 0) {
      throw new IllegalStateException("该订单已全额退款，不能重复申请");
    }

    BigDecimal maxAmount;
    if (orderItem != null) {
      int boughtQuantity = orderItem.getQuantity() == null ? 1 : orderItem.getQuantity();
      BigDecimal subtotal = orderItem.getSubtotal();
      if (subtotal == null && orderItem.getPrice() != null) {
        subtotal = orderItem.getPrice().multiply(BigDecimal.valueOf(boughtQuantity));
      }
      if (subtotal == null) {
        throw new IllegalStateException("商品金额异常，无法申请售后");
      }
      // 分次退货：先按数量比例算出本次应退金额，再扣除该商品行历史已申请的售后金额
      BigDecimal proportional = subtotal
          .multiply(BigDecimal.valueOf(quantity))
          .divide(BigDecimal.valueOf(boughtQuantity), 2, RoundingMode.HALF_UP);
      BigDecimal appliedAmount = sumAppliedAmount(order.getId(), orderItem.getId());
      BigDecimal itemRemaining = subtotal.subtract(appliedAmount).max(BigDecimal.ZERO);
      maxAmount = proportional.min(itemRemaining);
    } else {
      maxAmount = orderRefundable;
    }
    maxAmount = maxAmount.min(orderRefundable);

    BigDecimal amount = requestAmount == null
        ? maxAmount : requestAmount.setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("退款金额必须大于 0");
    }
    if (amount.compareTo(maxAmount) > 0) {
      throw new IllegalArgumentException("退款金额超过可退上限（最多 " + maxAmount.toPlainString() + "）");
    }
    return amount;
  }

  /**
   * 该商品行历史已申请售后的数量合计。
   * <p>
   * 排除已拒绝/已取消的申请（这些申请未实际退货），用于支持同一商品行分次退货。
   */
  private int sumAppliedQuantity(Long orderId, Long orderItemId) {
    List<Map<String, Object>> rows = afterSalesMapper.selectMaps(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AfterSalesEntity>()
            .select("COALESCE(SUM(quantity), 0) AS qty")
            .eq("order_id", orderId)
            .eq("order_item_id", orderItemId)
            .notIn("status", "REJECTED", "CANCELLED"));
    if (rows == null || rows.isEmpty()) return 0;
    Object qty = rows.get(0).get("qty");
    return qty == null ? 0 : new BigDecimal(qty.toString()).intValue();
  }

  /** 该商品行历史已申请售后的退款金额合计（口径同上） */
  private BigDecimal sumAppliedAmount(Long orderId, Long orderItemId) {
    List<Map<String, Object>> rows = afterSalesMapper.selectMaps(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AfterSalesEntity>()
            .select("COALESCE(SUM(amount), 0) AS amt")
            .eq("order_id", orderId)
            .eq("order_item_id", orderItemId)
            .notIn("status", "REJECTED", "CANCELLED"));
    if (rows == null || rows.isEmpty()) return BigDecimal.ZERO;
    Object amt = rows.get(0).get("amt");
    return amt == null ? BigDecimal.ZERO : new BigDecimal(amt.toString());
  }

  private BigDecimal remainingRefundable(OrderEntity order) {
    BigDecimal payAmount = order.getPayAmount();
    if (payAmount == null || payAmount.signum() <= 0) {
      throw new IllegalStateException("订单实付金额异常，暂不支持自助售后，请联系客服");
    }
    BigDecimal refunded = refundService.refundedAmount(order.getId());
    if (refunded == null) refunded = BigDecimal.ZERO;
    return payAmount.subtract(refunded).max(BigDecimal.ZERO);
  }

  private String resolveDeliveryType(String type, String deliveryType) {
    // 换货必须寄回，避免与原商品同时留存
    if ("EXCHANGE".equals(type)) {
      return "RETURN_SHIP";
    }
    if ("RETURN_REFUND".equals(type)) {
      return "RETURN_SHIP";
    }
    if (deliveryType != null && "RETURN_SHIP".equalsIgnoreCase(deliveryType.trim())) {
      return "RETURN_SHIP";
    }
    return "NO_RETURN";
  }

  private AfterSalesEntity requireOwned(Long id, Long userId) {
    AfterSalesEntity entity = afterSalesMapper.selectById(id);
    if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
      throw new IllegalArgumentException("售后申请不存在");
    }
    return entity;
  }

  private AfterSalesEntity requireEntity(Long id) {
    AfterSalesEntity entity = afterSalesMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("售后申请不存在");
    }
    return entity;
  }

  private String findOrderNo(Long orderId) {
    if (orderId == null) return null;
    OrderEntity order = orderMapper.selectById(orderId);
    return order == null ? null : order.getOrderNo();
  }

  private String generateAfterSaleNo() {
    return "AS" + LocalDateTime.now().format(DAY_FORMAT)
        + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }

  private IPage<AfterSalesVO> toVOPage(IPage<AfterSalesEntity> entityPage) {
    List<AfterSalesEntity> records = entityPage.getRecords();
    Map<Long, String> orderNoMap = new HashMap<>();
    List<Long> orderIds = records.stream()
        .map(AfterSalesEntity::getOrderId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (!orderIds.isEmpty()) {
      orderMapper.selectBatchIds(orderIds)
          .forEach(order -> orderNoMap.put(order.getId(), order.getOrderNo()));
    }
    return PageUtils.convertPage(entityPage,
        entity -> toVO(entity, orderNoMap.get(entity.getOrderId())));
  }

  private AfterSalesVO toVO(AfterSalesEntity entity, String orderNo) {
    AfterSalesVO vo = new AfterSalesVO();
    vo.setId(entity.getId());
    vo.setAfterSaleNo(entity.getAfterSaleNo());
    vo.setOrderId(entity.getOrderId());
    vo.setOrderNo(orderNo);
    vo.setOrderItemId(entity.getOrderItemId());
    vo.setSkuId(entity.getSkuId());
    vo.setProductId(entity.getProductId());
    vo.setProductName(entity.getProductName());
    vo.setSkuSpec(entity.getSkuSpec());
    vo.setMainImage(entity.getMainImage());
    vo.setQuantity(entity.getQuantity());
    String type = normalizeType(entity.getType());
    vo.setType(type);
    vo.setTypeLabel(typeLabel(type));
    vo.setDeliveryType(entity.getDeliveryType());
    vo.setReason(entity.getReason());
    vo.setAmount(entity.getAmount());
    vo.setDescription(entity.getDescription());
    vo.setImages(entity.getImages());
    vo.setStatus(entity.getStatus());
    vo.setStatusLabel(statusLabel(entity.getStatus(), type, entity.getDeliveryType()));
    vo.setReturnCompany(entity.getReturnCompany());
    vo.setReturnTrackingNo(entity.getReturnTrackingNo());
    vo.setReturnTime(entity.getReturnTime());
    vo.setReceiveTime(entity.getReceiveTime());
    vo.setRefundId(entity.getRefundId());
    vo.setRefundNo(entity.getRefundNo());
    vo.setRejectReason(entity.getRejectReason());
    vo.setRejectTime(entity.getRejectTime());
    vo.setCompleteTime(entity.getCompleteTime());
    vo.setRemark(entity.getRemark());
    vo.setCreateTime(entity.getCreateTime());

    int total = "REFUND_ONLY".equals(type) ? 3 : 5;
    vo.setProgressTotal(total);
    vo.setProgressIndex(progressIndex(type, entity.getStatus(), total));

    // 退货指引：仅在需要寄回且审核通过后下发，避免用户未审核就寄出
    if ("RETURN_SHIP".equals(entity.getDeliveryType())
        && Set.of("APPROVED", "RETURNING").contains(entity.getStatus())) {
      vo.setReturnInstructions(List.of(
          "请保持商品、配件及包装完好",
          "请于审核通过后 7 天内寄回并填写物流单号",
          "仓库收件地址请通过在线客服获取，寄回后请及时填写退货运单号"));
    } else {
      vo.setReturnInstructions(new ArrayList<>());
    }
    return vo;
  }

  private Integer progressIndex(String type, String status, int total) {
    if ("REJECTED".equals(status) || "CANCELLED".equals(status)) return -1;
    if ("REFUND_ONLY".equals(type)) {
      return switch (status) {
        case "PENDING" -> 0;
        case "APPROVED", "RECEIVED" -> 1;
        case "COMPLETED" -> 2;
        default -> 0;
      };
    }
    return switch (status) {
      case "PENDING" -> 0;
      case "APPROVED" -> 1;
      case "RETURNING" -> 2;
      case "RECEIVED" -> 3;
      case "COMPLETED" -> 4;
      default -> 0;
    };
  }

  private String typeLabel(String type) {
    return switch (type) {
      case "RETURN_REFUND" -> "退货退款";
      case "REFUND_ONLY" -> "仅退款";
      case "EXCHANGE" -> "换货";
      default -> type;
    };
  }

  /**
   * 状态文案需要结合类型/退货方式：
   * 仅退款审核通过后没有"寄回/收货"环节，统一展示"待退款"，避免误导用户寄回商品。
   */
  private String statusLabel(String status, String type, String deliveryType) {
    if (status == null) return "";
    return switch (status) {
      case "PENDING" -> "待审核";
      case "APPROVED" -> "RETURN_SHIP".equals(deliveryType) ? "待寄回" : "待退款";
      case "RETURNING" -> "已寄回待收货";
      case "RECEIVED" -> "REFUND_ONLY".equals(type) ? "待退款" : "已收货待处理";
      case "COMPLETED" -> "已完成";
      case "REJECTED" -> "已拒绝";
      case "CANCELLED" -> "已取消";
      default -> status;
    };
  }

  private Long toLong(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Long.valueOf(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
