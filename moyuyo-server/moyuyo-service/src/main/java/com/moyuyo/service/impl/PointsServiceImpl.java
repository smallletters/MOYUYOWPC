package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.CheckinMakeupEntity;
import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsGoodsEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.CheckinMakeupMapper;
import com.moyuyo.dao.mapper.PointsExchangeMapper;
import com.moyuyo.dao.mapper.PointsGoodsMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

  /** 章节 2.1：补签从第 2 次起每次消耗 50 积分 */
  private static final int MAKEUP_POINTS_COST = 50;

  private final PointsGoodsMapper pointsGoodsMapper;
  private final PointsExchangeMapper pointsExchangeMapper;
  private final CheckinMakeupMapper checkinMakeupMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;
  private final MemberService memberService;

  @Override
  public List<PointsGoodsEntity> listGoods(String category) {
    LambdaQueryWrapper<PointsGoodsEntity> q = new LambdaQueryWrapper<PointsGoodsEntity>()
        .eq(PointsGoodsEntity::getStatus, 1)
        .orderByAsc(PointsGoodsEntity::getSortOrder);
    if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
      q.eq(PointsGoodsEntity::getCategory, category);
    }
    return pointsGoodsMapper.selectList(q);
  }

  /**
   * 章节 3.2：兑换礼品。
   * 1. 校验商品上下架、库存
   * 2. 校验积分余额
   * 3. 扣减积分（MemberService.addPoints 走 SPEND 流水）
   * 4. 扣减库存 + 累计兑换数
   * 5. 写兑换记录
   */
  @Override
  @Transactional
  public PointsExchangeEntity exchange(Long userId, Long goodsId, String receiverName,
      String receiverPhone, String receiverAddress) {
    PointsGoodsEntity goods = pointsGoodsMapper.selectById(goodsId);
    if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
      throw new IllegalArgumentException("礼品不存在或已下架");
    }
    if (goods.getStock() != null && goods.getStock() >= 0 && goods.getStock() <= 0) {
      throw new IllegalStateException("库存不足");
    }
    int cost = goods.getPoints() == null ? 0 : goods.getPoints();
    int balance = memberService.getPointsBalance(userId);
    if (balance < cost) {
      throw new IllegalStateException("积分不足，无法兑换");
    }

    // 实物礼品需要地址
    boolean needAddr = Boolean.TRUE.equals(goods.getNeedAddress());
    if (needAddr) {
      if (receiverName == null || receiverName.isBlank()
          || receiverPhone == null || receiverPhone.isBlank()
          || receiverAddress == null || receiverAddress.isBlank()) {
        throw new IllegalArgumentException("实物礼品需填写收货地址");
      }
    }

    // 扣减积分（流水类型 EXCHANGE / SPEND）
    memberService.addPoints(userId, -cost, "EXCHANGE", String.valueOf(goodsId),
        "兑换礼品：" + goods.getName());

    // 扣减库存 + 累计兑换数
    if (goods.getStock() != null && goods.getStock() > 0) {
      pointsGoodsMapper.update(null,
          new LambdaUpdateWrapper<PointsGoodsEntity>()
              .eq(PointsGoodsEntity::getId, goodsId)
              .setSql("stock = stock - 1, total_exchanged = total_exchanged + 1"));
    } else {
      pointsGoodsMapper.update(null,
          new LambdaUpdateWrapper<PointsGoodsEntity>()
              .eq(PointsGoodsEntity::getId, goodsId)
              .setSql("total_exchanged = total_exchanged + 1"));
    }

    // 写兑换记录
    PointsExchangeEntity exchange = new PointsExchangeEntity();
    exchange.setUserId(userId);
    exchange.setGoodsId(goodsId);
    exchange.setGoodsName(goods.getName());
    exchange.setPointsCost(cost);
    exchange.setReceiverName(needAddr ? receiverName : null);
    exchange.setReceiverPhone(needAddr ? receiverPhone : null);
    exchange.setReceiverAddress(needAddr ? receiverAddress : null);
    exchange.setStatus("PENDING");
    pointsExchangeMapper.insert(exchange);

    log.info("Points exchange: userId={}, goodsId={}, cost={}", userId, goodsId, cost);
    return exchange;
  }

  @Override
  public List<PointsExchangeEntity> listMyExchanges(Long userId) {
    return pointsExchangeMapper.selectList(
        new LambdaQueryWrapper<PointsExchangeEntity>()
            .eq(PointsExchangeEntity::getUserId, userId)
            .orderByDesc(PointsExchangeEntity::getCreateTime));
  }

  /**
   * 章节 2.1：漏签补签规则
   * - 只能补"当月、今天之前"且当天没有签到流水的漏签日；date 为空时自动取当月最近的漏签日
   * - 每月可免费补签 1 次，之后每次补签消耗 50 积分
   * - 补签成功后写入 CHECKIN 流水，时间回填到被补签那一天（而不是操作当天），
   *   这样才真正修复连续签到：操作当天仍可正常签到，连续天数也能接上
   * - 补签不推进任务中心进度：被补日期可能落在上一周，推进"累计签到 5 天"会造成跨周口径混乱
   */
  @Override
  @Transactional
  public Map<String, Object> makeupCheckin(Long userId, LocalDate date) {
    LocalDate today = LocalDate.now();
    LocalDate target = date != null ? date : latestMissedDay(userId, today);

    if (target == null) {
      throw new IllegalStateException("当月没有可补签的漏签日");
    }
    if (!target.isBefore(today)) {
      throw new IllegalStateException("只能补签今天之前的日期，今天请直接签到");
    }
    if (target.getYear() != today.getYear() || target.getMonthValue() != today.getMonthValue()) {
      throw new IllegalStateException("只能补签当月的漏签日期");
    }
    // 取 [target, today] 窗口的签到日期集合，用于判断该日期是否已签到
    int span = (int) (today.toEpochDay() - target.toEpochDay()) + 1;
    if (memberService.getRecentCheckinDates(userId, span).contains(target)) {
      throw new IllegalStateException("该日期已签到，无需补签");
    }

    String ym = today.format(YM);

    CheckinMakeupEntity record = checkinMakeupMapper.selectOne(
        new LambdaQueryWrapper<CheckinMakeupEntity>()
            .eq(CheckinMakeupEntity::getUserId, userId)
            .eq(CheckinMakeupEntity::getYmonth, ym));

    int currentCount = record == null ? 0 : (record.getCount() == null ? 0 : record.getCount());
    boolean free = currentCount == 0;
    int cost = free ? 0 : MAKEUP_POINTS_COST;

    if (cost > 0) {
      int balance = memberService.getPointsBalance(userId);
      if (balance < cost) {
        throw new IllegalStateException("积分不足，无法补签");
      }
      memberService.spendPoints(userId, cost, ym, "漏签补签消耗积分");
    }

    // 写流水（CHECKIN +5），created_at 回填到被补签那天（沿用当前时刻，便于流水列表展示）
    memberService.addPointsAt(userId, 5, "CHECKIN", "makeup:" + target,
        "漏签补签 +5 积分（" + target + "）", target.atTime(LocalTime.now()));

    // 累加当月补签次数
    if (record == null) {
      CheckinMakeupEntity newRec = new CheckinMakeupEntity();
      newRec.setUserId(userId);
      newRec.setYmonth(ym);
      newRec.setCount(1);
      checkinMakeupMapper.insert(newRec);
    } else {
      record.setCount(currentCount + 1);
      checkinMakeupMapper.updateById(record);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("points", 5);
    result.put("cost", cost);
    result.put("free", free);
    result.put("makeupDate", target.toString());
    result.put("monthCount", currentCount + 1);
    log.info("Checkin makeup: userId={}, date={}, month={}, free={}, cost={}",
        userId, target, ym, free, cost);
    return result;
  }

  /**
   * 章节 2.1：签到日历数据（指定月份的已签到日期 + 当前连续签到天数）。
   * 前端直接按 dates 渲染日历，不再用"最近 N 条积分流水"自行推算。
   */
  @Override
  public Map<String, Object> getCheckinCalendar(Long userId, YearMonth month) {
    LocalDate today = LocalDate.now();
    YearMonth target = month != null ? month : YearMonth.from(today);
    List<LocalDate> dates = memberService.getCheckinDatesOfMonth(userId, target);

    Map<String, Object> result = new HashMap<>();
    result.put("month", target.toString());
    result.put("dates", dates.stream().map(LocalDate::toString).collect(Collectors.toList()));
    result.put("checkedToday", dates.contains(today));
    result.put("streak", memberService.getCurrentCheckinStreak(userId));
    return result;
  }

  /**
   * 取当月最近的一个漏签日（今天之前、且当天没有签到流水），没有则返回 null。
   * 只回溯到当月 1 号，与"每月免费 1 次"的计数口径保持一致。
   */
  private LocalDate latestMissedDay(Long userId, LocalDate today) {
    // days=今天是几号 → 窗口正好是 [当月 1 号, 今天]
    Set<LocalDate> checkinDates = memberService.getRecentCheckinDates(userId, today.getDayOfMonth());
    LocalDate firstOfMonth = today.withDayOfMonth(1);
    LocalDate cursor = today.minusDays(1);
    while (!cursor.isBefore(firstOfMonth)) {
      if (!checkinDates.contains(cursor)) {
        return cursor;
      }
      cursor = cursor.minusDays(1);
    }
    return null;
  }
}