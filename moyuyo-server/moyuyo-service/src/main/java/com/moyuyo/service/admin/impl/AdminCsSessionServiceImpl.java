package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsMessageMapper;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.service.CsChatContextService;
import com.moyuyo.service.admin.AdminCsSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客服会话服务实现
 *
 * 在原有会话元数据之上，补全消息统计：
 * - messageCount 来自 mo_cs_message 表的 COUNT(*)，避免前端硬编码 0
 * - lastMessage 来自最近一条消息的内容（截断）
 * - duration 仍按 update_time - create_time 计算（消息变更会自动改 update_time）
 */
@Service
@RequiredArgsConstructor
// 抑制 JDT 对 MyBatis-Plus Lambda 方法引用的 @Nonnull 空值分析误报（项目内 AdminProductController 同样处理）
@SuppressWarnings("null")
public class AdminCsSessionServiceImpl implements AdminCsSessionService {

  private final CsSessionMapper csSessionMapper;
  private final CsMessageMapper csMessageMapper;
  private final CsChatContextService csChatContextService;

  /** 会话最后一条消息的预览（按 content 前 50 字截断） */
  private String lastMessageOf(Long sessionId) {
    if (sessionId == null) return "";
    CsMessageEntity last = csMessageMapper.selectOne(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .orderByDesc(CsMessageEntity::getCreateTime)
            .last("LIMIT 1")
    );
    return last == null ? "" : previewOf(last.getContent());
  }

  /** 消息预览：超过 50 字截断 */
  private String previewOf(String content) {
    if (content == null) return "";
    return content.length() > 50 ? content.substring(0, 50) + "..." : content;
  }

  /** 批量统计消息数，避免列表逐条 COUNT */
  private Map<Long, Long> messageCountMap(List<Long> sessionIds) {
    if (sessionIds.isEmpty()) return Map.of();
    Map<Long, Long> map = new HashMap<>();
    for (Map<String, Object> row : csMessageMapper.countBySessionIds(sessionIds)) {
      Object sid = row.get("sessionId");
      Object cnt = row.get("cnt");
      if (sid instanceof Number && cnt instanceof Number) {
        map.put(((Number) sid).longValue(), ((Number) cnt).longValue());
      }
    }
    return map;
  }

  /** 批量取最后一条消息预览，避免列表逐条查询 */
  private Map<Long, String> lastMessageMap(List<Long> sessionIds) {
    if (sessionIds.isEmpty()) return Map.of();
    Map<Long, String> map = new HashMap<>();
    for (Map<String, Object> row : csMessageMapper.selectLatestContentBySessionIds(sessionIds)) {
      Object sid = row.get("sessionId");
      if (sid instanceof Number) {
        map.put(((Number) sid).longValue(), previewOf(row.get("content") == null ? null : String.valueOf(row.get("content"))));
      }
    }
    return map;
  }

  @Override
  public Map<String, Object> listAll(int page, int size, String status, String sessionId, String userId) {
    LambdaQueryWrapper<CsSessionEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(CsSessionEntity::getStatus, status);
    }
    if (sessionId != null && !sessionId.isEmpty()) {
      wrapper.eq(CsSessionEntity::getSessionId, sessionId);
    }
    if (userId != null && !userId.isBlank()) {
      try {
        wrapper.eq(CsSessionEntity::getUserId, Long.valueOf(userId.trim()));
      } catch (NumberFormatException e) {
        // 用户ID只能是数字：非法输入直接返回空列表，避免抛异常变成 500
        return emptyPage(page, size);
      }
    }
    wrapper.orderByDesc(CsSessionEntity::getCreateTime);

    Page<CsSessionEntity> pageObj = csSessionMapper.selectPage(new Page<>(page, size), wrapper);
    // 消息条数与最后一条消息改为两次批量查询，避免逐条 COUNT / LIMIT 1
    List<Long> sessionIds = pageObj.getRecords().stream()
        .map(CsSessionEntity::getId)
        .collect(Collectors.toList());
    Map<Long, Long> countMap = messageCountMap(sessionIds);
    Map<Long, String> lastMap = lastMessageMap(sessionIds);

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
      // 从批量统计结果取消息条数与最后一条消息预览
      item.put("messageCount", countMap.getOrDefault(entity.getId(), 0L));
      item.put("lastMessage", lastMap.getOrDefault(entity.getId(), ""));
      item.put("userName", entity.getUserId() != null ? "用户" + entity.getUserId() : "");
      item.put("agentName", entity.getCsStaffId() != null ? "客服" + entity.getCsStaffId() : "");
      item.put("createdAt", entity.getCreateTime());
      // 用户咨询的商品/订单概览，列表页可直接展示
      item.put("productId", entity.getProductId());
      item.put("productName", entity.getProductName());
      item.put("orderNo", entity.getOrderNo());
      item.put("consultType", entity.getOrderNo() != null ? "ORDER" : (entity.getProductName() != null ? "PRODUCT" : ""));
      item.put("consultTitle", consultTitleOf(entity));
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
    result.put("messageCount", countMessages(entity.getId()));
    result.put("lastMessage", lastMessageOf(entity.getId()));
    result.put("productId", entity.getProductId());
    result.put("productName", entity.getProductName());
    result.put("productImage", entity.getProductImage());
    result.put("orderNo", entity.getOrderNo());
    result.put("consultType", entity.getOrderNo() != null ? "ORDER" : (entity.getProductName() != null ? "PRODUCT" : ""));
    result.put("consultTitle", consultTitleOf(entity));
    // 咨询商品：商品表实时快照（价格/在售状态），商品已删除时用冗余名称/图片兜底
    result.put("contextProduct", csChatContextService.productSnapshotWithFallback(
        entity.getProductId(), entity.getProductName(), entity.getProductImage()));
    // 咨询订单：按订单号 + 用户查询，避免客服误发他人订单
    result.put("contextOrder", csChatContextService.orderSnapshotByNo(
        entity.getOrderNo(), entity.getUserId()));
    return result;
  }

  @Override
  public List<Map<String, Object>> listUserOrders(Long sessionId, int limit) {
    CsSessionEntity session = csSessionMapper.selectById(sessionId);
    if (session == null || session.getUserId() == null) return List.of();
    return csChatContextService.listUserOrders(session.getUserId(), limit);
  }

  @Override
  public List<Map<String, Object>> listByUserId(Long userId, int limit) {
    if (userId == null) return List.of();
    // 限制单次返回条数，避免历史会话过多的用户拖慢侧栏加载
    int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
    List<CsSessionEntity> rows = csSessionMapper.selectList(
        new LambdaQueryWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getUserId, userId)
            .orderByDesc(CsSessionEntity::getCreateTime)
            .last("LIMIT " + safeLimit)
    );
    if (rows.isEmpty()) return List.of();
    List<Long> sessionIds = rows.stream().map(CsSessionEntity::getId).collect(Collectors.toList());
    Map<Long, Long> countMap = messageCountMap(sessionIds);
    Map<Long, String> lastMap = lastMessageMap(sessionIds);
    return rows.stream().map(entity -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", entity.getId());
      item.put("sessionId", entity.getSessionId());
      item.put("userId", entity.getUserId());
      item.put("status", entity.getStatus());
      item.put("createTime", entity.getCreateTime());
      item.put("messageCount", countMap.getOrDefault(entity.getId(), 0L));
      item.put("lastMessage", lastMap.getOrDefault(entity.getId(), ""));
      item.put("consultTitle", consultTitleOf(entity));
      return item;
    }).collect(Collectors.toList());
  }

  /** 分页参数非法时的空结果（保持与正常返回相同的字段结构） */
  private Map<String, Object> emptyPage(int page, int size) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", List.of());
    result.put("total", 0L);
    result.put("page", page);
    result.put("size", size);
    return result;
  }

  /** 会话咨询上下文标题：优先订单，其次商品 */
  private String consultTitleOf(CsSessionEntity entity) {
    if (entity.getOrderNo() != null && !entity.getOrderNo().isBlank()) {
      return "订单：" + entity.getOrderNo();
    }
    if (entity.getProductName() != null && !entity.getProductName().isBlank()) {
      return "商品：" + entity.getProductName();
    }
    return "";
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

    // 平均首次响应时长（秒）：第一条 AGENT 消息 - 对应会话的 create_time
    // 简化：从所有会话消息中找出最早 AGENT 消息的时间 - 该 session 的 create_time，取平均
    Long avgResponseSec = computeAvgFirstResponseSec();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalSessions", totalSessions);
    stats.put("pendingSessions", pendingSessions);
    stats.put("activeSessions", activeSessions);
    stats.put("closedSessions", closedSessions);
    stats.put("todayNew", todayNew);
    stats.put("avgResponseTime", avgResponseSec);
    return stats;
  }

  /**
   * 平均首次响应时长（秒）：
   * 对每条 AGENT 类型且为该会话首条 AGENT 消息，计算它与会话 create_time 的差值取平均
   */
  private Long computeAvgFirstResponseSec() {
    // 这里走 SQL 复杂；为了不引入自定义 Mapper XML，简化为统计 "PROCESSING/WAITING 状态会话中 USER→AGENT 间隔"
    // 用逐会话首条 AGENT 消息的差值均值
    List<CsSessionEntity> sessions = csSessionMapper.selectList(
        new LambdaQueryWrapper<CsSessionEntity>().isNotNull(CsSessionEntity::getCreateTime));
    if (sessions.isEmpty()) return 0L;
    long total = 0;
    int n = 0;
    for (CsSessionEntity s : sessions) {
      CsMessageEntity firstAgent = csMessageMapper.selectOne(
          new LambdaQueryWrapper<CsMessageEntity>()
              .eq(CsMessageEntity::getSessionId, s.getId())
              .eq(CsMessageEntity::getSenderType, "AGENT")
              .orderByAsc(CsMessageEntity::getCreateTime)
              .last("LIMIT 1")
      );
      if (firstAgent == null || firstAgent.getCreateTime() == null || s.getCreateTime() == null) continue;
      total += Duration.between(s.getCreateTime(), firstAgent.getCreateTime()).getSeconds();
      n++;
    }
    return n == 0 ? 0 : total / n;
  }

  /** 统计某会话消息数 */
  private long countMessages(Long sessionId) {
    if (sessionId == null) return 0;
    Long cnt = csMessageMapper.selectCount(
        new LambdaQueryWrapper<CsMessageEntity>().eq(CsMessageEntity::getSessionId, sessionId));
    return cnt == null ? 0 : cnt;
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