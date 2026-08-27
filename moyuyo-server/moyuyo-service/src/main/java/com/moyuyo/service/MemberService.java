package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.member.MemberVO;
import com.moyuyo.common.dto.member.WalletVO;
import com.moyuyo.dao.entity.PointsLogEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MemberService {

  MemberVO getMemberInfo(Long userId);

  Page<PointsLogEntity> getPointsLog(Long userId, int page, int size);

  void addPoints(Long userId, int changeValue, String type, String bizNo, String remark);

  void spendPoints(Long userId, int changeValue, String bizNo, String remark);

  int getPointsBalance(Long userId);

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
