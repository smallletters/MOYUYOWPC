package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.SmsRecordEntity;
import com.moyuyo.dao.admin.mapper.SmsRecordMapper;
import com.moyuyo.service.SmsService;
import com.moyuyo.service.admin.AdminSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
// 抑制 JDT 静态分析对 MyBatis-Plus LambdaQueryWrapper 方法引用的 @Nonnull 类型告警
@SuppressWarnings("null")
public class AdminSmsServiceImpl implements AdminSmsService {

  private final SmsRecordMapper smsRecordMapper;
  private final SmsService smsService;

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

  @Override
  public Map<String, Object> send(String phone, String content) {
    if (!StringUtils.hasText(phone)) {
      throw new IllegalArgumentException("手机号不能为空");
    }
    if (!StringUtils.hasText(content)) {
      throw new IllegalArgumentException("短信内容不能为空");
    }

    // 先落库（PENDING），再调用真实短信通道，结果回写 SENT / FAILED
    SmsRecordEntity record = new SmsRecordEntity();
    record.setPhone(phone);
    record.setContent(content);
    // template_code 是 NOT NULL 必填字段，后台手工短信统一记为 NOTICE
    record.setTemplateCode("NOTICE");
    record.setChannel("ALIYUN");
    record.setStatus("PENDING");
    smsRecordMapper.insert(record);

    try {
      // title 留空：后台手工短信直接以 content 作为模板变量
      smsService.sendNotice(phone, null, content);
      record.setStatus("SENT");
      record.setSendTime(LocalDateTime.now());
      smsRecordMapper.updateById(record);
    } catch (Exception e) {
      record.setStatus("FAILED");
      record.setFailReason(abbreviate(e.getMessage()));
      record.setSendTime(LocalDateTime.now());
      smsRecordMapper.updateById(record);
      throw new BusinessException(502, "短信发送失败: " + abbreviate(e.getMessage()));
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", record.getId());
    result.put("phone", phone);
    result.put("status", "SENT");
    result.put("message", "短信发送成功");
    return result;
  }

  /**
   * 截断失败原因，避免超长第三方报文写入数据库
   */
  private String abbreviate(String text) {
    if (text == null) {
      return "未知错误";
    }
    return text.length() <= 200 ? text : text.substring(0, 200) + "...";
  }
}
