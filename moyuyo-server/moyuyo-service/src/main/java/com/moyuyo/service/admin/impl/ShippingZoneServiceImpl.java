package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.ShippingZoneEntity;
import com.moyuyo.dao.admin.mapper.ShippingZoneMapper;
import com.moyuyo.service.admin.ShippingZoneService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingZoneServiceImpl implements ShippingZoneService {

  /** 缓存有效期：30s。让运营后台改动 Zone 后至多延迟 30s 生效，避免每请求打 DB */
  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  /** 不可变快照容器，命中时无需 lock；引用替换用 AtomicReference 保证可见性 */
  private final AtomicReference<CacheSnapshot> shippableCache = new AtomicReference<>();

  /**
   * 主动失效时间戳：evictCache 时刷新；loadShippableCountries 用它区分
   * "正常过期"（计入 miss）与 "运营主动失效"（不计 miss，指标更准）。
   * 防御：null 表示从未被 evict 过，全部按正常路径计 miss/hit。
   */
  private final AtomicReference<Instant> lastEvictedAt = new AtomicReference<>();

  private final ShippingZoneMapper shippingZoneMapper;

  // 缓存命中指标：便于生产环境排查 cache hit ratio（命中 / 总请求）。
  // Micrometer 自动对接 Prometheus / actuator/prometheus，便于后续接 Grafana。
  // 当 MeterRegistry 不可用（如单元测试）时降级为 in-memory AtomicLong，避免强制依赖。
  private final AtomicLong hitLocal = new AtomicLong();
  private final AtomicLong missLocal = new AtomicLong();
  private final AtomicLong adminEvictHitLocal = new AtomicLong();
  private Counter hitCounter;
  private Counter missCounter;
  private Counter adminEvictHitCounter;

  // MeterRegistry 由 Spring 注入；保留 final 字段便于 recordHit/recordMiss 等方法直接使用。
  // Counter 必须由 MeterRegistry 创建，因此放在 @PostConstruct 里完成注册（依赖已就绪）。
  private final MeterRegistry meterRegistry;

  /**
   * 初始化 Prometheus Counter。Lombok @RequiredArgsConstructor 已注入 shippingZoneMapper 与 meterRegistry，
   * 此处再创建 3 个 Counter 字段并赋值；用 @PostConstruct 避免在构造器里手动赋值导致 Lombok 与显式构造器冲突（曾出现 No default constructor found 启动失败）。
   * 当 MeterRegistry 为空（单元测试场景）时降级为 null，缓存命中率仅依赖 AtomicLong 兜底计算。
   */
  @PostConstruct
  void initCounters() {
    if (meterRegistry != null) {
      this.hitCounter = Counter.builder("shippingzone.cache.hit")
          .description("loadShippableCountries 缓存命中次数")
          .register(meterRegistry);
      this.missCounter = Counter.builder("shippingzone.cache.miss")
          .description("loadShippableCountries 缓存未命中/过期次数（DB 查询）")
          .register(meterRegistry);
      // 主动失效后第一次命中（运营触发的失效，不算缓存本身命中率低）
      this.adminEvictHitCounter = Counter.builder("shippingzone.cache.adminEvict")
          .description("后台 evictCache 后第一次命中次数（运营主动失效）")
          .register(meterRegistry);
    } else {
      this.hitCounter = null;
      this.missCounter = null;
      this.adminEvictHitCounter = null;
    }
  }

  @Override
  @SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
  public Set<String> loadShippableCountries() {
    CacheSnapshot snap = shippableCache.get();
    Instant now = Instant.now();

    // 命中路径：快照未过期
    if (snap != null && snap.expireAt.isAfter(now)) {
      // 进一步：若上次 evictCache 后第一次进入 → 算"运营主动失效命中"，不算常规 hit
      // （这样缓存命中率指标不会被运营频繁改动 Zone 拉低）
      Instant lastEvict = lastEvictedAt.get();
      if (lastEvict != null && !lastEvict.isBefore(snap.createdAt)) {
        // snap 是在 evict 之后才生成的，符合"运营改动后第一次加载"语义
        recordAdminEvictHit();
        lastEvictedAt.compareAndSet(lastEvict, null);  // 只计一次
        return snap.countries;
      }
      recordHit();
      return snap.countries;
    }

    // 缓存过期或不存在：DB 查询 + 回填
    recordMiss();
    Set<String> fresh = aggregate(shippingZoneMapper.selectList(
        new LambdaQueryWrapper<ShippingZoneEntity>().eq(ShippingZoneEntity::getStatus, "ACTIVE")));
    CacheSnapshot next = new CacheSnapshot(Set.copyOf(fresh), now, now.plus(CACHE_TTL));
    // 即使竞态让两个线程同时算 fresh，set 是不可变引用，最坏仅多查一次 DB
    shippableCache.set(next);
    // DB 回填 = 已"治愈"了 evictCache 留下的失效状态。后续正常 hit 不应再被记为 adminEvict，
    // 清掉 lastEvictedAt 让下一次命中路径走 recordHit()（常规 hit，命中率指标更真实）。
    lastEvictedAt.set(null);
    return next.countries;
  }

  @Override
  public List<String> listSupportedCountries() {
    // 下拉要让用户能选所有被配置过的国家（含 INACTIVE 区域），下单时再由兜底拒绝
    // 此接口 QPS 较低（仅地址编辑页 onLoad 一次），不缓存
    Set<String> set = aggregate(shippingZoneMapper.selectList(null));
    return set.stream().sorted().collect(Collectors.toList());
  }

  @Override
  public void evictCache() {
    shippableCache.set(null);
    lastEvictedAt.set(Instant.now());
    log.debug("ShippingZone shippable cache evicted");
  }

  /**
   * 当前缓存命中率（仅供诊断 / actuator 使用）。
   * 注：本进程是单服务，单实例部署；多实例下要分别看各自的命中率。
   * 计算时把"运营主动失效命中"排除——避免运营频繁改动拉低命中率指标。
   */
  public double cacheHitRatio() {
    long hit = hitLocal.get();
    long miss = missLocal.get();
    long adminEvict = adminEvictHitLocal.get();
    long total = hit + miss + adminEvict;
    return total == 0 ? 0.0 : (double) hit / total;
  }

  // ==================== 内部 ====================

  private void recordHit() {
    hitLocal.incrementAndGet();
    if (hitCounter != null) hitCounter.increment();
  }

  private void recordMiss() {
    missLocal.incrementAndGet();
    if (missCounter != null) missCounter.increment();
  }

  private void recordAdminEvictHit() {
    adminEvictHitLocal.incrementAndGet();
    if (adminEvictHitCounter != null) adminEvictHitCounter.increment();
  }

  /**
   * 把 zone.countryCodes（逗号分隔的 ISO 码）拆开、去空格、转大写、去重。
   * 注意：用 LinkedHashSet 保证插入顺序，避免不同数据库/不同时间拿到的集合顺序影响缓存命中。
   * 调用方自行保证传入的 zones 已按是否过滤 ACTIVE。
   */
  private Set<String> aggregate(List<ShippingZoneEntity> zones) {
    if (zones == null || zones.isEmpty()) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (ShippingZoneEntity z : zones) {
      if (z.getCountryCodes() == null || z.getCountryCodes().isBlank()) continue;
      for (String c : z.getCountryCodes().split(",")) {
        String t = c.trim();
        if (!t.isEmpty()) out.add(t.toUpperCase());
      }
    }
    return out;
  }

  /**
   * 缓存快照：country 集合 + 创建时间 + 过期时间。
   * createdAt 用于区分"运营 evict 后第一次加载"。
   */
  private record CacheSnapshot(Set<String> countries, Instant createdAt, Instant expireAt) {}
}