package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.dao.admin.mapper.PushRecordMapper;
import com.moyuyo.service.admin.PushManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送管理服务实现
 */
@Service
@RequiredArgsConstructor
public class PushManageServiceImpl implements PushManageService {

  private final PushRecordMapper pushRecordMapper;

  @Override
  public List<PushRecordEntity> listRecords(String status, String type) {
    LambdaQueryWrapper<PushRecordEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(PushRecordEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(PushRecordEntity::getType, type);
    }
    wrapper.orderByDesc(PushRecordEntity::getCreateTime);
    return pushRecordMapper.selectList(wrapper);
  }

  @Override
  public void create(PushRecordEntity entity) {
    pushRecordMapper.insert(entity);
  }

  @Override
  public void update(PushRecordEntity entity) {
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void send(Long id) {
    PushRecordEntity entity = new PushRecordEntity();
    entity.setId(id);
    entity.setStatus("SENT");
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void cancel(Long id) {
    PushRecordEntity entity = new PushRecordEntity();
    entity.setId(id);
    entity.setStatus("CANCELLED");
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    pushRecordMapper.deleteById(id);
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();

    LocalDate today = LocalDate.now();
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

    // 今日推送数
    LambdaQueryWrapper<PushRecordEntity> todayWrapper = new LambdaQueryWrapper<>();
    todayWrapper.ge(PushRecordEntity::getCreateTime, todayStart);
    long todayPushCount = pushRecordMapper.selectCount(todayWrapper);

    // 本月推送数
    LambdaQueryWrapper<PushRecordEntity> monthWrapper = new LambdaQueryWrapper<>();
    monthWrapper.ge(PushRecordEntity::getCreateTime, monthStart);
    long monthPushCount = pushRecordMapper.selectCount(monthWrapper);

    // 从已发送的推送记录中统计成功/失败数
    LambdaQueryWrapper<PushRecordEntity> sentWrapper = new LambdaQueryWrapper<>();
    sentWrapper.eq(PushRecordEntity::getStatus, "SENT");
    List<PushRecordEntity> sentRecords = pushRecordMapper.selectList(sentWrapper);

    long totalSuccess = 0;
    long totalFail = 0;
    for (PushRecordEntity record : sentRecords) {
      totalSuccess += record.getSuccessCount() != null ? record.getSuccessCount() : 0;
      totalFail += record.getFailCount() != null ? record.getFailCount() : 0;
    }

    // 计算成功率
    BigDecimal successRate = BigDecimal.ZERO;
    long total = totalSuccess + totalFail;
    if (total > 0) {
      successRate = BigDecimal.valueOf(totalSuccess)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    stats.put("todayPushCount", todayPushCount);
    stats.put("monthPushCount", monthPushCount);
    stats.put("successRate", successRate);
    stats.put("subscribedUsers", BigDecimal.valueOf(totalSuccess));

    return stats;
  }

  @Override
  public List<PushRecordEntity> listScheduledRecords() {
    LambdaQueryWrapper<PushRecordEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(PushRecordEntity::getStatus, "SCHEDULED")
      .orderByAsc(PushRecordEntity::getScheduledTime);
    return pushRecordMapper.selectList(wrapper);
  }

  @Override
  public void saveSchedule(PushRecordEntity record) {
    record.setStatus("SCHEDULED");
    pushRecordMapper.insert(record);
  }
}
