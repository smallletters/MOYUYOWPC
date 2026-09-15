package com.moyuyo.service;

import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsGoodsEntity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface PointsService {

  /** 章节 3.2：上架的积分礼品列表 */
  List<PointsGoodsEntity> listGoods(String category);

  /** 章节 3.2：兑换礼品，扣减积分 + 写流水 + 创建兑换记录 */
  PointsExchangeEntity exchange(Long userId, Long goodsId, String receiverName,
      String receiverPhone, String receiverAddress);

  /** 当前用户的兑换记录 */
  List<PointsExchangeEntity> listMyExchanges(Long userId);

  /**
   * 章节 2.1：漏签补签（每月 1 次免费，之后 50 积分/次）。
   *
   * @param date 被补签的日期（只能补当月、今天之前且当天没有签到记录的漏签日）；
   *             为空时自动补"当月最近的漏签日"
   */
  Map<String, Object> makeupCheckin(Long userId, LocalDate date);

  /**
   * 章节 2.1：签到日历数据。
   * 返回指定月份的已签到日期集合 + 当前连续签到天数 + 今天是否已签到，
   * 供签到页直接渲染，避免前端用"最近 N 条积分流水"自行计算导致日历/连续天数不准。
   *
   * @param month 月份；为空时取当月
   */
  Map<String, Object> getCheckinCalendar(Long userId, YearMonth month);
}