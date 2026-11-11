package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PointsExchangeMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.admin.AdminPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 积分服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminPointsServiceImpl implements AdminPointsService {

  private final PointsLogMapper pointsLogMapper;
  private final UserMapper userMapper;
  private final MemberService memberService;
  /** 积分兑换记录：后台兑换记录页数据来源 */
  private final PointsExchangeMapper pointsExchangeMapper;
  /** 兑换生成的 0 元订单：用于展示订单号、订单状态与物流单号 */
  private final OrderMapper orderMapper;

  @Override
  public List<Map<String, Object>> listActivities() {
    // 从积分流水日志中聚合出活动数据
    List<PointsLogEntity> allLogs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .orderByDesc(PointsLogEntity::getCreatedAt));
    // 按类型分组聚合
    Map<String, List<PointsLogEntity>> grouped = new LinkedHashMap<>();
    for (PointsLogEntity log : allLogs) {
      String type = log.getType() != null ? log.getType() : "OTHER";
      grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(log);
    }
    List<Map<String, Object>> activities = new ArrayList<>();
    for (Map.Entry<String, List<PointsLogEntity>> entry : grouped.entrySet()) {
      List<PointsLogEntity> logs = entry.getValue();
      Map<String, Object> act = new LinkedHashMap<>();
      act.put("id", entry.getKey());
      act.put("name", getActivityName(entry.getKey()));
      act.put("type", entry.getKey());
      act.put("points", logs.stream().mapToLong(log -> log.getChangeValue() != null ? Math.abs(log.getChangeValue().longValue()) : 0L).sum());
      act.put("participantCount", (long) logs.stream().map(PointsLogEntity::getUserId).distinct().count());
      act.put("status", "ACTIVE");
      act.put("startTime", logs.get(logs.size() - 1).getCreatedAt());
      act.put("endTime", logs.get(0).getCreatedAt());
      act.put("count", logs.size());
      act.put("totalPoints", logs.stream().mapToLong(log -> log.getChangeValue() != null ? log.getChangeValue().longValue() : 0L).sum());
      act.put("lastTime", logs.get(0).getCreatedAt());
      activities.add(act);
    }
    return activities;
  }

  /** 根据类型返回活动名称 */
  private String getActivityName(String type) {
    return switch (type) {
      case "SIGN_IN" -> "每日签到";
      case "PURCHASE" -> "购物返积分";
      case "INVITE" -> "邀请奖励";
      case "EVENT" -> "活动奖励";
      default -> "其他活动";
    };
  }

  @Override
  public void createActivity(Map<String, Object> data) {
    // 创建积分活动：记录积分流水
    String name = (String) data.get("name");
    String type = (String) data.getOrDefault("type", "ACTIVITY");
    Object pointsObj = data.get("points");
    int points = pointsObj instanceof Number ? ((Number) pointsObj).intValue() : 0;
    if (points <= 0) {
      return;
    }
    // 使用 positive 值表示赠送积分
    PointsLogEntity log = new PointsLogEntity();
    log.setUserId(data.get("userId") != null ? ((Number) data.get("userId")).longValue() : 0L);
    log.setChangeValue(points);
    log.setType(type);
    String remark = data.get("name") != null ? (String) data.get("name") : "";
    if (data.get("startTime") != null) remark += " | " + data.get("startTime");
    if (data.get("endTime") != null) remark += " ~ " + data.get("endTime");
    log.setRemark(remark);
    pointsLogMapper.insert(log);
  }

  @Override
  public void updateActivity(String id, Map<String, Object> data) {
    // 按活动type(id)查找所有关联记录并更新备注等信息
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getType, id));
    if (logs.isEmpty()) {
      return;
    }
    String newName = (String) data.get("name");
    String newRemark = newName != null ? newName : "";
    if (data.get("startTime") != null) newRemark += " | " + data.get("startTime");
    if (data.get("endTime") != null) newRemark += " ~ " + data.get("endTime");
    for (PointsLogEntity log : logs) {
      log.setRemark(newRemark);
      pointsLogMapper.updateById(log);
    }
  }

  @Override
  public void deleteActivity(String type) {
    // 按类型删除对应的积分流水记录
    LambdaQueryWrapper<PointsLogEntity> wrapper = new LambdaQueryWrapper<PointsLogEntity>()
        .eq(PointsLogEntity::getType, type);
    pointsLogMapper.delete(wrapper);
  }

  @Override
  public Map<String, Object> listLogs(int page, int size, Long userId) {
    LambdaQueryWrapper<PointsLogEntity> wrapper = new LambdaQueryWrapper<PointsLogEntity>()
        .orderByDesc(PointsLogEntity::getCreatedAt);
    if (userId != null) {
      wrapper.eq(PointsLogEntity::getUserId, userId);
    }
    Page<PointsLogEntity> pageResult = pointsLogMapper.selectPage(new Page<>(page, size), wrapper);

    List<Map<String, Object>> list = new ArrayList<>();
    // 同一用户在当前页可能有多条流水，按 userId 缓存余额，避免重复查询
    Map<Long, Integer> balanceCache = new HashMap<>();
    for (PointsLogEntity log : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", log.getId());
      item.put("userId", log.getUserId());
      item.put("points", Math.abs(log.getChangeValue()));
      item.put("changeType", log.getChangeValue() >= 0 ? "EARN" : "SPEND");
      // 余额取用户当前可用积分，缺失用户 ID 时返回 0
      Integer balance = log.getUserId() == null
          ? 0
          : balanceCache.computeIfAbsent(log.getUserId(), memberService::getPointsBalance);
      item.put("balance", balance);
      item.put("type", log.getType());
      item.put("description", log.getRemark());
      item.put("remark", log.getRemark());
      item.put("createTime", log.getCreatedAt());
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return result;
  }

  @Override
  public Map<String, Object> listExchanges(int page, int size, String orderStatus, Long userId) {
    LambdaQueryWrapper<PointsExchangeEntity> wrapper = new LambdaQueryWrapper<PointsExchangeEntity>()
        .orderByDesc(PointsExchangeEntity::getCreateTime);
    if (userId != null) {
      wrapper.eq(PointsExchangeEntity::getUserId, userId);
    }
    // 履约状态以订单为准：用带参数的子查询直接在库内过滤，
    // 避免「先把匹配订单 ID 全查回应用层再拼 IN」导致订单量大时内存与 SQL 长度膨胀
    if (orderStatus != null && !orderStatus.isBlank()) {
      wrapper.apply(
          "order_id IN (SELECT id FROM mo_order WHERE order_type = 'POINTS' AND status = {0})",
          orderStatus);
    }
    Page<PointsExchangeEntity> pageResult =
        pointsExchangeMapper.selectPage(new Page<>(page, size), wrapper);
    List<PointsExchangeEntity> records = pageResult.getRecords();

    // 批量查询用户与订单，避免逐条查询造成 N+1
    Set<Long> userIds = records.stream()
        .map(PointsExchangeEntity::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, UserEntity> userMap = userIds.isEmpty() ? Map.of()
        : userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));

    Set<Long> orderIds = records.stream()
        .map(PointsExchangeEntity::getOrderId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, OrderEntity> orderMap = orderIds.isEmpty() ? Map.of()
        : orderMapper.selectBatchIds(orderIds).stream()
            .collect(Collectors.toMap(OrderEntity::getId, o -> o));

    List<Map<String, Object>> list = new ArrayList<>();
    for (PointsExchangeEntity exchange : records) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", exchange.getId());
      item.put("userId", exchange.getUserId());
      UserEntity user = userMap.get(exchange.getUserId());
      String nickName = user == null ? null : user.getNickname();
      item.put("userName", nickName != null && !nickName.isBlank()
          ? nickName : (user == null ? null : user.getEmail()));
      item.put("userEmail", user == null ? null : user.getEmail());
      item.put("goodsId", exchange.getGoodsId());
      item.put("productId", exchange.getProductId());
      item.put("goodsName", exchange.getGoodsName());
      item.put("pointsCost", exchange.getPointsCost());

      OrderEntity order = exchange.getOrderId() == null ? null : orderMap.get(exchange.getOrderId());
      item.put("orderId", exchange.getOrderId());
      item.put("orderNo", order == null ? null : order.getOrderNo());
      item.put("orderStatus", order == null ? null : order.getStatus());
      // 物流单号优先取订单（发货后由订单维护），历史兑换记录回退自身字段
      item.put("trackingNo", order != null && order.getTrackingNumber() != null
          ? order.getTrackingNumber() : exchange.getTrackingNo());

      item.put("receiverName", exchange.getReceiverName());
      item.put("receiverPhone", exchange.getReceiverPhone());
      item.put("receiverAddress", exchange.getReceiverAddress());
      item.put("status", exchange.getStatus());
      item.put("createTime", exchange.getCreateTime());
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return result;
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    long totalLogs = pointsLogMapper.selectCount(new LambdaQueryWrapper<>());

    // 统计积分发放（正值为获取）
    List<PointsLogEntity> allLogs = pointsLogMapper.selectList(new LambdaQueryWrapper<>());
    long totalEarned = allLogs.stream()
        .filter(l -> l.getChangeValue() != null && l.getChangeValue() > 0)
        .mapToLong(PointsLogEntity::getChangeValue).sum();
    long totalSpent = allLogs.stream()
        .filter(l -> l.getChangeValue() != null && l.getChangeValue() < 0)
        .mapToLong(l -> Math.abs(l.getChangeValue())).sum();
    // 活跃活动数 = 不同type的数量
    long activeTypes = allLogs.stream()
        .map(PointsLogEntity::getType).filter(t -> t != null).distinct().count();
    // 参与用户数
    long totalUsers = allLogs.stream()
        .map(PointsLogEntity::getUserId).distinct().count();
    // 已兑换次数：消耗积分的流水条数
    long exchangedCount = allLogs.stream()
        .filter(l -> l.getChangeValue() != null && l.getChangeValue() < 0)
        .count();
    // 积分价值：系统按 100 积分 = 1 货币单位抵扣
    BigDecimal pointValue = BigDecimal.valueOf(totalSpent)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

    // 与前端字段名对齐
    stats.put("totalIssued", totalEarned);
    stats.put("totalConsumed", totalSpent);
    stats.put("activeActivities", activeTypes);
    stats.put("totalUsers", totalUsers);
    // 营销管理页所需字段
    stats.put("activeCount", activeTypes);
    stats.put("exchangedCount", exchangedCount);
    stats.put("pointValue", pointValue);
    // 保留后端旧字段名以兼容
    stats.put("totalLogs", totalLogs);
    stats.put("totalEarned", totalEarned);
    stats.put("totalSpent", totalSpent);
    stats.put("balance", totalEarned - totalSpent);
    return stats;
  }

  @Override
  public Map<String, Object> getUserPoints(Long userId) {
    // 查询用户所有积分流水
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .orderByDesc(PointsLogEntity::getCreatedAt));

    // 计算余额：所有 changeValue 相加
    int balance = logs.stream()
        .mapToInt(log -> log.getChangeValue() != null ? log.getChangeValue() : 0)
        .sum();

    // 构建历史记录列表
    List<Map<String, Object>> records = new ArrayList<>();
    for (PointsLogEntity log : logs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", log.getId());
      item.put("userId", log.getUserId());
      item.put("changeValue", log.getChangeValue());
      item.put("type", log.getType());
      item.put("remark", log.getRemark());
      item.put("createdAt", log.getCreatedAt());
      records.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userId", userId);
    result.put("balance", balance);
    result.put("records", records);
    return result;
  }

  @Override
  @Transactional
  public void adjustPoints(Long userId, int amount, String reason) {
    // 1. 同步更新 mo_user.points（管理员手动调整后必须即时生效，否则 C 端积分展示与管理后台不一致）
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    int current = user.getPoints() != null ? user.getPoints() : 0;
    int newPoints = current + amount;
    if (newPoints < 0) {
      throw new IllegalArgumentException("调整后积分不能小于 0（当前 " + current + "，调整 " + amount + "）");
    }
    user.setPoints(newPoints);
    userMapper.updateById(user);

    // 2. 写一条 ADJUST 类型流水，供后续对账与审计
    PointsLogEntity log = new PointsLogEntity();
    log.setUserId(userId);
    log.setChangeValue(amount);
    log.setType("ADJUST");
    log.setRemark(reason);
    pointsLogMapper.insert(log);

    // 3. 重算会员等级（手动调高积分后必须自动升级，否则 mo_member.level 永远停留在 NORMAL）
    memberService.recalculateLevel(userId);
  }
}
