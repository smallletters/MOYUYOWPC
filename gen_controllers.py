import os
CONTROLLER_DIR = r'D:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\java\com\moyuyo\api\controller'

CONTROLLERS = {}

# HelpController
CONTROLLERS['HelpController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.HelpArticleEntity;
import com.moyuyo.dao.entity.HelpCategoryEntity;
import com.moyuyo.dao.mapper.HelpArticleMapper;
import com.moyuyo.dao.mapper.HelpCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "帮助中心")
@RestController
@RequestMapping("/api/v1/help")
@RequiredArgsConstructor
public class HelpController {

  private final HelpCategoryMapper categoryMapper;
  private final HelpArticleMapper articleMapper;

  @GetMapping("/categories")
  public Result<List<HelpCategoryEntity>> categories() {
    return Result.success(categoryMapper.selectList(
        new LambdaQueryWrapper<HelpCategoryEntity>()
            .eq(HelpCategoryEntity::getActive, 1)
            .orderByAsc(HelpCategoryEntity::getSortOrder)));
  }

  @GetMapping("/articles")
  public Result<Page<HelpArticleEntity>> articles(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String keyword) {
    LambdaQueryWrapper<HelpArticleEntity> q = new LambdaQueryWrapper<HelpArticleEntity>()
        .eq(HelpArticleEntity::getStatus, 1)
        .orderByAsc(HelpArticleEntity::getSortOrder);
    if (categoryId != null) q.eq(HelpArticleEntity::getCategoryId, categoryId);
    if (keyword != null && !keyword.isBlank()) q.like(HelpArticleEntity::getTitle, keyword);
    return Result.success(articleMapper.selectPage(new Page<>(page, size), q));
  }

  @GetMapping("/articles/{id}")
  public Result<HelpArticleEntity> articleDetail(@PathVariable Long id) {
    HelpArticleEntity a = articleMapper.selectById(id);
    if (a != null) {
      articleMapper.update(null,
          new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HelpArticleEntity>()
              .eq(HelpArticleEntity::getId, id)
              .setSql("view_count = view_count + 1"));
    }
    return Result.success(a);
  }

  @PostMapping("/articles/{id}/helpful")
  public Result<Void> helpful(@PathVariable Long id) {
    articleMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HelpArticleEntity>()
            .eq(HelpArticleEntity::getId, id)
            .setSql("helpful_count = helpful_count + 1"));
    return Result.success();
  }
}
'''

# DeviceController
CONTROLLERS['DeviceController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.UserDeviceEntity;
import com.moyuyo.dao.mapper.UserDeviceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

  private final UserDeviceMapper deviceMapper;

  @GetMapping
  public Result<Page<UserDeviceEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(deviceMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(UserDeviceEntity::getLoginTime)));
  }

  @PostMapping("/upsert")
  public Result<UserDeviceEntity> upsert(@RequestBody Map<String, String> body) {
    Long userId = UserContextHolder.getUserId();
    String deviceId = body == null ? null : body.get("deviceId");
    if (deviceId == null) throw new IllegalArgumentException("deviceId 不能为空");
    UserDeviceEntity exist = deviceMapper.selectOne(
        new LambdaQueryWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getUserId, userId)
            .eq(UserDeviceEntity::getDeviceId, deviceId));
    if (exist == null) {
      exist = new UserDeviceEntity();
      exist.setUserId(userId);
      exist.setDeviceId(deviceId);
    }
    exist.setDeviceName(body.get("deviceName"));
    exist.setDeviceType(body.get("deviceType"));
    exist.setOsVersion(body.get("osVersion"));
    exist.setAppVersion(body.get("appVersion"));
    exist.setIpAddress(body.get("ipAddress"));
    exist.setLocation(body.get("location"));
    exist.setLastActive(LocalDateTime.now());
    if (exist.getId() == null) {
      exist.setTrusted(0);
      deviceMapper.insert(exist);
    } else {
      deviceMapper.updateById(exist);
    }
    return Result.success(exist);
  }

  @DeleteMapping("/{id}")
  public Result<Void> remove(@PathVariable Long id) {
    UserDeviceEntity d = deviceMapper.selectById(id);
    if (d == null || !d.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalStateException("无权操作");
    deviceMapper.deleteById(id);
    return Result.success();
  }

  @PostMapping("/{id}/trust")
  public Result<Void> setTrust(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    UserDeviceEntity d = deviceMapper.selectById(id);
    if (d == null || !d.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalStateException("无权操作");
    boolean trusted = Boolean.TRUE.equals(body.get("trusted"));
    deviceMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getId, id)
            .set(UserDeviceEntity::getTrusted, trusted ? 1 : 0));
    return Result.success();
  }
}
'''

# FollowController
CONTROLLERS['FollowController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "关注/好友")
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowMapper followMapper;

  @PostMapping
  public Result<Void> follow(@RequestBody Map<String, Long> body) {
    Long userId = UserContextHolder.getUserId();
    Long targetId = body == null ? null : body.get("targetId");
    if (targetId == null || targetId.equals(userId)) throw new IllegalArgumentException("目标用户无效");
    FollowEntity exist = followMapper.selectOne(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, userId)
            .eq(FollowEntity::getTargetId, targetId));
    if (exist != null) return Result.success();
    FollowEntity f = new FollowEntity();
    f.setUserId(userId);
    f.setTargetId(targetId);
    f.setStatus("FOLLOWING");
    followMapper.insert(f);
    return Result.success();
  }

  @DeleteMapping("/{targetId}")
  public Result<Void> unfollow(@PathVariable Long targetId) {
    followMapper.delete(new LambdaQueryWrapper<FollowEntity>()
        .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
        .eq(FollowEntity::getTargetId, targetId));
    return Result.success();
  }

  @GetMapping("/{targetId}/status")
  public Result<Map<String, Boolean>> status(@PathVariable Long targetId) {
    long count = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
            .eq(FollowEntity::getTargetId, targetId));
    Map<String, Boolean> r = new HashMap<>();
    r.put("following", count > 0);
    return Result.success(r);
  }

  @GetMapping("/following")
  public Result<List<FollowEntity>> following() {
    return Result.success(followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
            .eq(FollowEntity::getStatus, "FOLLOWING")
            .orderByDesc(FollowEntity::getCreateTime)));
  }

  @GetMapping("/followers")
  public Result<List<FollowEntity>> followers() {
    return Result.success(followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getTargetId, UserContextHolder.getUserId())
            .orderByDesc(FollowEntity::getCreateTime)));
  }
}
'''

# UserController 公共主页
CONTROLLERS['UserController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户主页")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserMapper userMapper;
  private final FollowMapper followMapper;

  @GetMapping("/{id}/profile")
  public Result<Map<String, Object>> profile(@PathVariable Long id) {
    UserEntity u = userMapper.selectById(id);
    if (u == null) throw new IllegalArgumentException("用户不存在");
    long following = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getUserId, id));
    long followers = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getTargetId, id));
    Map<String, Object> p = new HashMap<>();
    p.put("id", u.getId());
    p.put("nickname", u.getNickname());
    p.put("avatar", u.getAvatar());
    p.put("country", u.getCountry());
    p.put("gender", u.getGender());
    p.put("bio", u.getBio());
    p.put("points", u.getPoints());
    p.put("following", following);
    p.put("followers", followers);
    p.put("isFollowing", false);
    return Result.success(p);
  }
}
'''

# BlockController
CONTROLLERS['BlockController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.BlockEntity;
import com.moyuyo.dao.mapper.BlockMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "黑名单")
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

  private final BlockMapper blockMapper;

  @GetMapping
  public Result<Page<BlockEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(blockMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<BlockEntity>()
            .eq(BlockEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(BlockEntity::getCreateTime)));
  }

  @PostMapping
  public Result<Void> block(@RequestBody Map<String, Object> body) {
    Long userId = UserContextHolder.getUserId();
    Long targetId = body == null ? null : ((Number) body.get("targetId")).longValue();
    if (targetId == null || targetId.equals(userId)) throw new IllegalArgumentException("目标用户无效");
    BlockEntity exist = blockMapper.selectOne(
        new LambdaQueryWrapper<BlockEntity>()
            .eq(BlockEntity::getUserId, userId)
            .eq(BlockEntity::getTargetId, targetId));
    if (exist != null) return Result.success();
    BlockEntity b = new BlockEntity();
    b.setUserId(userId);
    b.setTargetId(targetId);
    b.setReason((String) body.get("reason"));
    blockMapper.insert(b);
    return Result.success();
  }

  @DeleteMapping("/{targetId}")
  public Result<Void> unblock(@PathVariable Long targetId) {
    blockMapper.delete(new LambdaQueryWrapper<BlockEntity>()
        .eq(BlockEntity::getUserId, UserContextHolder.getUserId())
        .eq(BlockEntity::getTargetId, targetId));
    return Result.success();
  }
}
'''

# PrimeController
CONTROLLERS['PrimeController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.PrimePlanEntity;
import com.moyuyo.dao.mapper.PrimePlanMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Prime 会员")
@RestController
@RequestMapping("/api/v1/prime")
@RequiredArgsConstructor
public class PrimeController {

  private final PrimePlanMapper planMapper;

  @GetMapping("/plans")
  public Result<List<PrimePlanEntity>> plans() {
    return Result.success(planMapper.selectList(
        new LambdaQueryWrapper<PrimePlanEntity>()
            .eq(PrimePlanEntity::getActive, 1)
            .orderByAsc(PrimePlanEntity::getSortOrder)));
  }

  @GetMapping("/plans/{id}")
  public Result<PrimePlanEntity> planDetail(@PathVariable Long id) {
    return Result.success(planMapper.selectById(id));
  }
}
'''

# AffiliateController
CONTROLLERS['AffiliateController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.AffiliateAccountEntity;
import com.moyuyo.dao.entity.AffiliateCommissionEntity;
import com.moyuyo.dao.mapper.AffiliateAccountMapper;
import com.moyuyo.dao.mapper.AffiliateCommissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "推广分销")
@RestController
@RequestMapping("/api/v1/affiliate")
@RequiredArgsConstructor
public class AffiliateController {

  private final AffiliateAccountMapper accountMapper;
  private final AffiliateCommissionMapper commissionMapper;

  @GetMapping("/account")
  public Result<AffiliateAccountEntity> myAccount() {
    Long userId = UserContextHolder.getUserId();
    AffiliateAccountEntity acc = accountMapper.selectOne(
        new LambdaQueryWrapper<AffiliateAccountEntity>()
            .eq(AffiliateAccountEntity::getUserId, userId));
    if (acc == null) {
      acc = new AffiliateAccountEntity();
      acc.setUserId(userId);
      acc.setLevel("BRONZE");
      acc.setStatus("ACTIVE");
      accountMapper.insert(acc);
    }
    return Result.success(acc);
  }

  @GetMapping("/commissions")
  public Result<Page<AffiliateCommissionEntity>> commissions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(commissionMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<AffiliateCommissionEntity>()
            .eq(AffiliateCommissionEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(AffiliateCommissionEntity::getCreateTime)));
  }
}
'''

# BookingController
CONTROLLERS['BookingController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.ServiceBookingEntity;
import com.moyuyo.dao.mapper.ServiceBookingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "服务预约")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final ServiceBookingMapper bookingMapper;

  @GetMapping
  public Result<Page<ServiceBookingEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(bookingMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<ServiceBookingEntity>()
            .eq(ServiceBookingEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(ServiceBookingEntity::getBookingDate)));
  }

  @GetMapping("/{id}")
  public Result<ServiceBookingEntity> detail(@PathVariable Long id) {
    ServiceBookingEntity b = bookingMapper.selectById(id);
    if (b == null || !b.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalArgumentException("预约不存在");
    return Result.success(b);
  }

  @PostMapping
  public Result<ServiceBookingEntity> create(@RequestBody Map<String, Object> body) {
    ServiceBookingEntity b = new ServiceBookingEntity();
    b.setUserId(UserContextHolder.getUserId());
    if (body.get("petId") != null) b.setPetId(((Number) body.get("petId")).longValue());
    b.setServiceType((String) body.get("serviceType"));
    b.setBookingDate(java.time.LocalDate.parse((String) body.get("bookingDate")));
    b.setBookingTime(java.time.LocalTime.parse((String) body.get("bookingTime")));
    b.setDurationMin(body.get("durationMin") == null ? 60 : ((Number) body.get("durationMin")).intValue());
    b.setAddress((String) body.get("address"));
    b.setContactPhone((String) body.get("contactPhone"));
    b.setNotes((String) body.get("notes"));
    b.setPrice(new java.math.BigDecimal(String.valueOf(body.getOrDefault("price", "0"))));
    b.setStatus("PENDING");
    bookingMapper.insert(b);
    return Result.success(b);
  }

  @PostMapping("/{id}/cancel")
  public Result<Void> cancel(@PathVariable Long id) {
    ServiceBookingEntity b = bookingMapper.selectById(id);
    if (b == null || !b.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalArgumentException("预约不存在");
    b.setStatus("CANCELLED");
    bookingMapper.updateById(b);
    return Result.success();
  }
}
'''

# FestivalController
CONTROLLERS['FestivalController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FestivalEventEntity;
import com.moyuyo.dao.mapper.FestivalEventMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "节日活动")
@RestController
@RequestMapping("/api/v1/festivals")
@RequiredArgsConstructor
public class FestivalController {

  private final FestivalEventMapper eventMapper;

  @GetMapping("/active")
  public Result<List<FestivalEventEntity>> active() {
    LocalDateTime now = LocalDateTime.now();
    return Result.success(eventMapper.selectList(
        new LambdaQueryWrapper<FestivalEventEntity>()
            .eq(FestivalEventEntity::getActive, 1)
            .le(FestivalEventEntity::getStartTime, now)
            .ge(FestivalEventEntity::getEndTime, now)
            .orderByDesc(FestivalEventEntity::getStartTime)));
  }

  @GetMapping("/{id}")
  public Result<FestivalEventEntity> detail(@PathVariable Long id) {
    return Result.success(eventMapper.selectById(id));
  }
}
'''

# NewuserGiftController
CONTROLLERS['NewuserGiftController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.NewuserGiftClaimEntity;
import com.moyuyo.dao.entity.NewuserGiftEntity;
import com.moyuyo.dao.mapper.NewuserGiftClaimMapper;
import com.moyuyo.dao.mapper.NewuserGiftMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "新人礼包")
@RestController
@RequestMapping("/api/v1/newuser")
@RequiredArgsConstructor
public class NewuserGiftController {

  private final NewuserGiftMapper giftMapper;
  private final NewuserGiftClaimMapper claimMapper;

  @GetMapping("/gifts")
  public Result<List<NewuserGiftEntity>> gifts() {
    return Result.success(giftMapper.selectList(
        new LambdaQueryWrapper<NewuserGiftEntity>()
            .eq(NewuserGiftEntity::getActive, 1)));
  }

  @PostMapping("/gifts/{id}/claim")
  @Transactional
  public Result<NewuserGiftClaimEntity> claim(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    NewuserGiftEntity gift = giftMapper.selectById(id);
    if (gift == null || gift.getActive() != 1) throw new IllegalArgumentException("礼包不存在或已下架");
    NewuserGiftClaimEntity exist = claimMapper.selectOne(
        new LambdaQueryWrapper<NewuserGiftClaimEntity>()
            .eq(NewuserGiftClaimEntity::getUserId, userId)
            .eq(NewuserGiftClaimEntity::getGiftId, id));
    if (exist != null) return Result.success(exist);
    NewuserGiftClaimEntity c = new NewuserGiftClaimEntity();
    c.setUserId(userId);
    c.setGiftId(id);
    c.setStatus("CLAIMED");
    c.setExpireAt(LocalDate.now().plusDays(gift.getClaimWindowDays() == null ? 30 : gift.getClaimWindowDays()).atStartOfDay());
    claimMapper.insert(c);
    return Result.success(c);
  }

  @GetMapping("/my")
  public Result<Page<NewuserGiftClaimEntity>> myClaims() {
    return Result.success(claimMapper.selectPage(new Page<>(1, 20),
        new LambdaQueryWrapper<NewuserGiftClaimEntity>()
            .eq(NewuserGiftClaimEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(NewuserGiftClaimEntity::getClaimTime)));
  }
}
'''

# AchievementController
CONTROLLERS['AchievementController'] = '''package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.AchievementEntity;
import com.moyuyo.dao.entity.UserAchievementEntity;
import com.moyuyo.dao.mapper.AchievementMapper;
import com.moyuyo.dao.mapper.UserAchievementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "成就墙")
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

  private final AchievementMapper achievementMapper;
  private final UserAchievementMapper userAchievementMapper;

  @GetMapping
  public Result<List<AchievementEntity>> all() {
    return Result.success(achievementMapper.selectList(
        new LambdaQueryWrapper<AchievementEntity>()
            .eq(AchievementEntity::getActive, 1)
            .orderByAsc(AchievementEntity::getSortOrder)));
  }

  @GetMapping("/my")
  public Result<Map<String, Object>> myWall() {
    Long userId = UserContextHolder.getUserId();
    List<UserAchievementEntity> mine = userAchievementMapper.selectList(
        new LambdaQueryWrapper<UserAchievementEntity>()
            .eq(UserAchievementEntity::getUserId, userId));
    Set<Long> unlocked = mine.stream()
        .map(UserAchievementEntity::getAchievementId)
        .collect(Collectors.toSet());

    List<AchievementEntity> all = achievementMapper.selectList(
        new LambdaQueryWrapper<AchievementEntity>()
            .eq(AchievementEntity::getActive, 1)
            .orderByAsc(AchievementEntity::getSortOrder));

    List<Map<String, Object>> items = new ArrayList<>();
    for (AchievementEntity a : all) {
      Map<String, Object> item = new HashMap<>();
      item.put("id", a.getId());
      item.put("code", a.getCode());
      item.put("name", a.getName());
      item.put("description", a.getDescription());
      item.put("icon", a.getIcon());
      item.put("badgeImage", a.getBadgeImage());
      item.put("pointsReward", a.getPointsReward());
      item.put("category", a.getCategory());
      item.put("unlocked", unlocked.contains(a.getId()));
      item.put("unlockedAt", mine.stream()
          .filter(u -> u.getAchievementId().equals(a.getId()))
          .findFirst()
          .map(UserAchievementEntity::getUnlockedAt)
          .orElse(null));
      items.add(item);
    }
    Map<String, Object> result = new HashMap<>();
    result.put("total", all.size());
    result.put("unlocked", unlocked.size());
    result.put("items", items);
    return Result.success(result);
  }
}
'''

# ReportController
CONTROLLERS['ReportController'] = '''package com.moyuyo.api.controller;

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
    if (u != null && u.getCreateTime() != null) {
      daysWithUs = ChronoUnit.DAYS.between(u.getCreateTime().toLocalDate(), java.time.LocalDate.now());
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
'''

for name, content in CONTROLLERS.items():
    path = os.path.join(CONTROLLER_DIR, f'{name}.java')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content.strip() + '\n')
    print(f'created: {name}.java')
