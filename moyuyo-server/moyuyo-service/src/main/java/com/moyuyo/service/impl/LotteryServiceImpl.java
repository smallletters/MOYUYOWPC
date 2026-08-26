package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.LotteryEntity;
import com.moyuyo.dao.entity.LotteryRecordEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.LotteryMapper;
import com.moyuyo.dao.mapper.LotteryRecordMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.LotteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryServiceImpl implements LotteryService {

  private final LotteryMapper lotteryMapper;
  private final LotteryRecordMapper lotteryRecordMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;

  // 使用密码学安全的随机数生成器，避免 Random 的可预测性
  private static final Random SECURE_RANDOM = new SecureRandom();

  @Override
  public List<LotteryEntity> list() {
    return lotteryMapper.selectList(
        new LambdaQueryWrapper<LotteryEntity>()
            .eq(LotteryEntity::getActive, true)
            .orderByDesc(LotteryEntity::getCreateTime));
  }

  @Override
  @Transactional
  public LotteryRecordEntity spin(Long userId, Long lotteryId) {
    LotteryEntity lottery = lotteryMapper.selectById(lotteryId);
    if (lottery == null || !lottery.getActive()) {
      throw new IllegalArgumentException("抽奖活动不存在或已结束");
    }
    LocalDate today = LocalDate.now();
    long todayFree = lotteryRecordMapper.selectCount(
        new LambdaQueryWrapper<LotteryRecordEntity>()
            .eq(LotteryRecordEntity::getUserId, userId)
            .eq(LotteryRecordEntity::getLotteryId, lotteryId)
            .eq(LotteryRecordEntity::getUsedFreeSpin, true)
            .ge(LotteryRecordEntity::getCreateTime, LocalDateTime.of(today, LocalTime.MIN))
            .lt(LotteryRecordEntity::getCreateTime, LocalDateTime.of(today, LocalTime.MAX)));
    boolean usedFree = todayFree < lottery.getDailyFree();

    // 如果免费次数用完，需要从用户积分中扣除 pointsCost
    int pointsCost = usedFree ? 0 : (lottery.getPointsCost() == null ? 0 : lottery.getPointsCost());
    if (pointsCost > 0) {
      UserEntity user = userMapper.selectById(userId);
      if (user == null || user.getPoints() == null || user.getPoints() < pointsCost) {
        throw new IllegalStateException("积分不足，无法抽奖");
      }
      user.setPoints(user.getPoints() - pointsCost);
      userMapper.updateById(user);

      PointsLogEntity logEntity = new PointsLogEntity();
      logEntity.setUserId(userId);
      logEntity.setChangeValue(-pointsCost);
      logEntity.setType("SPEND");
      logEntity.setBizNo(String.valueOf(lotteryId));
      logEntity.setRemark("抽奖消耗积分：" + lottery.getName());
      pointsLogMapper.insert(logEntity);
    }

    LotteryRecordEntity record = new LotteryRecordEntity();
    record.setUserId(userId);
    record.setLotteryId(lotteryId);
    record.setUsedFreeSpin(usedFree);
    record.setPointsSpent(pointsCost);
    boolean won = lottery.draw(SECURE_RANDOM);
    record.setWon(won);
    record.setPrizeName(won ? lottery.getPrizeName() : "未中奖");
    lotteryRecordMapper.insert(record);

    // 中奖发放积分奖励（如果奖品名包含数字积分，如 "100积分"）
    if (won && lottery.getPrizeName() != null) {
      Integer prizePoints = parsePrizePoints(lottery.getPrizeName());
      if (prizePoints != null && prizePoints > 0) {
        UserEntity user = userMapper.selectById(userId);
        if (user != null) {
          user.setPoints(user.getPoints() + prizePoints);
          userMapper.updateById(user);
          PointsLogEntity prizeLog = new PointsLogEntity();
          prizeLog.setUserId(userId);
          prizeLog.setChangeValue(prizePoints);
          prizeLog.setType("EVENT");
          prizeLog.setBizNo(String.valueOf(lotteryId));
          prizeLog.setRemark("抽奖奖励：" + lottery.getPrizeName());
          pointsLogMapper.insert(prizeLog);
        }
      }
    }

    log.info("Lottery spin: userId={}, lotteryId={}, won={}, pointsSpent={}",
        userId, lotteryId, won, pointsCost);
      return record;
  }

  /**
   * 从奖品名解析积分数量，如 "100积分" → 100、"¥5 优惠券" → null
   */
  private Integer parsePrizePoints(String prizeName) {
    if (prizeName == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*积分").matcher(prizeName);
    return m.find() ? Integer.parseInt(m.group(1)) : null;
  }

  @Override
  public List<LotteryRecordEntity> getHistory(Long userId) {
    return lotteryRecordMapper.selectList(
        new LambdaQueryWrapper<LotteryRecordEntity>()
            .eq(LotteryRecordEntity::getUserId, userId)
            .orderByDesc(LotteryRecordEntity::getCreateTime));
  }

  @Override
  public Map<String, Object> getStats(Long userId) {
    Map<String, Object> stats = new HashMap<>();
    LocalDate today = LocalDate.now();
    long todayFreeUsed = lotteryRecordMapper.selectCount(
        new LambdaQueryWrapper<LotteryRecordEntity>()
            .eq(LotteryRecordEntity::getUserId, userId)
            .eq(LotteryRecordEntity::getUsedFreeSpin, true)
            .ge(LotteryRecordEntity::getCreateTime, LocalDateTime.of(today, LocalTime.MIN))
            .lt(LotteryRecordEntity::getCreateTime, LocalDateTime.of(today, LocalTime.MAX)));
    long totalSpins = lotteryRecordMapper.selectCount(
        new LambdaQueryWrapper<LotteryRecordEntity>()
            .eq(LotteryRecordEntity::getUserId, userId));
    long totalWins = lotteryRecordMapper.selectCount(
        new LambdaQueryWrapper<LotteryRecordEntity>()
            .eq(LotteryRecordEntity::getUserId, userId)
            .eq(LotteryRecordEntity::getWon, true));
    stats.put("freeSpins", todayFreeUsed);
    stats.put("totalSpins", totalSpins);
    stats.put("totalWins", totalWins);
    return stats;
  }
}
