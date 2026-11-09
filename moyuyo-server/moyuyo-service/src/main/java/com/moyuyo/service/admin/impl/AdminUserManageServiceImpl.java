package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminUserManageService;
import com.moyuyo.service.event.UserTokenInvalidatedEvent;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManageServiceImpl implements AdminUserManageService {

  /** Redis 封禁状态 key 前缀：供 JwtAuthFilter 快速拦截被封禁用户（值为封禁提示文案） */
  public static final String REDIS_KEY_USER_BAN = "auth:user:ban:";

  /** C 端 token 版本号 key（与 JwtAuthFilter / AuthServiceImpl 保持一致） */
  private static final String REDIS_KEY_TOKEN_VERSION = "auth:token-version:";
  /** refresh token 及其反向索引 key（与管理端重置用户密码时吊销会话使用） */
  private static final String REDIS_KEY_USER_REFRESH = "auth:user-refresh:";
  private static final String REDIS_KEY_REFRESH = "auth:refresh:";

  private static final DateTimeFormatter BAN_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final UserMapper userMapper;
  private final MemberMapper memberMapper;
  private final PasswordEncoder passwordEncoder;
  private final StringRedisTemplate redisTemplate;
  /** 事件发布器：管理端重置用户密码后，通知 api 模块关闭该用户的 WebSocket 会话 */
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Map<String, Object> getStats() {
    LocalDate today = LocalDate.now();

    // 总用户数
    Long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<>());

    // 今日新增用户（注册时间在今天）
    Long newToday = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getCreatedAt, today)
    );

    // 活跃用户（今日有登录记录）
    Long activeToday = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getLastLoginTime, today)
    );

    // 总会员数
    Long totalMembers = memberMapper.selectCount(new LambdaQueryWrapper<>());

    // 今日新增会员
    Long newMembersToday = memberMapper.selectCount(
        new LambdaQueryWrapper<MemberEntity>()
            .ge(MemberEntity::getCreateTime, today)
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalUsers", totalUsers);
    result.put("newToday", newToday);
    result.put("activeToday", activeToday);
    result.put("totalMembers", totalMembers);
    result.put("newMembersToday", newMembersToday);
    return result;
  }

  @Override
  public Map<String, Object> listUsers(int page, int size, String search, String level, String channel, String status) {
    // 构建查询条件
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

    // 关键词搜索（昵称、邮箱）
    if (search != null && !search.isEmpty()) {
      wrapper.and(w -> w
          .like(UserEntity::getNickname, search)
          .or()
          .like(UserEntity::getEmail, search)
      );
    }

    // 按状态筛选
    if (status != null && !status.isEmpty()) {
      wrapper.eq(UserEntity::getStatus, ACTIVE.name().equals(status) ? 1 : 0);
    }

    // 按会员等级筛选（修复：之前未生效，level 参数是死参数）
    // mo_member.level 是用户的会员等级，需要 EXISTS 子查询在分页阶段完成过滤
    // 否则 total 计数会与过滤后数据不一致
    if (level != null && !level.isEmpty()) {
      wrapper.exists("SELECT 1 FROM mo_member WHERE mo_member.user_id = mo_user.id AND mo_member.level = {0}", level);
    }

    // 按注册渠道筛选（修复：之前未生效，channel 参数是死参数）
    // V20260819_02 迁移新增 mo_user.registration_channel 字段
    if (channel != null && !channel.isEmpty()) {
      wrapper.eq(UserEntity::getRegistrationChannel, channel);
    }

    // 按创建时间倒序
    wrapper.orderByDesc(UserEntity::getCreatedAt);

    // 执行分页查询
    Page<UserEntity> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);

    // 收集所有用户ID，批量查询会员信息
    List<Long> userIds = userPage.getRecords().stream()
        .map(UserEntity::getId)
        .collect(Collectors.toList());

    // 查询会员信息（按user_id分组）
    Map<Long, MemberEntity> memberMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      List<MemberEntity> members = memberMapper.selectList(
          new LambdaQueryWrapper<MemberEntity>()
              .in(MemberEntity::getUserId, userIds)
      );
      for (MemberEntity m : members) {
        memberMap.put(m.getUserId(), m);
      }
    }

    // 组装返回数据
    List<Map<String, Object>> list = new ArrayList<>();
    for (UserEntity user : userPage.getRecords()) {
      MemberEntity member = memberMap.get(user.getId());

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", user.getId());
      item.put("nickname", user.getNickname());
      item.put("email", user.getEmail() != null ? user.getEmail() : "");
      item.put("phone", user.getPhone() != null ? user.getPhone() : "");
      item.put("level", member != null && member.getLevel() != null ? member.getLevel().name() : "NORMAL");
      item.put("status", user.getStatus() != null && user.getStatus() == 1 ? ACTIVE.name() : INACTIVE.name());
      // 封禁信息：管理端列表展示封禁至何时 / 永久封禁及原因
      item.put("banReason", user.getBanReason() != null ? user.getBanReason() : "");
      item.put("bannedAt", user.getBannedAt() != null
          ? BAN_TIME_FORMATTER.format(user.getBannedAt()) : "");
      item.put("banExpiresAt", user.getBanExpiresAt() != null
          ? BAN_TIME_FORMATTER.format(user.getBanExpiresAt()) : "");
      item.put("registerTime", user.getCreatedAt() != null ? user.getCreatedAt().toString().replace("T", " ") : "");
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", userPage.getTotal());
    result.put("page", (long) page);
    result.put("size", (long) size);
    return result;
  }

  @Override
  public Map<String, Object> getUserDetail(Long id) {
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", user.getId());
    detail.put("nickname", user.getNickname());
    detail.put("email", user.getEmail() != null ? user.getEmail() : "");
    detail.put("phone", user.getPhone() != null ? user.getPhone() : "");
    detail.put("status", user.getStatus() != null && user.getStatus() == 1 ? ACTIVE.name() : INACTIVE.name());
    detail.put("banReason", user.getBanReason() != null ? user.getBanReason() : "");
    detail.put("bannedAt", user.getBannedAt() != null
        ? BAN_TIME_FORMATTER.format(user.getBannedAt()) : "");
    detail.put("banExpiresAt", user.getBanExpiresAt() != null
        ? BAN_TIME_FORMATTER.format(user.getBanExpiresAt()) : "");
    detail.put("registerTime", user.getCreatedAt() != null ? user.getCreatedAt().toString().replace("T", " ") : "");
    detail.put("lastLoginTime", user.getLastLoginTime() != null ? user.getLastLoginTime().toString().replace("T", " ") : "");

    // 查询会员信息
    MemberEntity member = memberMapper.selectOne(
        new LambdaQueryWrapper<MemberEntity>().eq(MemberEntity::getUserId, id));
    if (member != null) {
      detail.put("level", member.getLevel() != null ? member.getLevel().name() : "NORMAL");
      detail.put("points", user.getPoints() != null ? user.getPoints() : 0);
    } else {
      detail.put("level", "NORMAL");
      detail.put("points", 0);
    }
    return detail;
  }

  @Override
  @Transactional
  public void updateUserStatus(Long id, String status, String banReason, Integer banDurationDays) {
    if (id == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("状态参数不能为空");
    }
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    LocalDateTime now = LocalDateTime.now();

    // 解封：恢复启用状态并清空封禁信息 + 删除 Redis 封禁标记
    if (ACTIVE.name().equals(status)) {
      userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
          .eq(UserEntity::getId, id)
          .set(UserEntity::getStatus, 1)
          .set(UserEntity::getBanReason, null)
          .set(UserEntity::getBannedAt, null)
          .set(UserEntity::getBanExpiresAt, null));
      clearBanCache(id);
      log.info("[admin-user] unban: userId={}", id);
      return;
    }

    // 封禁：原因必填；banDurationDays > 0 为临时封禁，0/null 为永久封禁
    String reason = banReason == null ? "" : banReason.trim();
    if (reason.isEmpty()) {
      throw new IllegalArgumentException("封禁原因不能为空");
    }
    if (reason.length() > 255) {
      reason = reason.substring(0, 255);
    }
    int days = banDurationDays == null ? 0 : banDurationDays;
    if (days < 0) {
      throw new IllegalArgumentException("封禁天数无效");
    }
    LocalDateTime expiresAt = days > 0 ? now.plusDays(days) : null;
    userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
        .eq(UserEntity::getId, id)
        .set(UserEntity::getStatus, 0)
        .set(UserEntity::getBanReason, reason)
        .set(UserEntity::getBannedAt, now)
        .set(UserEntity::getBanExpiresAt, expiresAt));

    // 同步 Redis 封禁标记：JwtAuthFilter 据此拦截已登录用户，并下发封禁提示
    String message = buildBanMessage(reason, expiresAt);
    cacheBan(id, message, expiresAt, now);
    log.info("[admin-user] ban: userId={}, days={}, reason={}", id, days, reason);
  }

  /**
   * 构建 C 端可见的封禁提示（登录接口与 JwtAuthFilter 共用同一文案）
   */
  public static String buildBanMessage(String reason, LocalDateTime expiresAt) {
    String safeReason = reason == null || reason.isBlank() ? "违反社区规范" : reason.trim();
    if (expiresAt != null) {
      return "账号已被封禁，解封时间：" + BAN_TIME_FORMATTER.format(expiresAt) + "。原因：" + safeReason;
    }
    return "账号已被永久封禁。原因：" + safeReason;
  }

  /** 写入 Redis 封禁标记：临时封禁设置 TTL，永久封禁不设 TTL */
  private void cacheBan(Long userId, String message, LocalDateTime expiresAt, LocalDateTime now) {
    String key = REDIS_KEY_USER_BAN + userId;
    try {
      if (expiresAt != null) {
        long seconds = Duration.between(now, expiresAt).getSeconds();
        if (seconds <= 0) return;
        redisTemplate.opsForValue().set(key, message, Duration.ofSeconds(seconds));
      } else {
        redisTemplate.opsForValue().set(key, message);
      }
    } catch (DataAccessException e) {
      // 缓存失败不阻断封禁落库：登录路径仍会读库拦截；已登录用户下次刷新 token 时被拦截
      log.error("[admin-user] 写入封禁缓存失败: userId={}", userId, e);
    }
  }

  /** 解封时删除 Redis 封禁标记 */
  private void clearBanCache(Long userId) {
    try {
      redisTemplate.delete(REDIS_KEY_USER_BAN + userId);
    } catch (DataAccessException e) {
      log.error("[admin-user] 删除封禁缓存失败: userId={}", userId, e);
    }
  }

  @Override
  @Transactional
  public Map<String, Object> createUser(Map<String, Object> body) {
    String email = (String) body.get("email");
    String nickname = (String) body.getOrDefault("nickname", "");
    String password = (String) body.getOrDefault("password", "123456");

    if (email == null || email.isEmpty()) {
      throw new IllegalArgumentException("邮箱不能为空");
    }

    // 检查邮箱是否已存在
    UserEntity existing = userMapper.selectOne(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
    if (existing != null) {
      throw new IllegalArgumentException("该邮箱已被注册");
    }

    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setNickname(nickname.isEmpty() ? email.split("@")[0] : nickname);
    // P0 安全修复:必须用 BCrypt 加密后存储,否则明文密码会让登录验证失败
    // (BCryptPasswordEncoder.matches() 永远不匹配明文 hash)
    // 与 resetPassword() 对齐使用同一个 passwordEncoder
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setStatus(1); // 默认启用
    user.setCreatedAt(LocalDateTime.now());
    userMapper.insert(user);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", user.getId());
    result.put("message", "用户创建成功");
    return result;
  }

  @Override
  @Transactional
  public void updateUser(Long id, Map<String, Object> body) {
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    if (body.containsKey("nickname")) {
      user.setNickname((String) body.get("nickname"));
    }
    if (body.containsKey("email")) {
      user.setEmail((String) body.get("email"));
    }
    if (body.containsKey("phone")) {
      user.setPhone((String) body.get("phone"));
    }
    userMapper.updateById(user);
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    userMapper.deleteById(id);
    // 同时删除关联的会员记录
    memberMapper.delete(new LambdaQueryWrapper<MemberEntity>().eq(MemberEntity::getUserId, id));
  }

  @Override
  @Transactional
  public void resetPassword(Long id, String newPassword) {
    if (newPassword == null || newPassword.isEmpty()) {
      throw new IllegalArgumentException("新密码不能为空");
    }
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    // P0 安全修复:必须用 BCrypt 加密后存储,否则明文密码会导致登录验证失败
    // (BCryptPasswordEncoder.matches() 永远不匹配明文)
    String hashed = passwordEncoder.encode(newPassword);
    user.setPasswordHash(hashed);
    int updated = userMapper.updateById(user);

    // 管理端重置用户密码后，必须让该用户所有旧会话立即失效：
    // 1) 递增 token 版本 → 旧 access token 被 JwtAuthFilter 拒绝
    // 2) 吊销 refresh token 反向索引 → 旧 refresh token 不能再换新 access token
    bumpTokenVersionOrThrow(id);
    revokeUserRefreshTokensOrThrow(id);
    // 事务提交后由 api 模块监听并强制关闭该用户已建立的 WebSocket 长连接
    try {
      eventPublisher.publishEvent(new UserTokenInvalidatedEvent(id, "ADMIN_RESET_PASSWORD"));
    } catch (Exception e) {
      // 事件失败不影响重置密码主流程
      log.error("[admin-user] 发布 token 失效事件失败，userId={}", id, e);
    }

    log.info("[admin-user] resetPassword: userId={}, updatedRows={}", id, updated);
  }

  /** 递增用户 token 版本号；失败抛异常让事务回滚，避免"密码已改但旧 token 仍有效" */
  private void bumpTokenVersionOrThrow(Long userId) {
    try {
      redisTemplate.opsForValue().increment(REDIS_KEY_TOKEN_VERSION + userId);
    } catch (DataAccessException e) {
      log.error("[admin-user] 递增 token version 失败，中止重置密码: userId={}", userId, e);
      throw new IllegalStateException("系统繁忙，请稍后再试", e);
    }
  }

  /** 吊销用户全部 refresh token；失败抛异常让事务回滚，确保旧会话不能续期 */
  private void revokeUserRefreshTokensOrThrow(Long userId) {
    try {
      String userRefreshKey = REDIS_KEY_USER_REFRESH + userId;
      java.util.Set<String> tokens = redisTemplate.opsForSet().members(userRefreshKey);
      if (tokens != null && !tokens.isEmpty()) {
        redisTemplate.delete(tokens.stream()
            .map(token -> REDIS_KEY_REFRESH + token)
            .collect(Collectors.toList()));
      }
      redisTemplate.delete(userRefreshKey);
    } catch (DataAccessException e) {
      log.error("[admin-user] 吊销 refresh token 失败，中止重置密码: userId={}", userId, e);
      throw new IllegalStateException("系统繁忙，请稍后再试", e);
    }
  }
}
