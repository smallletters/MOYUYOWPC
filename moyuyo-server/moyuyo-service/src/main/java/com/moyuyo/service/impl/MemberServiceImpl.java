package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.member.MemberVO;
import com.moyuyo.common.dto.member.WalletVO;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.MemberEntity.Level;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.entity.WalletEntity;
import com.moyuyo.dao.entity.WalletEntity.Status;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.dao.mapper.WalletMapper;
import com.moyuyo.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

  private final MemberMapper memberMapper;
  private final UserMapper userMapper;
  private final WalletMapper walletMapper;
  private final PointsLogMapper pointsLogMapper;

  @Override
  public MemberVO getMemberInfo(Long userId) {
    MemberEntity member = ensureMember(userId);
    WalletEntity wallet = ensureWallet(userId);
    UserEntity user = userMapper.selectById(userId);

    MemberVO vo = new MemberVO();
    vo.setUserId(userId);
    vo.setLevel(member.getLevel().name());
    vo.setGrowthValue(member.getGrowthValue());
    vo.setPoints(user != null ? user.getPoints() : 0);
    vo.setBalance(wallet.getBalance());
    return vo;
  }

  @Override
  public Page<PointsLogEntity> getPointsLog(Long userId, int page, int size) {
    return pointsLogMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .orderByDesc(PointsLogEntity::getCreatedAt));
  }

  @Override
  @Transactional
  public void addPoints(Long userId, int changeValue, String type, String bizNo, String remark) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }

    int newPoints = user.getPoints() != null ? user.getPoints() + changeValue : changeValue;
    if (newPoints < 0) {
      throw new IllegalArgumentException("积分不足");
    }

    user.setPoints(newPoints);
    userMapper.updateById(user);

    PointsLogEntity log = new PointsLogEntity();
    log.setUserId(userId);
    log.setChangeValue(changeValue);
    log.setType(type);
    log.setBizNo(bizNo);
    log.setRemark(remark);
    pointsLogMapper.insert(log);
  }

  @Override
  public WalletVO getWallet(Long userId) {
    WalletEntity wallet = ensureWallet(userId);

    WalletVO vo = new WalletVO();
    vo.setUserId(userId);
    vo.setBalance(wallet.getBalance());
    vo.setTotalRecharged(wallet.getTotalRecharged());
    vo.setTotalSpent(wallet.getTotalSpent());
    vo.setStatus(wallet.getStatus().name());
    return vo;
  }

  @Override
  @Transactional
  public void spendPoints(Long userId, int changeValue, String bizNo, String remark) {
    addPoints(userId, -changeValue, "SPEND", bizNo, remark);
    log.info("Points spent: userId={}, amount={}, bizNo={}", userId, changeValue, bizNo);
  }

  @Override
  public int getPointsBalance(Long userId) {
    UserEntity user = userMapper.selectById(userId);
    return user != null && user.getPoints() != null ? user.getPoints() : 0;
  }

  @Override
  @Transactional
  public WalletVO recharge(Long userId, BigDecimal amount, String channel) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("充值金额必须大于零");
    }

    WalletEntity wallet = ensureWallet(userId);
    wallet.setBalance(wallet.getBalance().add(amount));
    wallet.setTotalRecharged(wallet.getTotalRecharged().add(amount));
    walletMapper.updateById(wallet);

    WalletVO vo = new WalletVO();
    vo.setUserId(userId);
    vo.setBalance(wallet.getBalance());
    vo.setTotalRecharged(wallet.getTotalRecharged());
    vo.setTotalSpent(wallet.getTotalSpent());
    vo.setStatus(wallet.getStatus().name());
    return vo;
  }

  @Override
  public List<Map<String, Object>> listLevels() {
    // 等级档位（按成长值门槛排序）
    List<Map<String, Object>> levels = new java.util.ArrayList<>();
    levels.add(level("L1", "Member", "注册即获得", 0, 1.0));
    levels.add(level("L2", "Silver", "完成首单 + 几次签到", 500, 1.1));
    levels.add(level("L3", "Gold", "活跃用户", 2000, 1.2));
    levels.add(level("L4", "Platinum", "高频消费用户", 8000, 1.5));
    levels.add(level("L5", "Black", "顶级 VIP", 25000, 2.0));
    return levels;
  }

  @Override
  public double getCurrentPointsRate(Long userId) {
    MemberEntity m = ensureMember(userId);
    String level = m.getLevel() == null ? "NORMAL" : m.getLevel().name();
    switch (level) {
      case "SILVER": return 1.1;
      case "GOLD": return 1.2;
      case "PLATINUM": return 1.5;
      case "DIAMOND": return 2.0;
      case "NORMAL":
      default:
        return 1.0;
    }
  }

  private Map<String, Object> level(String code, String name, String desc, int threshold, double rate) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("code", code);
    m.put("name", name);
    m.put("description", desc);
    m.put("growthThreshold", threshold);
    m.put("pointsRate", rate);
    return m;
  }

  private MemberEntity ensureMember(Long userId) {
    MemberEntity entity = memberMapper.selectOne(
        new LambdaQueryWrapper<MemberEntity>()
            .eq(MemberEntity::getUserId, userId));
    if (entity == null) {
      entity = new MemberEntity();
      entity.setUserId(userId);
      entity.setLevel(Level.NORMAL);
      entity.setGrowthValue(0);
      memberMapper.insert(entity);
    }
    return entity;
  }

  private WalletEntity ensureWallet(Long userId) {
    WalletEntity entity = walletMapper.selectOne(
        new LambdaQueryWrapper<WalletEntity>()
            .eq(WalletEntity::getUserId, userId));
    if (entity == null) {
      entity = new WalletEntity();
      entity.setUserId(userId);
      entity.setBalance(BigDecimal.ZERO);
      entity.setTotalRecharged(BigDecimal.ZERO);
      entity.setTotalSpent(BigDecimal.ZERO);
      entity.setStatus(Status.ACTIVE);
      walletMapper.insert(entity);
    }
    return entity;
  }
}
