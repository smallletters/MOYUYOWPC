package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.dto.address.AddressValidateResponse;
import com.moyuyo.common.util.CountryCodes;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.service.AddressService;
import com.moyuyo.service.admin.ShippingZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// 抑制 JDT 静态检查对 MyBatis-Plus Lambda 引用的 null type safety 警告
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

  private final AddressMapper addressMapper;
  private final ShippingZoneService shippingZoneService;

  @Override
  @Transactional(readOnly = true)
  public List<AddressEntity> listByUserId(Long userId) {
    return addressMapper.selectList(
        new LambdaQueryWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId)
            .orderByDesc(AddressEntity::getIsDefault)
            .orderByDesc(AddressEntity::getCreatedAt));
  }

  @Override
  @Transactional(readOnly = true)
  public AddressEntity getById(Long id, Long userId) {
    AddressEntity entity = addressMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("地址不存在");
    }
    // 地址存在但不属于当前用户：抛 403，避免通过响应区分 ID 是否存在（更严格可也抛 404）
    if (!entity.getUserId().equals(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("无权访问该地址");
    }
    return entity;
  }

  @Override
  @Transactional
  public AddressEntity create(Long userId, AddressEntity address) {
    address.setId(null);
    address.setUserId(userId);

    boolean wantDefault = Boolean.TRUE.equals(address.getIsDefault());
    // 若是首条（count==0），自动升为默认
    long count = addressMapper.selectCount(
        new LambdaQueryWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId));
    if (count == 0) {
      wantDefault = true;
    }

    if (wantDefault) {
      // 并发安全：先批量取消既有默认（保证全表只有 0 或 1 条 is_default=true），
      // 再 insert 新默认；即使两条 create 并发，另一条也会因 update 条件精确命中而拿到不同结果。
      addressMapper.update(
          null,
          new LambdaUpdateWrapper<AddressEntity>()
              .eq(AddressEntity::getUserId, userId)
              .eq(AddressEntity::getIsDefault, true)
              .set(AddressEntity::getIsDefault, false));
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
    if (existing == null) {
      throw new IllegalArgumentException("地址不存在");
    }
    if (!existing.getUserId().equals(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("无权操作该地址");
    }

    address.setUserId(userId);
    addressMapper.updateById(address);
    return addressMapper.selectById(address.getId());
  }

  @Override
  @Transactional
  public void delete(Long id, Long userId) {
    AddressEntity existing = addressMapper.selectById(id);
    if (existing == null) {
      throw new IllegalArgumentException("地址不存在");
    }
    if (!existing.getUserId().equals(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("无权删除该地址");
    }
    addressMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void setDefault(Long id, Long userId) {
    AddressEntity existing = addressMapper.selectById(id);
    if (existing == null) {
      throw new IllegalArgumentException("地址不存在");
    }
    if (!existing.getUserId().equals(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("无权修改该地址");
    }
    if (Boolean.TRUE.equals(existing.getIsDefault())) {
      // 已经是默认了，省一次 DB 写
      // 仍记一条 INFO 日志用于审计：用户重复点"设为默认"按钮时能区分服务端行为（无变更）
      log.info("setDefault: addressId={} userId={} already default (idempotent)", id, userId);
      return;
    }

    // 并发安全：UPDATE 限定 user_id + id 自定义，AND NOT is_default=本条；同事务内分两步执行，
    // 数据库 Innodb 行锁会让两条并发的 setDefault 串行完成，最终保持仅有 1 条默认。
    addressMapper.update(
        null,
        new LambdaUpdateWrapper<AddressEntity>()
            .eq(AddressEntity::getUserId, userId)
            .eq(AddressEntity::getIsDefault, true)
            .ne(AddressEntity::getId, id)
            .set(AddressEntity::getIsDefault, false));

    existing.setIsDefault(true);
    addressMapper.updateById(existing);
  }

  /**
   * 命中 ACTIVE 区域的国家码即认为可发货。
   * 由 ShippingZoneService 统一聚合，避免在 AddressService 直接依赖 dao 层 mapper。
   */
  private Set<String> loadShippableCountries() {
    return shippingZoneService.loadShippableCountries();
  }

  @Override
  @Transactional(readOnly = true)
  public AddressValidateResponse validateAddress(Long addressId, Long userId) {
    try {
      AddressEntity address = getById(addressId, userId);
      if (CountryCodes.isBlank(address.getCountry())) {
        return new AddressValidateResponse(false, "Country is required");
      }
      String code = CountryCodes.normalizeSingle(address.getCountry());
      Set<String> shippable = loadShippableCountries();
      if (shippable.contains(code)) {
        return new AddressValidateResponse(true, "We ship to " + code);
      }
      return new AddressValidateResponse(false, "We currently do not ship to " + code);
    } catch (IllegalArgumentException e) {
      return new AddressValidateResponse(false, "Address not found");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<AddressValidateResponse.Item> batchValidate(List<Long> addressIds, Long userId) {
    if (addressIds == null || addressIds.isEmpty()) return Collections.emptyList();
    // 一次性查所有地址（避免 N+1）
    // 用 selectByIds 替代已 deprecated 的 selectBatchIds（同签名，MyBatis-Plus 3.5.x 推荐写法）
    List<AddressEntity> addresses = addressMapper.selectByIds(addressIds);
    // 仅保留归属当前用户的（getById 内含权限校验，这里用批量 + 内存过滤，避免逐条 selectById）
    List<AddressEntity> owned = new ArrayList<>();
    for (AddressEntity a : addresses) {
      if (a != null && a.getUserId() != null && a.getUserId().equals(userId)) owned.add(a);
    }
    // 单次缓存命中：30s 内复用同一份国家码集
    Set<String> shippable = loadShippableCountries();
    List<AddressValidateResponse.Item> items = new ArrayList<>(owned.size());
    for (AddressEntity a : owned) {
      String raw = a.getCountry();
      String normalized = CountryCodes.normalizeSingle(raw);
      boolean ok = !CountryCodes.isBlank(normalized) && shippable.contains(normalized);
      items.add(new AddressValidateResponse.Item(a.getId(), ok, raw));
    }
    return items;
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<String> listSupportedCountries() {
    return shippingZoneService.listSupportedCountries();
  }
}
