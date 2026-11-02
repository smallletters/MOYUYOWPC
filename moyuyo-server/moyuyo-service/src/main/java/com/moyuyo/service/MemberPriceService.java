package com.moyuyo.service;

import com.moyuyo.dao.entity.MemberPriceConfigEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员价（按会员等级统一折扣）配置服务
 */
public interface MemberPriceService {

  /** 五档会员等级，顺序与后台展示一致 */
  List<String> LEVELS = List.of("NORMAL", "SILVER", "GOLD", "PLATINUM", "DIAMOND");

  /** 全部等级折扣配置（按等级顺序返回） */
  List<MemberPriceConfigEntity> listConfigs();

  /** 保存配置（按 level 更新或新增），返回保存后的全量配置 */
  List<MemberPriceConfigEntity> saveConfigs(List<MemberPriceConfigEntity> configs);

  /** 按等级取折扣系数；无配置/未启用时返回 1.00（原价） */
  BigDecimal getDiscountByLevel(String level);

  /** 按用户取折扣系数；无会员记录时返回 1.00（原价） */
  BigDecimal getDiscountForUser(Long userId);

  /** 按用户取会员等级编码；无会员记录时返回 NORMAL（只读，不创建会员记录） */
  String getLevelForUser(Long userId);
}
