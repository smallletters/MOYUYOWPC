package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.service.admin.AdminCsSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客服会话服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminCsSessionServiceImpl implements AdminCsSessionService {

  private final CsSessionMapper csSessionMapper;

  @Override
  public Map<String, Object> listAll(int page, int size, String status, String sessionId, String userId) {
    LambdaQueryWrapper<CsSessionEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(CsSessionEntity::getStatus, status);
    }
    if (sessionId != null && !sessionId.isEmpty()) {
      wrapper.eq(CsSessionEntity::getSessionId, sessionId);
    }
    if (userId != null && !userId.isEmpty()) {
      wrapper.eq(CsSessionEntity::getUserId, Long.valueOf(userId));
    }
    wrapper.orderByDesc(CsSessionEntity::getCreateTime);

    Page<CsSessionEntity> pageObj = csSessionMapper.selectPage(new Page<>(page, size), wrapper);

    List<Map<String, Object>> mappedList = pageObj.getRecords().stream().map(entity -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", entity.getId());
      item.put("sessionId", entity.getSessionId());
      item.put("userId", entity.getUserId());
      item.put("csStaffId", entity.getCsStaffId());
      item.put("status", entity.getStatus());
      item.put("channel", entity.getChannel());
      item.put("createTime", entity.getCreateTime());
      item.put("updateTime", entity.getUpdateTime());
      // 计算字段：基于 update_time - create_time 推算会话持续时长
      item.put("duration", computeDuration(entity.getCreateTime(), entity.getUpdateTime()));
      // TODO: 接入 IM 中间件后，从消息表汇总实际消息条数替换该占位值
      item.put("messageCount", 0);
      item.put("userName", entity.getUserId() != null ? "用户" + entity.getUserId() : "");
      item.put("agentName", entity.getCsStaffId() != null ? "客服" + entity.getCsStaffId() : "");
      item.put("createdAt", entity.getCreateTime());
      return item;
    }).collect(Collectors.toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", mappedList);
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    CsSessionEntity entity = csSessionMapper.selectById(id);
    if (entity == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("sessionId", entity.getSessionId());
    result.put("userId", entity.getUserId());
    result.put("csStaffId", entity.getCsStaffId());
    result.put("status", entity.getStatus());
    result.put("channel", entity.getChannel());
    result.put("createTime", entity.getCreateTime());
    result.put("updateTime", entity.getUpdateTime());
    result.put("duration", computeDuration(entity.getCreateTime(), entity.getUpdateTime()));
    result.put("messageCount", 0);
    result.put("lastMessage", "（暂无消息记录，待 IM 中间件接入）");
    return result;
  }

  @Override
  public Map<String, Object> getStats() {
    Long totalSessions = csSessionMapper.selectCount(new LambdaQueryWrapper<>());
    Long pendingSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "WAITING"));
    Long activeSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "PROCESSING"));
    Long closedSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "CLOSED"));

    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    Long todayNew = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().ge(CsSessionEntity::getCreateTime, todayStart));

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalSessions", totalSessions);
    stats.put("pendingSessions", pendingSessions);
    stats.put("activeSessions", activeSessions);
    stats.put("closedSessions", closedSessions);
    stats.put("todayNew", todayNew);
    // 平均响应时间：IM 中间件接入后由实时消息计算
    stats.put("avgResponseTime", 0);
    return stats;
  }

  /** 会话持续时长：将差值格式化为 "X小时Y分" 或 "Y分钟" */
  private String computeDuration(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null) {
      return "-";
    }
    long minutes = Duration.between(start, end).toMinutes();
    if (minutes <= 0) {
      return "0分钟";
    }
    long hours = minutes / 60;
    long mins = minutes % 60;
    return hours > 0 ? (hours + "小时" + mins + "分") : (mins + "分钟");
  }
}