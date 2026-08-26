package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "用户年度报告")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private final OrderMapper orderMapper;
  private final PointsLogMapper pointsLogMapper;
  private final UserMapper userMapper;

  @GetMapping("/annual")
  public Result<Map<String, Object>> annual() {
    Long userId = UserContextHolder.getUserId();
    UserEntity u = userMapper.selectById(userId);
    int year = LocalDateTime.now().getYear();
    LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
    LocalDateTime end = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);

    List<OrderEntity> yearOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .ge(OrderEntity::getCreateTime, start)
            .lt(OrderEntity::getCreateTime, end));
    int orderCount = yearOrders.size();
    BigDecimal totalSpent = yearOrders.stream()
        .map(OrderEntity::getPayAmount).filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<PointsLogEntity> yearPoints = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .ge(PointsLogEntity::getCreatedAt, start)
            .lt(PointsLogEntity::getCreatedAt, end));
    int pointsEarned = yearPoints.stream()
        .filter(l -> l.getChangeValue() != null && l.getChangeValue() > 0)
        .mapToInt(PointsLogEntity::getChangeValue).sum();

    long daysWithUs = 0;
    if (u != null && u.getCreatedAt() != null) {
      daysWithUs = ChronoUnit.DAYS.between(u.getCreatedAt().toLocalDate(), java.time.LocalDate.now());
    }

    Map<String, Object> report = new HashMap<>();
    report.put("year", year);
    report.put("orderCount", orderCount);
    report.put("totalSpent", totalSpent);
    report.put("pointsEarned", pointsEarned);
    report.put("currentPoints", u == null ? 0 : (u.getPoints() == null ? 0 : u.getPoints()));
    report.put("daysWithUs", daysWithUs);
    return Result.success(report);
  }
}
