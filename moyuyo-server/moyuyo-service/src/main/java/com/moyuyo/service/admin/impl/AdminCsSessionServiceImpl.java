package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.service.admin.AdminCsSessionService;
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

    // 转换为前端需要的格式，添加计算字段
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
      // 计算字段
      item.put("userName", "用户" + (entity.getUserId() != null ? entity.getUserId() : ""));
      item.put("agentName", "客服" + (entity.getCsStaffId() != null ? entity.getCsStaffId() : ""));
      item.put("messageCount", 0);
      item.put("duration", "0分钟");
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
    return result;
  }

  @Override
  public Map<String, Object> getStats() {
    // 查询各状态会话数量
    Long totalSessions = csSessionMapper.selectCount(new LambdaQueryWrapper<>());
    Long pendingSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "WAITING"));
    Long activeSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "PROCESSING"));
    Long closedSessions = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().eq(CsSessionEntity::getStatus, "CLOSED"));

    // 今日新会话数
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    Long todayNew = csSessionMapper.selectCount(
        new LambdaQueryWrapper<CsSessionEntity>().ge(CsSessionEntity::getCreateTime, todayStart));

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalSessions", totalSessions);
    stats.put("pendingSessions", pendingSessions);
    stats.put("activeSessions", activeSessions);
    stats.put("closedSessions", closedSessions);
    stats.put("todayNew", todayNew);
    stats.put("avgResponseTime", "N/A");
    return stats;
  }
}
