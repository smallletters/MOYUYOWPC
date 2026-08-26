package com.moyuyo.service;

import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsGoodsEntity;

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

  /** 章节 2.1：漏签补签（每月 1 次免费，之后 50 积分/次） */
  Map<String, Object> makeupCheckin(Long userId);
}