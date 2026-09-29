package com.moyuyo.service.admin;

import java.util.List;
import java.util.Set;

/**
 * 发货区域（Shipping Zone）业务服务。
 * 提供两个核心能力：
 * 1) loadShippableCountries：聚合 ACTIVE 区域的国家码，供 APP 地址校验 / checkout 兜底使用；
 * 2) listSupportedCountries：去重排序后的国家码列表，供 APP 国家选择器加载。
 */
public interface ShippingZoneService {

  /** 聚合所有 ACTIVE 区域配置的国家码（去重、转大写） */
  Set<String> loadShippableCountries();

  /** 返回 ACTIVE 区域内的去重排序国家码列表，供前端下拉/校验使用 */
  List<String> listSupportedCountries();

  /**
   * 失效缓存：管理后台对 Zone CRUD 后调用，让下一次 loadShippableCountries 立刻重读 DB，
   * 避免最长 30s 的延迟让 APP 用户短暂看到"运营已删国家但仍可下单"。
   */
  void evictCache();
}