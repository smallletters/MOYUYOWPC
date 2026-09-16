package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.UserDeviceEntity;
import com.moyuyo.dao.mapper.UserDeviceMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
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
    Long userId = UserContextHolder.getUserId();
    // 防御性 null 短路:拦截器漏放或未登录请求下 userId 可能为 null,
    // MyBatis-Plus eq(userId, null) 行为是 IS NULL,会返回所有用户的设备记录 → 越权
    if (userId == null) {
      return Result.success(new Page<>(page, size));
    }
    return Result.success(deviceMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getUserId, userId)
            .orderByDesc(UserDeviceEntity::getLastActive)));
  }

  /**
   * 设备 upsert:用 SELECT ... FOR UPDATE 串行化同 (userId, deviceId) 的并发请求,
   * 避免"两次请求都查到 null → 都去 insert → 第二个撞唯一索引 uk_user_device"的 409 场景。
   *
   * 为什么不直接用 INSERT ... ON DUPLICATE KEY UPDATE:
   * - mo_device 实体由 MyBatis-Plus 管理,既有 insert/update 语义对调用方透明;
   * - 保留 selectOne 还能在更新前把当前记录(model/trusted 等)读出来,便于后续字段合并扩展;
   * - FOR UPDATE 行锁 + 唯一索引兜底的双重保护,即可消除生产中观察到的 409。
   *
   * 兜底说明:即便出现间隙锁失效等极端情况导致 insert 仍撞键,
   * catch DataIntegrityViolationException 后回查一次并走 update 路径,
   * 保证幂等成功,不再向上抛 409。
   */
  @PostMapping("/upsert")
  @Transactional
  public Result<UserDeviceEntity> upsert(@RequestBody Map<String, String> body) {
    Long userId = UserContextHolder.getUserId();
    // 防御性 null 短路:未登录请求下 userId 为 null,
    // MyBatis-Plus eq(userId, null) 会以 IS NULL 拼 SQL,
    // 可能误匹配/误改不属于当前请求者的设备记录 → 越权
    if (userId == null) {
      throw new IllegalStateException("未登录");
    }
    String deviceId = body == null ? null : body.get("deviceId");
    if (deviceId == null) throw new IllegalArgumentException("deviceId 不能为空");
    // 行锁:同 (userId, deviceId) 的并发请求在此串行化;
    // 记录不存在时 InnoDB 间隙锁仍能挡住相邻区间的并发 insert。
    UserDeviceEntity exist = deviceMapper.selectOne(
        new LambdaQueryWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getUserId, userId)
            .eq(UserDeviceEntity::getDeviceId, deviceId)
            .last("FOR UPDATE"));
    if (exist == null) {
      exist = new UserDeviceEntity();
      exist.setUserId(userId);
      exist.setDeviceId(deviceId);
      exist.setTrusted(0);
    }
    // 兼容前端字段命名:platform/deviceType 都接受,model/deviceName 都接受
    String platform = body.get("platform");
    if (platform == null) platform = body.get("deviceType");
    if (platform != null) exist.setPlatform(platform);

    String model = body.get("model");
    if (model == null) model = body.get("deviceName");
    if (model != null) exist.setModel(model);

    if (body.get("osVersion") != null) exist.setOsVersion(body.get("osVersion"));
    if (body.get("appVersion") != null) exist.setAppVersion(body.get("appVersion"));
    if (body.get("pushToken") != null) exist.setPushToken(body.get("pushToken"));
    exist.setLastActive(LocalDateTime.now());

    if (exist.getId() == null) {
      try {
        deviceMapper.insert(exist);
      } catch (DataIntegrityViolationException e) {
        // 兜底:理论上 FOR UPDATE 已串行化,这里只兜住间隙锁失效等极小概率事件。
        // 回查后走 update 即可幂等成功,不再抛 409 给前端。
        UserDeviceEntity current = deviceMapper.selectOne(
            new LambdaQueryWrapper<UserDeviceEntity>()
                .eq(UserDeviceEntity::getUserId, userId)
                .eq(UserDeviceEntity::getDeviceId, deviceId));
        if (current == null) throw e;
        applyFields(current, body);
        current.setLastActive(LocalDateTime.now());
        deviceMapper.updateById(current);
        return Result.success(current);
      }
    } else {
      deviceMapper.updateById(exist);
    }
    return Result.success(exist);
  }

  /**
   * 把请求体里的可写字段合并到已存在的实体上,
   * 与 upsert() 主路径的合并逻辑保持一致,用于并发撞键后的兜底 update 路径。
   */
  private void applyFields(UserDeviceEntity target, Map<String, String> body) {
    String platform = body.get("platform");
    if (platform == null) platform = body.get("deviceType");
    if (platform != null) target.setPlatform(platform);

    String model = body.get("model");
    if (model == null) model = body.get("deviceName");
    if (model != null) target.setModel(model);

    if (body.get("osVersion") != null) target.setOsVersion(body.get("osVersion"));
    if (body.get("appVersion") != null) target.setAppVersion(body.get("appVersion"));
    if (body.get("pushToken") != null) target.setPushToken(body.get("pushToken"));
  }

  @DeleteMapping("/{id}")
  public Result<Void> remove(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      throw new IllegalStateException("未登录");
    }
    UserDeviceEntity d = deviceMapper.selectById(id);
    if (d == null || !d.getUserId().equals(userId)) throw new IllegalStateException("无权操作");
    deviceMapper.deleteById(id);
    return Result.success();
  }

  /**
   * 按 deviceId 删除当前用户的设备记录
   * 用途:登出时前端无需先用 deviceList 查 id(避免 store 缓存来自别的账号时误删),
   * 服务端用 (user_id, device_id) 复合条件删除,严格限制为本人设备
   */
  @DeleteMapping("/by-device-id")
  public Result<Void> removeByDeviceId(@RequestParam String deviceId) {
    if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId 不能为空");
    Long userId = UserContextHolder.getUserId();
    if (userId == null) return Result.success();
    deviceMapper.delete(new LambdaQueryWrapper<UserDeviceEntity>()
        .eq(UserDeviceEntity::getUserId, userId)
        .eq(UserDeviceEntity::getDeviceId, deviceId));
    return Result.success();
  }

  @PostMapping("/{id}/trust")
  public Result<Void> setTrust(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      throw new IllegalStateException("未登录");
    }
    UserDeviceEntity d = deviceMapper.selectById(id);
    if (d == null || !d.getUserId().equals(userId)) throw new IllegalStateException("无权操作");
    boolean trusted = Boolean.TRUE.equals(body.get("trusted"));
    deviceMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserDeviceEntity>()
            .eq(UserDeviceEntity::getId, id)
            .set(UserDeviceEntity::getTrusted, trusted ? 1 : 0));
    return Result.success();
  }
}
