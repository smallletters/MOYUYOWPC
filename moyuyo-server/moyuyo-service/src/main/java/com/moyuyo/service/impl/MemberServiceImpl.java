package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.constant.MembershipRuleConstants;
import com.moyuyo.common.dto.member.MemberVO;
import com.moyuyo.common.dto.member.WalletVO;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.MemberEntity.Level;
import com.moyuyo.dao.entity.MemberNoSeqEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.entity.WalletEntity;
import com.moyuyo.dao.entity.WalletEntity.Status;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.dao.mapper.WalletMapper;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.config.MembershipRuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT null 静态分析差异（LambdaQueryWrapper 的 Function<T,R> 方法引用）
@SuppressWarnings("null")
public class MemberServiceImpl implements MemberService {

  /** 积分有效期：12 个月 */
  private static final int POINTS_VALID_MONTHS = MembershipRuleConstants.POINTS_VALID_MONTHS;

  /** 配置项前缀：会员等级积分倍率（points.level_rate.NORMAL / SILVER / GOLD / PLATINUM / DIAMOND） */
  public static final String CONFIG_LEVEL_RATE_PREFIX = "points.level_rate.";

  /** 五档会员积分倍率默认值（后台未配置时使用） */
  private static final Map<Level, Double> DEFAULT_LEVEL_RATES = new EnumMap<>(Level.class);
  static {
    DEFAULT_LEVEL_RATES.put(Level.NORMAL, 1.0);
    DEFAULT_LEVEL_RATES.put(Level.SILVER, 1.1);
    DEFAULT_LEVEL_RATES.put(Level.GOLD, 1.2);
    DEFAULT_LEVEL_RATES.put(Level.PLATINUM, 1.5);
    DEFAULT_LEVEL_RATES.put(Level.DIAMOND, 2.0);
  }

  private final MemberMapper memberMapper;
  private final UserMapper userMapper;
  private final WalletMapper walletMapper;
  private final PointsLogMapper pointsLogMapper;
  private final SystemConfigMapper systemConfigMapper;
  /** 会员规则配置（签到/评价/邀请/补签/抵扣，后台可调） */
  private final MembershipRuleConfigService ruleConfigService;

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
    vo.setMemberNo(getOrAssignMemberNo(userId));
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
  public Set<LocalDate> getRecentCheckinDates(Long userId, int days) {
    if (userId == null || days <= 0) {
      return Collections.emptySet();
    }
    // 含今天：days=7 表示 [今天-6, 今天]
    LocalDate from = LocalDate.now().minusDays(days - 1L);
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .eq(PointsLogEntity::getType, "CHECKIN")
            .ge(PointsLogEntity::getCreatedAt, from.atStartOfDay()));
    Set<LocalDate> dates = new HashSet<>();
    for (PointsLogEntity l : logs) {
      if (l.getCreatedAt() != null) {
        // 同一天多次签到只算一天
        dates.add(l.getCreatedAt().toLocalDate());
      }
    }
    return dates;
  }

  @Override
  public List<LocalDate> getCheckinDatesOfMonth(Long userId, YearMonth month) {
    if (userId == null || month == null) {
      return Collections.emptyList();
    }
    // [当月 1 号 00:00, 次月 1 号 00:00) 左闭右开，避免月末最后一秒的边界问题
    LocalDateTime from = month.atDay(1).atStartOfDay();
    LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .eq(PointsLogEntity::getType, "CHECKIN")
            .ge(PointsLogEntity::getCreatedAt, from)
            .lt(PointsLogEntity::getCreatedAt, to));
    return logs.stream()
        .map(PointsLogEntity::getCreatedAt)
        .filter(Objects::nonNull)
        .map(LocalDateTime::toLocalDate)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  @Override
  public int getCurrentCheckinStreak(Long userId) {
    if (userId == null) {
      return 0;
    }
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .eq(PointsLogEntity::getType, "CHECKIN"));
    Set<LocalDate> dates = new HashSet<>();
    for (PointsLogEntity l : logs) {
      if (l.getCreatedAt() != null) {
        dates.add(l.getCreatedAt().toLocalDate());
      }
    }
    int streak = 0;
    LocalDate cursor = LocalDate.now();
    while (dates.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  @Override
  @Transactional
  public void addPoints(Long userId, int changeValue, String type, String bizNo, String remark) {
    addPointsAt(userId, changeValue, type, bizNo, remark, LocalDateTime.now());
  }

  @Override
  @Transactional
  public void addPointsAt(Long userId, int changeValue, String type, String bizNo, String remark,
      LocalDateTime createdAt) {
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

    LocalDateTime base = createdAt != null ? createdAt : LocalDateTime.now();
    // 补签等场景需要回填流水时间。createdAt 上是 FieldFill.INSERT，
    // 而 MyMetaObjectHandler 用的是 strictInsertFill（仅字段为空时填充），因此显式赋值不会被覆盖
    insertPointsLog(userId, changeValue, type, bizNo, remark, createdAt, base);

    // 积分变动后自动重算会员等级 / 成长值，确保 mo_member.level 与总积分同步
    recalculateLevel(userId, newPoints);
  }

  /**
   * 写入一条积分流水（只落 mo_points_log，不动余额、不重算等级）。
   * <p>
   * 供 addPointsAt（正负向余额变动）与冻结/解冻/冻结转实扣复用：
   * - 仅正向流水（积分发放）设置 12 月有效期，扣减/冻结/解冻等流水不设；
   * - createdAt 非空时显式回填流水时间（strictInsertFill 不会覆盖非空值）。
   *
   * @param createdAt 流水时间；为空则由 MyBatis-Plus 自动填充为当前时间
   * @param base      用于计算有效期的基准时间（通常 = createdAt ?: now），不可为空
   */
  private void insertPointsLog(Long userId, int changeValue, String type, String bizNo,
      String remark, LocalDateTime createdAt, LocalDateTime base) {
    PointsLogEntity log = new PointsLogEntity();
    log.setUserId(userId);
    log.setChangeValue(changeValue);
    log.setType(type);
    log.setBizNo(bizNo);
    log.setRemark(remark);
    if (createdAt != null) {
      log.setCreatedAt(createdAt);
    }
    if (changeValue > 0) {
      log.setExpireTime(base.plusMonths(POINTS_VALID_MONTHS));
    }
    pointsLogMapper.insert(log);
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
    if (user == null) {
      return 0;
    }
    int total = user.getPoints() != null ? user.getPoints() : 0;
    int frozen = user.getPointsFrozen() != null ? user.getPointsFrozen() : 0;
    // 可用积分 = 总积分 - 冻结(待付款订单占用)。上限 0，避免脏数据导致负数展示
    return Math.max(0, total - frozen);
  }

  @Override
  @Transactional
  public boolean freezePoints(Long userId, int points, String bizNo, String remark) {
    if (points <= 0) {
      return true;
    }
    // 原子条件更新：仅当「可用 = points - points_frozen >= points」时才冻结。
    // 用 affected rows 判定而非"先 SELECT 再判断"，杜绝两笔待付款订单并发占用同一批积分的竞态。
    int updated = userMapper.update(null,
        new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .apply("points - points_frozen >= {0}", points)
            .setSql("points_frozen = points_frozen + " + points));
    if (updated == 0) {
      log.warn("[points] 冻结失败(可用积分不足): userId={}, points={}", userId, points);
      return false;
    }
    // 冻结不改变总积分，写一条 0 变动流水留痕（沿用 addPoints 体系；不影响等级/成长值）
    insertPointsLog(userId, 0, "FREEZE", bizNo, remark, null, LocalDateTime.now());
    log.info("[points] 冻结成功: userId={}, points={}, bizNo={}", userId, points, bizNo);
    return true;
  }

  @Override
  @Transactional
  public boolean unfreezePoints(Long userId, int points, String bizNo, String remark) {
    if (points <= 0) {
      return true;
    }
    // 原子条件更新：仅当确有足够冻结(points_frozen >= points)时才回退。
    // 幂等：存量订单/重复取消命中 0 行 → 返回 false，不报错、不误还。
    int updated = userMapper.update(null,
        new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .apply("points_frozen >= {0}", points)
            .setSql("points_frozen = points_frozen - " + points));
    if (updated == 0) {
      log.warn("[points] 解冻跳过(无对应冻结): userId={}, points={}", userId, points);
      return false;
    }
    insertPointsLog(userId, 0, "UNFREEZE", bizNo, remark, null, LocalDateTime.now());
    log.info("[points] 解冻成功: userId={}, points={}, bizNo={}", userId, points, bizNo);
    return true;
  }

  @Override
  @Transactional
  public boolean consumeFrozenPoints(Long userId, int points, String bizNo, String remark) {
    if (points <= 0) {
      return true;
    }
    // 冻结转实扣：同一个 UPDATE 内同时扣总积分与冻结额，
    // 避免"先 unfreeze 再 spendPoints"两步之间的并发窗口造成重复扣减/漏扣。
    int updated = userMapper.update(null,
        new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .apply("points_frozen >= {0}", points)
            .apply("points >= {0}", points)
            .setSql("points = points - " + points + ", points_frozen = points_frozen - " + points));
    if (updated == 0) {
      log.warn("[points] 冻结转实扣失败(冻结/余额不足): userId={}, points={}", userId, points);
      return false;
    }
    // 扣减流水（SPEND，负向）用于用户侧"积分明细"留痕
    insertPointsLog(userId, -points, "SPEND", bizNo, remark, null, LocalDateTime.now());
    // 总积分已变，按总积分同步会员等级/成长值（仅升不降，扣分不会掉级）
    recalculateLevel(userId);
    log.info("[points] 冻结转实扣成功: userId={}, points={}, bizNo={}", userId, points, bizNo);
    return true;
  }

  @Override
  @Transactional
  public boolean spendAvailablePoints(Long userId, int points, String type, String bizNo, String remark) {
    if (points <= 0) {
      return true;
    }
    // 原子条件更新：仅当「可用 = points - points_frozen >= points」时才扣减，
    // 用 affected rows 判定，避免两笔兑换并发时都通过"先查余额再扣"的校验造成超兑。
    int updated = userMapper.update(null,
        new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .apply("points - points_frozen >= {0}", points)
            .setSql("points = points - " + points));
    if (updated == 0) {
      log.warn("[points] 可用积分扣减失败(余额不足): userId={}, points={}", userId, points);
      return false;
    }
    // 扣减流水（调用方指定类型，如 EXCHANGE），用于用户侧"积分明细"留痕
    insertPointsLog(userId, -points, type, bizNo, remark, null, LocalDateTime.now());
    // 总积分已变，按总积分同步会员等级/成长值（仅升不降，扣分不会掉级）
    recalculateLevel(userId);
    log.info("[points] 可用积分扣减成功: userId={}, points={}, type={}, bizNo={}",
        userId, points, type, bizNo);
    return true;
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
    // 基础特权对所有会员开放；进阶特权仅 L5+
    boolean isAdvanced = isAtLeast(userId, "DIAMOND");
    List<Map<String, Object>> list = new java.util.ArrayList<>();
    list.add(privilege("tag", "新品优先购", "新品提前48小时购买权"));
    list.add(privilege("star", "会员日特惠", "每月8号会员专属折扣"));
    list.add(privilege("package", "专属IP周边", "MOYUYO IP限定周边"));
    if (isAdvanced) {
      list.add(privilege("heart", "免费宠物体检", "年度一次基础体检（L5+ 专享）"));
    }
    return list;
  }

  /**
   * 会员卡号：取自增序列的全局递增 id，12 位前导零格式（000000000001、000000000002…）。
   * 数字自然递增、全局唯一；首次生成后写回 mo_member.member_no 永久固定。
   */
  @Override
  public String getOrAssignMemberNo(Long userId) {
    MemberEntity entity = ensureMember(userId);
    if (entity.getMemberNo() != null && !entity.getMemberNo().isBlank()) {
      return entity.getMemberNo();
    }
    for (int attempt = 0; attempt < 5; attempt++) {
      // 领取下一个自增号（DB 生成，天然唯一、无撞号）
      MemberNoSeqEntity seq = new MemberNoSeqEntity();
      memberMapper.insertSeq(seq);
      if (seq.getId() == null) {
        throw new IllegalStateException("会员卡号分配失败，请稍后重试");
      }
      String no = String.format("%012d", seq.getId());
      // 仅当仍为空时写回；同用户并发时后者改为读取已分配号码
      LambdaUpdateWrapper<MemberEntity> uw = new LambdaUpdateWrapper<>();
      uw.eq(MemberEntity::getId, entity.getId())
          .isNull(MemberEntity::getMemberNo)
          .set(MemberEntity::getMemberNo, no);
      int updated;
      try {
        updated = memberMapper.update(null, uw);
      } catch (org.springframework.dao.DuplicateKeyException e) {
        // 理论上自增号不会冲突，此处仅防御性兜底
        updated = 0;
      }
      if (updated == 1) {
        entity.setMemberNo(no);
        return no;
      }
      // 并发下其他请求已分配：重读返回
      MemberEntity fresh = memberMapper.selectById(entity.getId());
      if (fresh != null && fresh.getMemberNo() != null && !fresh.getMemberNo().isBlank()) {
        return fresh.getMemberNo();
      }
    }
    throw new IllegalStateException("会员卡号分配失败，请稍后重试");
  }

  /**
   * 会员规则聚合：等级档位 + 积分获取/使用规则，供 C 端会员规则页直接渲染，
   * 数值与后端实际发放逻辑共用 {@link MembershipRuleConstants} 及后台配置，避免页面文案失真。
   */
  @Override
  public Map<String, Object> getMembershipRules() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("levels", listLevels());

    MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();

    Map<String, Object> earn = new LinkedHashMap<>();
    earn.put("checkinPoints", rule.checkinPoints());
    earn.put("checkinStreakDays", rule.checkinStreakDays());
    earn.put("checkinStreakMultiplier", rule.checkinStreakMultiplier());
    earn.put("pointsPerCurrency",
        readPositiveIntConfig(PointsRewardServiceImpl.CONFIG_USD_RATE, 10));
    earn.put("firstOrderMultiplier",
        readPositiveIntConfig(PointsRewardServiceImpl.CONFIG_FIRST_ORDER_MULTIPLIER, 2));
    earn.put("reviewPoints", rule.reviewPoints());
    earn.put("invitePoints", rule.inviteRewardPoints());
    earn.put("inviteMonthlyLimit", rule.inviteMonthlyLimit());
    result.put("earn", earn);

    Map<String, Object> use = new LinkedHashMap<>();
    use.put("pointsPerCurrencyUnit", rule.pointsPerCurrencyUnit());
    use.put("maxDeductPercent", rule.maxDeductPercent());
    use.put("makeupFreePerMonth", rule.makeupFreePerMonth());
    use.put("makeupPoints", rule.makeupPointsCost());
    result.put("use", use);

    result.put("pointsValidMonths", MembershipRuleConstants.POINTS_VALID_MONTHS);
    return result;
  }

  /** 读取正整数配置项，缺失/非法/≤0 时回退默认值 */
  private int readPositiveIntConfig(String key, int defaultValue) {
    try {
      SystemConfigEntity entity = systemConfigMapper.selectOne(
          new LambdaQueryWrapper<SystemConfigEntity>()
              .eq(SystemConfigEntity::getConfigKey, key));
      if (entity == null || entity.getConfigValue() == null || entity.getConfigValue().isBlank()) {
        return defaultValue;
      }
      int value = Integer.parseInt(entity.getConfigValue().trim());
      return value > 0 ? value : defaultValue;
    } catch (Exception e) {
      log.warn("读取会员规则配置失败，使用默认值: key={}, reason={}", key, e.getMessage());
      return defaultValue;
    }
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
    // 等级档位（按成长值门槛排序），倍率统一从后台配置读取
    Map<Level, Double> rates = readLevelRates();
    List<Map<String, Object>> levels = new java.util.ArrayList<>();
    levels.add(level("L1", "Member", "注册即获得", 0, rates.get(Level.NORMAL)));
    levels.add(level("L2", "Silver", "完成首单 + 几次签到", 500, rates.get(Level.SILVER)));
    levels.add(level("L3", "Gold", "活跃用户", 2000, rates.get(Level.GOLD)));
    levels.add(level("L4", "Platinum", "高频消费用户", 8000, rates.get(Level.PLATINUM)));
    levels.add(level("L5", "Black", "顶级 VIP", 25000, rates.get(Level.DIAMOND)));
    return levels;
  }

  @Override
  public double getCurrentPointsRate(Long userId) {
    MemberEntity m = ensureMember(userId);
    Level level = m.getLevel() == null ? Level.NORMAL : m.getLevel();
    return readLevelRates().getOrDefault(level, DEFAULT_LEVEL_RATES.get(level));
  }

  /**
   * 读取后台配置的五档积分倍率。
   * 配置键：points.level_rate.NORMAL / SILVER / GOLD / PLATINUM / DIAMOND（值为倍率，如 1.2）。
   * 未配置、值非法或 ≤0 的档位回退到默认倍率。
   */
  private Map<Level, Double> readLevelRates() {
    Map<Level, Double> rates = new EnumMap<>(Level.class);
    rates.putAll(DEFAULT_LEVEL_RATES);
    try {
      List<SystemConfigEntity> list = systemConfigMapper.selectList(
          new LambdaQueryWrapper<SystemConfigEntity>()
              .likeRight(SystemConfigEntity::getConfigKey, CONFIG_LEVEL_RATE_PREFIX));
      for (SystemConfigEntity entity : list) {
        if (entity.getConfigKey() == null || entity.getConfigValue() == null) {
          continue;
        }
        String code = entity.getConfigKey().substring(CONFIG_LEVEL_RATE_PREFIX.length());
        try {
          Level level = Level.valueOf(code);
          double value = Double.parseDouble(entity.getConfigValue().trim());
          if (value > 0) {
            rates.put(level, value);
          }
        } catch (IllegalArgumentException ignore) {
          // 非法等级名 / 非法数值：忽略该条配置，保留默认倍率
        }
      }
    } catch (Exception e) {
      log.warn("读取会员等级积分倍率失败，使用默认值: {}", e.getMessage());
    }
    return rates;
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
