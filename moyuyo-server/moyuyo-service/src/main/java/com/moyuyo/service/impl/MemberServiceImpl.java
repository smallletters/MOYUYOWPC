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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

  /** 积分有效期：12 个月 */
  private static final int POINTS_VALID_MONTHS = 12;

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
    vo.setMemberNo(generateMemberNo(userId));
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
    // 仅正向流水（积分发放）设置 12 月有效期；扣减/退款/兑换等流水不设
    if (changeValue > 0) {
      LocalDateTime expireTime = LocalDateTime.now().plusMonths(POINTS_VALID_MONTHS);
      log.setExpireTime(expireTime);
    }
    pointsLogMapper.insert(log);

    // 积分变动后自动重算会员等级 / 成长值，确保 mo_member.level 与总积分同步
    recalculateLevel(userId, newPoints);
  }

  /**
   * 根据当前积分计算会员等级并写库。
   * <p>
   * 等级阈值与 listLevels() 保持一致（共享 L1~L5 阶梯）。
   * 仅升不降：积分上涨时升级；积分减少时（如订单退款扣分）不主动降级，避免影响会员信任与已发放权益。
   * 同时把成长值同步为"用户当前积分"，便于后续按成长值排序 / 推送活动。
   */
  private void recalculateLevel(Long userId, int currentPoints) {
    MemberEntity member = ensureMember(userId);
    Level target = levelByPoints(currentPoints);
    if (member.getLevel() == null || levelRank(target) > levelRank(member.getLevel())) {
      log.info("Member level upgrade: userId={} {} -> {} (points={})",
          userId, member.getLevel(), target, currentPoints);
      member.setLevel(target);
    }
    member.setGrowthValue(currentPoints);
    memberMapper.updateById(member);
  }

  /** 等级阶梯：与 listLevels() 完全一致，禁止分散配置。 */
  private static Level levelByPoints(int points) {
    if (points >= 25000) return Level.DIAMOND;
    if (points >= 8000) return Level.PLATINUM;
    if (points >= 2000) return Level.GOLD;
    if (points >= 500) return Level.SILVER;
    return Level.NORMAL;
  }

  @Override
  @Transactional
  public void recalculateLevel(Long userId) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    recalculateLevel(userId, user.getPoints() != null ? user.getPoints() : 0);
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
  public List<Map<String, Object>> listPrivileges(Long userId) {
    // 基础特权对所有会员开放；进阶特权仅 L3+
    boolean isAdvanced = isAtLeast(userId, "GOLD");
    List<Map<String, Object>> list = new java.util.ArrayList<>();
    list.add(privilege("tag", "新品优先购", "新品提前48小时购买权"));
    list.add(privilege("star", "会员日特惠", "每月8号会员专属折扣"));
    list.add(privilege("package", "专属IP周边", "MOYUYO IP限定周边"));
    if (isAdvanced) {
      list.add(privilege("heart", "免费宠物体检", "年度一次基础体检（L3+ 专享）"));
    }
    return list;
  }

  /**
   * 根据 userId 确定性生成会员卡号：MY + 8 位数字区段 + 4 位数字校验位
   * 同 userId 永远生成同一卡号，无需落库
   * 例：userId = 200000000017 → MY·00000017·2177
   */
  private String generateMemberNo(Long userId) {
    if (userId == null) return "MY·00000000·0000";
    // 仅取 userId 低 8 位十进制数字作为区段，保证短小易读
    String seg = String.format("%08d", Math.abs(userId) % 100_000_000L);
    // 校验位：userId 各字节累加再取模，输出固定 4 位数字
    long check = Math.abs(userId);
    for (int i = 0; i < 4; i++) {
      check = (check / 10) + (check % 10);
    }
    String checkNum = String.format("%04d", (int) (check % 10_000L));
    return String.format("MY·%s·%s", seg, checkNum);
  }

  private boolean isAtLeast(Long userId, String minLevel) {
    MemberEntity m = ensureMember(userId);
    int cur = levelRank(m.getLevel());
    return cur >= levelRank(parseLevel(minLevel));
  }

  private MemberEntity.Level parseLevel(String s) {
    if (s == null) return MemberEntity.Level.NORMAL;
    try {
      return MemberEntity.Level.valueOf(s.toUpperCase());
    } catch (Exception e) {
      return MemberEntity.Level.NORMAL;
    }
  }

  private int levelRank(MemberEntity.Level lv) {
    if (lv == null) return 0;
    switch (lv) {
      case DIAMOND: return 4;
      case PLATINUM: return 3;
      case GOLD: return 2;
      case SILVER: return 1;
      case NORMAL:
      default: return 0;
    }
  }

  private Map<String, Object> privilege(String icon, String title, String desc) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("icon", icon);
    m.put("title", title);
    m.put("desc", desc);
    return m;
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
