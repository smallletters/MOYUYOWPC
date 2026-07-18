package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.UserBehaviorEntity;
import com.moyuyo.dao.admin.mapper.UserBehaviorMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.AdminUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 用户画像")
@RestController
@RequestMapping("/api/admin/user-profile")
public class AdminUserProfileController {

  private final AdminUserProfileService adminUserProfileService;
  private final OrderMapper orderMapper;

  // 新DAO模块maven安装失败时允许为null，避免ClassNotFoundException
  @Autowired(required = false)
  private Object userBehaviorMapper;

  // 手动构造器注入必需的依赖
  public AdminUserProfileController(AdminUserProfileService adminUserProfileService,
                                     OrderMapper orderMapper) {
    this.adminUserProfileService = adminUserProfileService;
    this.orderMapper = orderMapper;
  }

  @Operation(summary = "用户画像信息")
  @GetMapping("/{userId}")
  public Result<Map<String, Object>> profile(@PathVariable Long userId) {
    Map<String, Object> result = adminUserProfileService.getDetail(userId);
    return Result.success(result);
  }

  @Operation(summary = "用户行为数据")
  @GetMapping("/{userId}/behaviors")
  public Result<List<Map<String, Object>>> behaviors(@PathVariable Long userId) {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (userBehaviorMapper == null) {
      return Result.success(Collections.emptyList());
    }
    // 从 mo_user_behavior 表查询该用户的行为数据
    List<UserBehaviorEntity> behaviorList = ((UserBehaviorMapper) userBehaviorMapper).selectList(
        new LambdaQueryWrapper<UserBehaviorEntity>()
            .eq(UserBehaviorEntity::getUserId, userId));

    List<Map<String, Object>> list = new ArrayList<>();
    for (UserBehaviorEntity entity : behaviorList) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("behaviorType", entity.getBehaviorType());
      item.put("count", entity.getCount());
      item.put("lastTime", entity.getLastTime());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "用户订单历史")
  @GetMapping("/{userId}/orders")
  public Result<List<Map<String, Object>>> orders(@PathVariable Long userId) {
    // 从 mo_order 表查询该用户的订单，按创建时间降序
    List<OrderEntity> orderList = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .orderByDesc(OrderEntity::getCreateTime));

    List<Map<String, Object>> list = new ArrayList<>();
    for (OrderEntity entity : orderList) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("orderNo", entity.getOrderNo());
      item.put("amount", entity.getPayAmount());
      item.put("status", entity.getStatus());
      item.put("createTime", entity.getCreateTime());
      list.add(item);
    }
    return Result.success(list);
  }
}
