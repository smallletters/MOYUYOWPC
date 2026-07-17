package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.service.admin.AdminReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台评价管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

  private final ProductReviewMapper productReviewMapper;

  @Override
  public Page<ProductReviewEntity> listAll(String status, int page, int size) {
    LambdaQueryWrapper<ProductReviewEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(ProductReviewEntity::getStatus, status);
    }
    wrapper.orderByDesc(ProductReviewEntity::getCreateTime);
    return productReviewMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 查询所有评价
    Long total = productReviewMapper.selectCount(new LambdaQueryWrapper<>());
    // 好评（评分 >= 4）
    Long positive = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().ge(ProductReviewEntity::getRating, 4));
    // 中评（评分 = 3）
    Long neutral = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getRating, 3));
    // 差评（评分 <= 2）
    Long negative = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().le(ProductReviewEntity::getRating, 2));
    // 待审核
    Long pending = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getStatus, "待审核"));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("positive", positive);
    result.put("neutral", neutral);
    result.put("negative", negative);
    result.put("pending", pending);
    // 好评率 = 好评数 / 总数 * 100
    result.put("positiveRate", total > 0 ? (double) positive / total * 100 : 0);
    return result;
  }

  @Override
  public void reply(Long id, String content) {
    // ProductReviewEntity 没有 replyContent 字段，这里通过更新 status 来标记已回复
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null) {
      entity.setTags("已回复:" + content);
      productReviewMapper.updateById(entity);
    }
  }

  @Override
  public void delete(Long id) {
    productReviewMapper.deleteById(id);
  }

  @Override
  public void approve(Long id) {
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("已审核");
      productReviewMapper.updateById(entity);
    }
  }

  @Override
  public void reject(Long id) {
    ProductReviewEntity entity = productReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("已驳回");
      productReviewMapper.updateById(entity);
    }
  }
}
