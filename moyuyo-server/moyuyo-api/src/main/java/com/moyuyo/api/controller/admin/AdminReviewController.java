package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 评价管理")
@RestController
@RequestMapping("/api/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

  private final AdminReviewService adminReviewService;
  private final ProductMapper productMapper;
  private final UserMapper userMapper;

  @Operation(summary = "评价列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    var pageResult = adminReviewService.listAll(status, page, size);
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> list = new ArrayList<>();
    for (ProductReviewEntity review : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", review.getId());
      item.put("productId", review.getProductId());
      item.put("userId", review.getUserId());
      // 查询商品名称
      if (review.getProductId() != null) {
        ProductEntity product = productMapper.selectById(review.getProductId());
        item.put("productName", product != null ? product.getName() : "未知商品");
      } else {
        item.put("productName", "未知商品");
      }
      // 查询用户名称
      if (review.getUserId() != null) {
        UserEntity user = userMapper.selectById(review.getUserId());
        item.put("userName", user != null ? user.getNickname() : "匿名用户");
      } else {
        item.put("userName", "匿名用户");
      }
      item.put("rating", review.getRating());
      item.put("content", review.getContent());
      item.put("status", review.getStatus());
      item.put("createTime", review.getCreateTime());
      list.add(item);
    }
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  @Operation(summary = "审核通过")
  @PutMapping("/{id}/approve")
  public Result<Map<String, Object>> approve(@PathVariable Long id) {
    adminReviewService.approve(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", "已审核");
    result.put("message", "审核通过成功");
    return Result.success(result);
  }

  @Operation(summary = "审核驳回")
  @PutMapping("/{id}/reject")
  public Result<Map<String, Object>> reject(@PathVariable Long id) {
    adminReviewService.reject(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", "已驳回");
    result.put("message", "审核驳回成功");
    return Result.success(result);
  }

  @Operation(summary = "回复评价")
  @PostMapping("/{id}/reply")
  public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
    adminReviewService.reply(id, body.getOrDefault("content", ""));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("reply", body.getOrDefault("content", ""));
    result.put("message", "回复成功");
    return Result.success(result);
  }
}
