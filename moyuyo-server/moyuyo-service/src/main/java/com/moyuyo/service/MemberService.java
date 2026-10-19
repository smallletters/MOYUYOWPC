package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.member.MemberVO;
import com.moyuyo.common.dto.member.WalletVO;
import com.moyuyo.dao.entity.PointsLogEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MemberService {

  MemberVO getMemberInfo(Long userId);

  /**
   * 获取/分配会员卡号：首次调用生成随机唯一 12 位数字并落库，此后永久返回同一号码。
   * C 端会员中心与管理后台统一走此方法，保证同一用户两边看到一致卡号。
   */
  String getOrAssignMemberNo(Long userId);

  Page<PointsLogEntity> getPointsLog(Long userId, int page, int size);

  /**
   * 取最近 days 天内存在签到的日期集合（含今天，按自然日）。
   * 用于连续签到 / 断签判定：直接按日期查流水，避免用"最近 N 条流水"分页窗口判定时，
   * 被任务奖励、订单等大量流水挤掉签到记录而导致漏判、误判。
   */
  Set<LocalDate> getRecentCheckinDates(Long userId, int days);

  /** 取指定月份内存在签到的日期（升序、去重），用于签到日历渲染 */
  List<LocalDate> getCheckinDatesOfMonth(Long userId, YearMonth month);

  /**
   * 当前连续签到天数：从今天往回数，今天未签到则为 0。
   * 全站唯一实现（签到日历接口与任务中心 stats 共用），避免多处各写一份导致口径不一致。
   */
  int getCurrentCheckinStreak(Long userId);

  void addPoints(Long userId, int changeValue, String type, String bizNo, String remark);

  /**
   * 按指定时间补写正向/负向积分流水。
   * 用于补签场景：把 CHECKIN 流水回填到被补签的那一天，使连续签到判定能正确接上。
   * 有效期同样按 createdAt + 12 月计算，保证积分过期口径与正常发放一致。
   *
   * @param createdAt 流水时间；为空时等同 {@link #addPoints}
   */
  void addPointsAt(Long userId, int changeValue, String type, String bizNo, String remark,
      LocalDateTime createdAt);

  void spendPoints(Long userId, int changeValue, String bizNo, String remark);

  /**
   * 可用积分余额 = 总积分(mo_user.points) − 冻结积分(mo_user.points_frozen)。
   * <p>
   * 冻结积分是"未支付订单占用的抵扣积分"：下单即冻结、支付成功转实扣、取消/超时解冻。
   * 结算页/兑换/补签等一切"用户本次能用多少积分"的判断都必须用可用余额，
   * 否则同一批积分会被多笔待付款订单重复勾选（原缺陷）。
   */
  int getPointsBalance(Long userId);

  /**
   * 冻结积分（下单占用）：原子条件更新 points_frozen += points，
   * 仅当可用积分(points − points_frozen) ≥ points 时成功，用 affected rows 判定，避免"先查后判"的并发竞态。
   *
   * @return true=冻结成功；false=可用积分不足（未做任何变更）
   */
  boolean freezePoints(Long userId, int points, String bizNo, String remark);

  /**
   * 解冻积分（取消/超时回退）：原子条件更新 points_frozen −= points，仅当 points_frozen ≥ points 时成功。
   * <p>
   * 幂等：无对应冻结（如存量订单、重复取消）时命中 0 行并返回 false，不报错、不误还。
   * 调用方（cancelOrder）已通过 PENDING_PAY→CANCELLED 原子抢占保证每单只解冻一次。
   *
   * @return true=解冻成功；false=无对应冻结（已忽略）
   */
  boolean unfreezePoints(Long userId, int points, String bizNo, String remark);

  /**
   * 冻结转实扣（支付成功）：在同一个 UPDATE 内同时 points −= points 与 points_frozen −= points，
   * 避免"先解冻再 spendPoints"两步之间存在并发窗口导致重复扣减/漏扣。
   * <p>
   * 语义替代原来的 spendPoints：支付成功只调用本方法，绝不再额外 spendPoints，防止双扣。
   * 失败（冻结/余额不足）返回 false，由调用方记录告警走人工对账，不阻断支付主流程。
   *
   * @return true=实扣成功；false=冻结或余额不足
   */
  boolean consumeFrozenPoints(Long userId, int points, String bizNo, String remark);

  WalletVO getWallet(Long userId);

  WalletVO recharge(Long userId, BigDecimal amount, String channel);

  /** 等级列表 + 倍率（前端会员页对比表） */
  List<Map<String, Object>> listLevels();

  /** 当前用户的积分倍率 */
  double getCurrentPointsRate(Long userId);

  /** 会员专属特权列表（按等级过滤后返回） */
  List<Map<String, Object>> listPrivileges(Long userId);

  /**
   * 根据用户当前总积分重算并同步 mo_member.level / mo_member.growth_value
   * 用于：管理员手动调整积分后、C 端签到/订单返积分后、后台一键修复历史数据
   */
  void recalculateLevel(Long userId);
}
