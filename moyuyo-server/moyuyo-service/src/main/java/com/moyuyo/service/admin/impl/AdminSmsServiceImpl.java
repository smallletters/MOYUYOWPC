package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.SmsRecordEntity;
import com.moyuyo.dao.admin.mapper.SmsRecordMapper;
import com.moyuyo.service.admin.AdminSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台短信服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminSmsServiceImpl implements AdminSmsService {

  private final SmsRecordMapper smsRecordMapper;

  @Override
  public Page<Map<String, Object>> listAll(String phone, String status, int page, int size) {
    LambdaQueryWrapper<SmsRecordEntity> wrapper = new LambdaQueryWrapper<>();
    if (phone != null && !phone.isEmpty()) {
      wrapper.like(SmsRecordEntity::getPhone, phone);
    }
    if (status != null && !status.isEmpty()) {
      wrapper.eq(SmsRecordEntity::getStatus, status);
    }
    wrapper.orderByDesc(SmsRecordEntity::getCreateTime);

    Page<SmsRecordEntity> entityPage = smsRecordMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("phone", e.getPhone());
      item.put("templateCode", e.getTemplateCode());
      item.put("content", e.getContent());
      item.put("channel", e.getChannel());
      item.put("status", e.getStatus());
      item.put("failReason", e.getFailReason());
      item.put("sendTime", e.getSendTime());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Map<String, Object> stats() {
    // 今日发送数和成功率
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

    LambdaQueryWrapper<SmsRecordEntity> todayWrapper = new LambdaQueryWrapper<>();
    todayWrapper.between(SmsRecordEntity::getCreateTime, todayStart, todayEnd);
    List<SmsRecordEntity> todayRecords = smsRecordMapper.selectList(todayWrapper);

    long todaySent = todayRecords.size();
    long todaySuccess = todayRecords.stream().filter(r -> "SENT".equals(r.getStatus())).count();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("todaySent", todaySent);
    result.put("todaySuccess", todaySuccess);
    result.put("successRate", todaySent > 0 ? Math.round(todaySuccess * 10000.0 / todaySent) / 100.0 : 0);
    return result;
  }
}
