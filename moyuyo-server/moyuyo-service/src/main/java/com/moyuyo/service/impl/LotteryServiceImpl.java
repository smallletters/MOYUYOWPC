package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.LotteryEntity;
import com.moyuyo.dao.entity.LotteryRecordEntity;
import com.moyuyo.dao.mapper.LotteryMapper;
import com.moyuyo.dao.mapper.LotteryRecordMapper;
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
    LotteryRecordEntity record = new LotteryRecordEntity();
    record.setUserId(userId);
    record.setLotteryId(lotteryId);
    record.setUsedFreeSpin(usedFree);
    record.setPointsSpent(usedFree ? 0 : lottery.getPointsCost());
    boolean won = lottery.draw(SECURE_RANDOM);
    record.setWon(won);
    record.setPrizeName(won ? lottery.getPrizeName() : "未中奖");
    lotteryRecordMapper.insert(record);
    log.info("Lottery spin: userId={}, lotteryId={}, won={}", userId, lotteryId, won);
    return record;
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
