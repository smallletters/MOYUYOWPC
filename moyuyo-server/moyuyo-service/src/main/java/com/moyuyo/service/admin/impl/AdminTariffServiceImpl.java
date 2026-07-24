package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.TariffConfigEntity;
import com.moyuyo.dao.admin.mapper.TariffConfigMapper;
import com.moyuyo.service.admin.AdminTariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关税服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTariffServiceImpl implements AdminTariffService {

  private final TariffConfigMapper tariffConfigMapper;

  @Override
  public List<Map<String, Object>> listConfigs(String countryCode) {
    LambdaQueryWrapper<TariffConfigEntity> wrapper = new LambdaQueryWrapper<>();
    if (countryCode != null && !countryCode.isEmpty()) {
      wrapper.eq(TariffConfigEntity::getCountryCode, countryCode);
    }
    wrapper.orderByDesc(TariffConfigEntity::getCreateTime);

    List<TariffConfigEntity> configs = tariffConfigMapper.selectList(wrapper);
    List<Map<String, Object>> list = new ArrayList<>();
    for (TariffConfigEntity config : configs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", config.getId());
      item.put("countryCode", config.getCountryCode());
      item.put("productCategory", config.getProductCategory());
      item.put("rate", config.getRate());
      item.put("currency", config.getCurrency());
      item.put("minThreshold", config.getMinThreshold());
      item.put("maxThreshold", config.getMaxThreshold());
      item.put("effectiveDate", config.getEffectiveDate());
      item.put("expireDate", config.getExpireDate());
      item.put("status", config.getStatus());
      item.put("createTime", config.getCreateTime());
      list.add(item);
    }
    return list;
  }

  @Override
  @Transactional
  public void createConfig(Map<String, Object> data) {
    TariffConfigEntity entity = new TariffConfigEntity();
    if (data.get("countryCode") != null) entity.setCountryCode((String) data.get("countryCode"));
    if (data.get("productCategory") != null) entity.setProductCategory((String) data.get("productCategory"));
    if (data.get("rate") != null) entity.setRate(new BigDecimal(data.get("rate").toString()));
    if (data.get("currency") != null) entity.setCurrency((String) data.get("currency"));
    if (data.get("minThreshold") != null) entity.setMinThreshold(new BigDecimal(data.get("minThreshold").toString()));
    if (data.get("maxThreshold") != null) entity.setMaxThreshold(new BigDecimal(data.get("maxThreshold").toString()));
    if (data.get("status") != null) entity.setStatus((String) data.get("status"));
    entity.setStatus("ENABLED");
    tariffConfigMapper.insert(entity);
  }

  @Override
  @Transactional
  public void updateConfig(Map<String, Object> data) {
    if (data.get("id") == null) return;
    TariffConfigEntity entity = tariffConfigMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("countryCode") != null) entity.setCountryCode((String) data.get("countryCode"));
    if (data.get("productCategory") != null) entity.setProductCategory((String) data.get("productCategory"));
    if (data.get("rate") != null) entity.setRate(new BigDecimal(data.get("rate").toString()));
    if (data.get("currency") != null) entity.setCurrency((String) data.get("currency"));
    if (data.get("minThreshold") != null) entity.setMinThreshold(new BigDecimal(data.get("minThreshold").toString()));
    if (data.get("maxThreshold") != null) entity.setMaxThreshold(new BigDecimal(data.get("maxThreshold").toString()));
    if (data.get("status") != null) entity.setStatus((String) data.get("status"));
    tariffConfigMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void deleteConfig(Long id) {
    tariffConfigMapper.deleteById(id);
  }

  @Override
  public Map<String, Object> calculate(String countryCode, BigDecimal amount, String category) {
    // 查找匹配的关税配置
    LambdaQueryWrapper<TariffConfigEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TariffConfigEntity::getCountryCode, countryCode);
    wrapper.eq(TariffConfigEntity::getProductCategory, category);
    wrapper.eq(TariffConfigEntity::getStatus, "ENABLED");

    TariffConfigEntity config = tariffConfigMapper.selectOne(wrapper);

    BigDecimal tariff = BigDecimal.ZERO;
    if (config != null && config.getRate() != null) {
      // 检查金额是否在阈值范围内
      boolean inRange = true;
      if (config.getMinThreshold() != null && amount.compareTo(config.getMinThreshold()) < 0) {
        inRange = false;
      }
      if (config.getMaxThreshold() != null && amount.compareTo(config.getMaxThreshold()) > 0) {
        inRange = false;
      }
      if (inRange) {
        tariff = amount.multiply(config.getRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      }
    }

    BigDecimal total = amount.add(tariff);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("countryCode", countryCode);
    result.put("amount", amount);
    result.put("category", category);
    result.put("tariff", tariff);
    result.put("total", total);
    return result;
  }
}
