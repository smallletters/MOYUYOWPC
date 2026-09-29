package com.moyuyo.service;

import com.moyuyo.common.dto.address.AddressValidateResponse;
import com.moyuyo.dao.entity.AddressEntity;

import java.util.List;

public interface AddressService {

  List<AddressEntity> listByUserId(Long userId);

  AddressEntity getById(Long id, Long userId);

  AddressEntity create(Long userId, AddressEntity address);

  AddressEntity update(Long userId, AddressEntity address);

  void delete(Long id, Long userId);

  void setDefault(Long id, Long userId);

  AddressValidateResponse validateAddress(Long addressId, Long userId);

  /**
   * 批量校验：仅校验传入地址 ID 列表中归属当前用户的地址。
   * - 不属于当前用户的地址会被过滤（不返回结果，行为等价于"看不见"）
   * - 地址不存在同样过滤
   */
  java.util.List<com.moyuyo.common.dto.address.AddressValidateResponse.Item> batchValidate(java.util.List<Long> addressIds, Long userId);

  /** APP 国家选择器数据源：返回所有被配置过的国家码（去重排序） */
  java.util.List<String> listSupportedCountries();
}
