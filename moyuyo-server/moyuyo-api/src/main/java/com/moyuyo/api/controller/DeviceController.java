package com.moyuyo.api.controller;

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
