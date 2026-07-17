package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.SatisfactionSurveyEntity;
import com.moyuyo.dao.admin.mapper.SatisfactionSurveyMapper;
import com.moyuyo.service.admin.AdminSatisfactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台满意度服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminSatisfactionServiceImpl implements AdminSatisfactionService {

  private final SatisfactionSurveyMapper satisfactionSurveyMapper;

  @Override
  public Page<Map<String, Object>> listAll(Integer score, String category, int page, int size) {
    LambdaQueryWrapper<SatisfactionSurveyEntity> wrapper = new LambdaQueryWrapper<>();
    if (score != null) {
      wrapper.eq(SatisfactionSurveyEntity::getScore, score);
    }
    if (category != null && !category.isEmpty()) {
      wrapper.eq(SatisfactionSurveyEntity::getCategory, category);
    }
    wrapper.orderByDesc(SatisfactionSurveyEntity::getCreateTime);

    Page<SatisfactionSurveyEntity> entityPage = satisfactionSurveyMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("ticketId", e.getTicketId());
      item.put("orderId", e.getOrderId());
      item.put("userId", e.getUserId());
      item.put("score", e.getScore());
      item.put("category", e.getCategory());
      item.put("comment", e.getComment());
      item.put("dimensionsJson", e.getDimensionsJson());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Map<String, Object> stats() {
    List<SatisfactionSurveyEntity> all = satisfactionSurveyMapper.selectList(null);
    Map<String, Object> result = new LinkedHashMap<>();

    // 平均分
    double avgScore = all.stream().mapToInt(SatisfactionSurveyEntity::getScore).average().orElse(0.0);
    result.put("avgScore", Math.round(avgScore * 10.0) / 10.0);

    // 分布统计（按分数分组）
    Map<Integer, Long> distribution = all.stream()
        .collect(Collectors.groupingBy(SatisfactionSurveyEntity::getScore, Collectors.counting()));
    result.put("distribution", distribution);

    // 总数
    result.put("totalCount", all.size());
    return result;
  }
}
