package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.userprofile.UserBehaviorResponse;
import com.moyuyo.common.dto.admin.userprofile.UserProfileResponse;
import com.moyuyo.dao.admin.entity.UserBehaviorEntity;
import com.moyuyo.dao.admin.mapper.UserBehaviorMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.AdminUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 用户画像")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/user-profile")
public class AdminUserProfileController {

  private final AdminUserProfileService adminUserProfileService;
  private final OrderMapper orderMapper;
  private final UserBehaviorMapper userBehaviorMapper;

  @Operation(summary = "用户画像信息")
  @GetMapping("/{userId}")
  public Result<UserProfileResponse> profile(@PathVariable Long userId) {
    try {
      Map<String, Object> svcResult = adminUserProfileService.getDetail(userId);
      if (svcResult == null || svcResult.isEmpty()) {
        return Result.error(404, "用户不存在");
      }
      UserProfileResponse resp = new UserProfileResponse();
      resp.setUserId((Long) svcResult.get("userId"));
      resp.setNickname((String) svcResult.get("nickname"));
      resp.setAvatar((String) svcResult.getOrDefault("avatar", null));
      resp.setEmail((String) svcResult.get("email"));
      resp.setPhone((String) svcResult.get("phone"));
      resp.setOrderCount(((Number) svcResult.getOrDefault("orderCount", 0)).intValue());
      resp.setRegisterTime((String) svcResult.get("registerTime"));
      Object totalSpentVal = svcResult.get("totalSpent");
      resp.setTotalSpent(totalSpentVal != null ? ((Number) totalSpentVal).intValue() : 0);
      // 性别 / 年龄：直接透传 Service 计算结果（null 表示用户未填写，保持前端体验一致）
      resp.setGender((String) svcResult.get("gender"));
      Object ageVal = svcResult.get("age");
      resp.setAge(ageVal != null ? ((Number) ageVal).intValue() : null);
      return Result.success(resp);
    } catch (IllegalArgumentException e) {
      return Result.error(404, e.getMessage());
    } catch (Exception e) {
      return Result.error("查询用户画像失败: " + e.getMessage());
    }
  }

  @Operation(summary = "用户行为数据")
  @GetMapping("/{userId}/behaviors")
  public Result<List<UserBehaviorResponse>> behaviors(@PathVariable Long userId) {
    // 从 mo_user_behavior 表查询该用户的行为数据
    List<UserBehaviorEntity> behaviorList = userBehaviorMapper.selectList(
      new LambdaQueryWrapper<UserBehaviorEntity>()
        .eq(UserBehaviorEntity::getUserId, userId));

    List<UserBehaviorResponse> list = new ArrayList<>();
    for (UserBehaviorEntity entity : behaviorList) {
      UserBehaviorResponse item = new UserBehaviorResponse();
      item.setBehaviorType(entity.getBehaviorType());
      item.setCount(entity.getCount());
      item.setLastTime(entity.getLastTime() != null ? entity.getLastTime().toString() : null);
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
