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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
   * - 每月可免费补签 1 次
   * - 之后每次补签消耗 50 积分
   * - 补签成功后写入 CHECKIN 流水（type=CHECKIN），积分到账
   */
  @Override
  @Transactional
  public Map<String, Object> makeupCheckin(Long userId) {
    String ym = LocalDate.now().format(YM);

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

    // 写流水（CHECKIN +5）
    memberService.addPoints(userId, 5, "CHECKIN", "makeup:" + ym, "漏签补签 +5 积分");

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
    result.put("monthCount", currentCount + 1);
    log.info("Checkin makeup: userId={}, month={}, free={}, cost={}",
        userId, ym, free, cost);
    return result;
  }
}