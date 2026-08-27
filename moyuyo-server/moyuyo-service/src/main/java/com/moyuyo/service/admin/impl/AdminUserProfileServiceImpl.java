package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.userprofile.UserVisitedPageResponse;
import com.moyuyo.common.dto.admin.userprofile.UserVisitedProductResponse;
import com.moyuyo.dao.admin.entity.VisitLogEntity;
import com.moyuyo.dao.admin.mapper.VisitLogMapper;
import com.moyuyo.dao.entity.BrowsingHistoryEntity;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.BrowsingHistoryMapper;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.service.admin.AdminUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台用户画像服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserProfileServiceImpl implements AdminUserProfileService {

  private final UserMapper userMapper;
  private final OrderMapper orderMapper;
  private final MemberMapper memberMapper;
  private final BrowsingHistoryMapper browsingHistoryMapper;
  private final ProductMapper productMapper;
  private final VisitLogMapper visitLogMapper;

  @Override
  public Page<UserEntity> listAll(String keyword, Integer status, int page, int size) {
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(UserEntity::getNickname, keyword)
          .or().like(UserEntity::getEmail, keyword)
          .or().like(UserEntity::getPhone, keyword);
    }
    if (status != null) {
      wrapper.eq(UserEntity::getStatus, status);
    }
    wrapper.orderByDesc(UserEntity::getCreatedAt);
    return userMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 用户统计
    Long total = userMapper.selectCount(new LambdaQueryWrapper<>());
    Long active = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, 1));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("active", active);
    result.put("inactive", total - active);
    return result;
  }

  @Override
  public Map<String, Object> getDetail(Long id) {
    // 查询用户基本信息
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }

    // 查询用户的订单数
    Long orderCount = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getUserId, id));
    // Java 端聚合已完成订单的总金额
    List<OrderEntity> completedOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, id)
            .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.name()));
    java.math.BigDecimal totalConsumption = completedOrders.stream()
        .map(OrderEntity::getPayAmount)
        .filter(Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

    // 查询会员信息（无记录时兜底默认 NORMAL）
    MemberEntity member = memberMapper.selectOne(
        new LambdaQueryWrapper<MemberEntity>().eq(MemberEntity::getUserId, id));
    String memberLevel = (member != null && member.getLevel() != null) ? member.getLevel().name() : "NORMAL";
    int growthValue = (member != null && member.getGrowthValue() != null) ? member.getGrowthValue() : 0;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userId", user.getId());
    result.put("nickname", user.getNickname());
    result.put("avatar", user.getAvatar());
    result.put("email", user.getEmail());
    result.put("phone", user.getPhone());
    result.put("status", user.getStatus());
    result.put("registerTime", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
    result.put("orderCount", orderCount);
    result.put("totalSpent", totalConsumption);
    // 性别：直接透传用户填写的枚举（MALE/FEMALE/OTHER/UNDISCLOSED），null 表示用户从未填写
    result.put("gender", user.getGender());
    // 年龄：基于 birthday 计算周岁，未填写时为 null
    result.put("age", calculateAge(user.getBirthday()));
    // 积分余额（直接读 mo_user.points）
    result.put("points", user.getPoints() != null ? user.getPoints() : 0);
    // 会员等级 / 成长值 / 会员卡号
    result.put("memberLevel", memberLevel);
    result.put("growthValue", growthValue);
    result.put("memberNo", generateMemberNo(user.getId()));
    // 最近登录时间
    result.put("lastLoginTime", user.getLastLoginTime() != null ? user.getLastLoginTime().toString() : null);
    return result;
  }

  /**
   * 根据 birthday 计算周岁年龄
   * <p>
   * birthday 为空或晚于当前日期时返回 null（避免返回负数年龄误导运营）
   */
  private static Integer calculateAge(LocalDate birthday) {
    if (birthday == null) {
      return null;
    }
    LocalDate today = LocalDate.now();
    if (birthday.isAfter(today)) {
      return null;
    }
    return Period.between(birthday, today).getYears();
  }

  /**
   * 会员卡号：与 C 端 MemberServiceImpl 保持一致，确保管理后台与 C 端展示统一。
   * 格式：MY + userId 低 8 位十进制 + 4 位数字校验位
   */
  private String generateMemberNo(Long userId) {
    if (userId == null) return "MY·00000000·0000";
    String seg = String.format("%08d", Math.abs(userId) % 100_000_000L);
    long check = Math.abs(userId);
    for (int i = 0; i < 4; i++) {
      check = (check / 10) + (check % 10);
    }
    String checkNum = String.format("%04d", (int) (check % 10_000L));
    return String.format("MY·%s·%s", seg, checkNum);
  }

  @Override
  public void updateStatus(Long id, Integer status) {
    UserEntity entity = userMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(status);
      userMapper.updateById(entity);
    }
  }

  @Override
  public void delete(Long id) {
    // 逻辑删除（通过 MyBatis Plus 的 @TableLogic 自动处理）
    userMapper.deleteById(id);
  }

  // ===== 用户画像新增：访问商品 / 访问页面 =====

  /**
   * 用户访问过的商品列表：按 mo_browsing_history 聚合到商品维度，关联 mo_product 补全商品信息。
   * 返回结果按"最近访问时间"倒序，最多返回 size 条；size<=0 时默认 50。
   */
  @Override
  public List<UserVisitedProductResponse> listVisitedProducts(Long userId, int size) {
    if (userId == null) return Collections.emptyList();
    int limit = size <= 0 ? 50 : Math.min(size, 200);

    List<BrowsingHistoryEntity> historyList = browsingHistoryMapper.selectList(
        new LambdaQueryWrapper<BrowsingHistoryEntity>()
            .eq(BrowsingHistoryEntity::getUserId, userId));
    if (historyList.isEmpty()) return Collections.emptyList();

    // 1. 按 productId 聚合：累计次数 + 最近时间
    Map<Long, int[]> counter = new HashMap<>(); // [次数, 最近时间 epochSecond 占位]
    Map<Long, java.time.LocalDateTime> latestTime = new HashMap<>();
    for (BrowsingHistoryEntity h : historyList) {
      Long pid = h.getProductId();
      if (pid == null) continue;
      counter.merge(pid, new int[]{1, 0}, (oldV, newV) -> {
        oldV[0] += 1;
        return oldV;
      });
      java.time.LocalDateTime cur = latestTime.get(pid);
      if (cur == null || (h.getCreateTime() != null && h.getCreateTime().isAfter(cur))) {
        latestTime.put(pid, h.getCreateTime());
      }
    }

    // 2. 批量查询商品信息
    List<Long> productIds = new ArrayList<>(counter.keySet());
    Map<Long, com.moyuyo.dao.entity.ProductEntity> productMap = productIds.stream()
        .map(productMapper::selectById)
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(com.moyuyo.dao.entity.ProductEntity::getId, p -> p));

    // 3. 组装响应，按最近访问时间倒序
    List<UserVisitedProductResponse> result = new ArrayList<>();
    for (Map.Entry<Long, int[]> e : counter.entrySet()) {
      Long pid = e.getKey();
      com.moyuyo.dao.entity.ProductEntity p = productMap.get(pid);
      UserVisitedProductResponse r = new UserVisitedProductResponse();
      r.setProductId(pid);
      r.setViewCount(e.getValue()[0]);
      r.setLastVisitTime(latestTime.get(pid));
      if (p != null) {
        r.setProductName(p.getName());
        r.setMainImage(p.getMainImage());
        if (p.getPrice() != null) {
          // 后端 mo_product.price 单位为元，转为分避免精度丢失
          r.setPrice(p.getPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }
      }
      result.add(r);
    }
    result.sort((a, b) -> {
      java.time.LocalDateTime ta = a.getLastVisitTime();
      java.time.LocalDateTime tb = b.getLastVisitTime();
      if (ta == null && tb == null) return 0;
      if (ta == null) return 1;
      if (tb == null) return -1;
      return tb.compareTo(ta);
    });
    if (result.size() > limit) {
      return result.subList(0, limit);
    }
    return result;
  }

  /**
   * 用户访问过的页面列表：按 mo_visit_log 聚合到 pageUrl 维度。
   * 返回结果按"最近访问时间"倒序，最多返回 size 条。
   */
  @Override
  public List<UserVisitedPageResponse> listVisitedPages(Long userId, int size) {
    if (userId == null) return Collections.emptyList();
    int limit = size <= 0 ? 50 : Math.min(size, 200);

    List<VisitLogEntity> logs = visitLogMapper.selectList(
        new LambdaQueryWrapper<VisitLogEntity>()
            .eq(VisitLogEntity::getUserId, userId)
            .orderByDesc(VisitLogEntity::getCreateTime));
    if (logs.isEmpty()) return Collections.emptyList();

    // 按 pageUrl 聚合次数与停留时长
    Map<String, int[]> agg = new HashMap<>(); // [次数, 总停留秒数]
    Map<String, String> pageNameMap = new HashMap<>();
    Map<String, java.time.LocalDateTime> latestMap = new HashMap<>();
    for (VisitLogEntity log : logs) {
      String url = log.getPageUrl();
      if (url == null || url.isEmpty()) continue;
      agg.computeIfAbsent(url, k -> new int[]{0, 0});
      int[] v = agg.get(url);
      v[0] += 1;
      v[1] += (log.getStayDuration() != null ? log.getStayDuration() : 0);
      if (log.getPageName() != null && !log.getPageName().isEmpty()) {
        pageNameMap.putIfAbsent(url, log.getPageName());
      }
      java.time.LocalDateTime cur = latestMap.get(url);
      if (cur == null || (log.getCreateTime() != null && log.getCreateTime().isAfter(cur))) {
        latestMap.put(url, log.getCreateTime());
      }
    }

    List<UserVisitedPageResponse> result = new ArrayList<>();
    for (Map.Entry<String, int[]> e : agg.entrySet()) {
      UserVisitedPageResponse r = new UserVisitedPageResponse();
      r.setPageUrl(e.getKey());
      r.setPageName(pageNameMap.getOrDefault(e.getKey(), e.getKey()));
      r.setVisitCount(e.getValue()[0]);
      r.setStayDuration(e.getValue()[1]);
      r.setLastVisitTime(latestMap.get(e.getKey()));
      result.add(r);
    }
    if (result.size() > limit) {
      return result.subList(0, limit);
    }
    return result;
  }
}