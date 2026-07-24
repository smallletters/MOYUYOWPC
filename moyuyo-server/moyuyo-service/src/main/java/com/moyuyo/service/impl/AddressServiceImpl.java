package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.dto.address.AddressValidateResponse;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

  private final AddressMapper addressMapper;

  @Override
  public List<AddressEntity> listByUserId(Long userId) {
    return addressMapper.selectList(
        new LambdaQueryWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId)
            .orderByDesc(AddressEntity::getIsDefault)
            .orderByDesc(AddressEntity::getCreatedAt));
  }

  @Override
  public AddressEntity getById(Long id, Long userId) {
    AddressEntity entity = addressMapper.selectById(id);
    if (entity == null || !entity.getUserId().equals(userId)) {
      throw new IllegalArgumentException("地址不存在或无权访问");
    }
    return entity;
  }

  @Override
  @Transactional
  public AddressEntity create(Long userId, AddressEntity address) {
    address.setId(null);
    address.setUserId(userId);

    long count = addressMapper.selectCount(
        new LambdaQueryWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId));

    if (Boolean.TRUE.equals(address.getIsDefault()) || count == 0) {
      address.setIsDefault(true);
    } else {
      address.setIsDefault(false);
    }

    addressMapper.insert(address);
    return address;
  }

  @Override
  @Transactional
  public AddressEntity update(Long userId, AddressEntity address) {
    AddressEntity existing = addressMapper.selectById(address.getId());
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("地址不存在或无权操作");
    }

    address.setUserId(userId);
    addressMapper.updateById(address);
    return addressMapper.selectById(address.getId());
  }

  @Override
  @Transactional
  public void delete(Long id, Long userId) {
    AddressEntity existing = addressMapper.selectById(id);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("地址不存在或无权操作");
    }
    addressMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void setDefault(Long id, Long userId) {
    AddressEntity existing = addressMapper.selectById(id);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("地址不存在或无权操作");
    }

    addressMapper.update(
        null,
        new LambdaUpdateWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId)
            .eq(AddressEntity::getIsDefault, true)
            .set(AddressEntity::getIsDefault, false));

    existing.setIsDefault(true);
    addressMapper.updateById(existing);
  }

  private static final java.util.Set<String> SHIPPABLE_COUNTRIES = java.util.Set.of(
      "US", "CA", "GB", "DE", "FR", "IT", "ES", "AU", "JP", "KR", "SG", "NZ");

  @Override
  public AddressValidateResponse validateAddress(Long addressId, Long userId) {
    try {
      AddressEntity address = getById(addressId, userId);
      String country = address.getCountry();
      if (country == null || country.isBlank()) {
        return new AddressValidateResponse(false, "Country is required");
      }

      String code = country.trim().toUpperCase();
      if (SHIPPABLE_COUNTRIES.contains(code)) {
        return new AddressValidateResponse(true, "We ship to " + code);
      }

      return new AddressValidateResponse(false, "We currently do not ship to " + code);
    } catch (IllegalArgumentException e) {
      return new AddressValidateResponse(false, "Address not found");
    }
  }
}
