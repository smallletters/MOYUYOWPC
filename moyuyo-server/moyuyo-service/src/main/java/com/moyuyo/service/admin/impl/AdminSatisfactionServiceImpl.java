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

  @Override
  public Map<String, Object> createSurvey(Map<String, Object> body) {
    // 新建满意度调查记录，userId 必填，评分默认 5
    Object userIdObj = body.get("userId");
    if (userIdObj == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }
    SatisfactionSurveyEntity entity = new SatisfactionSurveyEntity();
    entity.setUserId(Long.valueOf(userIdObj.toString()));
    Object score = body.get("score");
    entity.setScore(score != null ? Integer.valueOf(score.toString()) : 5);
    entity.setCategory((String) body.get("category"));
    entity.setComment((String) body.get("comment"));
    if (body.get("ticketId") != null) {
      entity.setTicketId(Long.valueOf(body.get("ticketId").toString()));
    }
    if (body.get("orderId") != null) {
      entity.setOrderId(Long.valueOf(body.get("orderId").toString()));
    }
    satisfactionSurveyMapper.insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("userId", entity.getUserId());
    result.put("score", entity.getScore());
    result.put("message", "满意度调查创建成功");
    return result;
  }

  @Override
  public void replySurvey(Long id, String reply) {
    SatisfactionSurveyEntity entity = satisfactionSurveyMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("评价记录不存在");
    }
    // 追加客服回复到评价内容，避免新增字段
    String old = entity.getComment();
    String prefix = old == null || old.isEmpty() ? "" : old + "\n";
    entity.setComment(prefix + "【客服回复】" + (reply == null ? "" : reply));
    satisfactionSurveyMapper.updateById(entity);
  }
}
