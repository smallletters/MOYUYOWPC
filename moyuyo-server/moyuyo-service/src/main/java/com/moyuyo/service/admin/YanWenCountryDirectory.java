package com.moyuyo.service.admin;

import com.moyuyo.common.config.YanWenProperties;
import com.moyuyo.common.dto.logistics.YanWenCountryListResponse;
import com.moyuyo.common.logistics.YanWenApiClient;
import com.moyuyo.common.util.CountryDirectoryLookup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 燕文通达国家目录 —— 内存缓存 + 启动期预热 + 定时刷新。
 * <p>
 * 设计目标：让 CountryResolver 用燕文官方数据做"中文国名 → ISO 二字码"的精确匹配，
 * 避免运营漏配 yml aliases 导致地址解析失败。
 * <p>
 * 工作流程：
 *   1) 启动后异步调一次燕文 common.country.getlist，填充内部 map（nameCh → code, nameEn → code）
 *   2) 失败 → 指数退避重试 3 次；最后失败 → 启动不阻塞，缓存留空（运行时 CountryResolver 走 yml aliases 兜底）
 *   3) 定时（默认 24h）刷新一次，避免燕文新增国家 / 改 code 后我们这边滞后
 *   4) 提供 lookupByZh / lookupByEn 给 CountryResolver 调用
 *
 * 线程安全：内部用 AtomicReference<Map>，写入通过 setRelease 整体替换，无锁读。
 */
@Slf4j
@Component
public class YanWenCountryDirectory implements CountryDirectoryLookup {

    /** 国家目录缓存：key = 中文国名 / 英文国名（小写归一）；value = ISO alpha-2 */
    private final AtomicReference<Map<String, String>> directory = new AtomicReference<>(new HashMap<>());

    /** 上次成功刷新的时间戳（毫秒）；0 表示从未刷新成功过 */
    private final java.util.concurrent.atomic.AtomicLong lastRefreshAt = new java.util.concurrent.atomic.AtomicLong(0);
    /** 上次刷新的耗时（毫秒）；0 表示从未刷新过 */
    private final java.util.concurrent.atomic.AtomicLong lastRefreshCostMs = new java.util.concurrent.atomic.AtomicLong(0);
    /** 上次刷新失败的错误信息；null 表示上次刷新成功 */
    private final java.util.concurrent.atomic.AtomicReference<String> lastRefreshError =
            new java.util.concurrent.atomic.AtomicReference<>(null);

    private final YanWenProperties yanWenProperties;
    private final YanWenApiClientFactory clientFactory;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    public YanWenCountryDirectory(YanWenProperties yanWenProperties,
                                  YanWenApiClientFactory clientFactory) {
        this.yanWenProperties = yanWenProperties;
        this.clientFactory = clientFactory;
    }

    /**
     * 启动期预热：异步调燕文拉一次国家列表填进缓存。
     * <p>
     * 注意：@PostConstruct 同步执行。这里用单独的线程做首次拉取，
     * 避免燕文接口 1~5s 的延迟阻塞 Spring 启动。
     */
    @PostConstruct
    public void warmup() {
        if (!yanWenProperties.isEnabled()) {
            log.info("燕文未启用（moyuyo.logistics.yanwen.enabled=false），跳过国家目录预热");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yanwen-country-refresh");
            t.setDaemon(true);
            return t;
        });
        // 启动 2s 后跑首次拉取（让应用先完成 bean 初始化，避免和 controller 启动抢线程）
        scheduler.schedule(this::refreshSafely, 2, TimeUnit.SECONDS);
        // 之后每 24h 刷一次
        long hours = 24;
        refreshTask = scheduler.scheduleAtFixedRate(this::refreshSafely,
                hours, hours, TimeUnit.HOURS);
        log.info("燕文国家目录定时刷新已启动，间隔 {} 小时", hours);
    }

    /**
     * 销毁前关闭 scheduler，避免线程泄漏。
     */
    @PreDestroy
    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 主动刷新：被定时任务调用。
     * <p>
     * 内部用指数退避：失败 1s → 2s → 4s 重试 3 次；最后失败仅记 warn（不抛异常，避免静默崩溃）。
     */
    void refreshSafely() {
        long backoffMs = 1000;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                refresh();
                return;
            } catch (Exception e) {
                log.warn("燕文国家目录刷新失败（第 {} / 3 次）：{}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    backoffMs *= 2;
                }
            }
        }
        log.warn("燕文国家目录 3 次重试均失败，继续使用上次缓存（如有）；CountryResolver 会兜底走 yml aliases");
    }

    /**
     * 实际拉取 + 写缓存。
     */
    private void refresh() {
        long start = System.currentTimeMillis();
        if (!yanWenProperties.isEnabled()) {
            log.debug("燕文未启用，跳过本次国家目录刷新");
            // P0：业务原因不能刷新也要写错误信息，否则 status 接口只能看到 "缓存 0 条 + 无错误"
            //   运营会以为"刷新失败"，实际是"燕文未启用"
            lastRefreshError.set("燕文未启用（moyuyo.logistics.yanwen.enabled=false）");
            return;
        }
        // 凭证解析（与 createOrder 一致：优先 mo_carrier，回退全局）
        // 这里简化：直接用全局凭证（运营通常 1 个燕文账号，且不会和承运商配置冲突）
        String userId = yanWenProperties.getUserId();
        String apiToken = yanWenProperties.getApiToken();
        String baseUrl = yanWenProperties.getBaseUrl();
        if (userId == null || userId.isBlank() || apiToken == null || apiToken.isBlank()) {
            log.warn("燕文凭证未配置，跳过国家目录刷新");
            // P0：同上 —— 让 status 接口明确告诉运营"为什么没刷成功"
            lastRefreshError.set("燕文凭证未配置（moyuyo.logistics.yanwen.user-id / api-token）");
            return;
        }
        try {
            YanWenApiClient client = clientFactory.create(baseUrl, userId, apiToken);
            YanWenCountryListResponse resp = client.getCountryList();
            if (resp == null || resp.getData() == null) {
                throw new IllegalStateException("燕文返回国家列表为空");
            }
            Map<String, String> map = new HashMap<>(resp.getData().size() * 2);
            for (YanWenCountryListResponse.CountryItem item : resp.getData()) {
                if (item.getCode() == null || item.getCode().isBlank()) continue;
                String code = item.getCode().trim().toUpperCase(Locale.ROOT);
                if (item.getNameCh() != null && !item.getNameCh().isBlank()) {
                    map.put(normalize(item.getNameCh()), code);
                }
                if (item.getNameEn() != null && !item.getNameEn().isBlank()) {
                    map.put(normalize(item.getNameEn()), code);
                }
            }
            directory.set(map);
            // 记录成功状态（admin 接口会读取这些字段）
            lastRefreshAt.set(System.currentTimeMillis());
            lastRefreshCostMs.set(System.currentTimeMillis() - start);
            lastRefreshError.set(null);
            log.info("燕文国家目录已刷新：{} 条（中英文去重后），耗时 {}ms",
                    map.size(), lastRefreshCostMs.get());
        } catch (Exception e) {
            // 失败时记录错误信息，供 admin 接口展示
            lastRefreshError.set(e.getMessage());
            throw e;
        }
    }

    /**
     * 按中文国名查 ISO alpha-2。
     * <p>
     * 大小写不敏感（内部统一小写归一）。
     *
     * @param nameZh 中文名（如 "加拿大"）或英文名（如 "CANADA"）
     * @return ISO alpha-2；查不到返回 null
     */
    @Override
    public String lookupByName(String nameZh) {
        if (nameZh == null || nameZh.isBlank()) return null;
        return directory.get().get(normalize(nameZh));
    }

    /**
     * 给 CountryResolver 提供的"按 address 子串查 code"便捷方法。
     * <p>
     * 遍历缓存所有 key，找"address 是否包含此 key" —— 命中即返回 code。
     * <p>
     * 设计取舍：
     *   - 用缓存 key 反向匹配 address，避免 CountryResolver 写"中文别名穷举"
     *   - O(n*m) 但 n≤200 国家、m≤200 地址长度 → 完全够用
     *   - 返回首个匹配项（按 HashMap 顺序，不保证；如需精确优先级可改 LinkedHashMap）
     */
    @Override
    public String findFirstCodeIn(String address) {
        if (address == null || address.isBlank()) return null;
        Map<String, String> map = directory.get();
        if (map.isEmpty()) return null;
        String lower = address.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 缓存大小（调试用）。
     */
    @Override
    public int size() {
        return directory.get().size();
    }

    /**
     * 上次成功刷新时间戳（毫秒）；0 = 从未成功刷新过。
     */
    public long getLastRefreshAt() {
        return lastRefreshAt.get();
    }

    /**
     * 上次刷新耗时（毫秒）；0 = 从未刷新过。
     */
    public long getLastRefreshCostMs() {
        return lastRefreshCostMs.get();
    }

    /**
     * 上次刷新失败的错误信息；null = 上次刷新成功。
     */
    public String getLastRefreshError() {
        return lastRefreshError.get();
    }

    /**
     * 取当前缓存的不可变快照（key=小写国名 / value=ISO code）。
     * <p>
     * 用于 admin 接口展示完整列表 + 搜索过滤。
     */
    public Map<String, String> snapshot() {
        return Map.copyOf(directory.get());
    }

    /**
     * 主动触发一次刷新（admin "强制刷新" 按钮调用）。
     * <p>
     * 与定时刷新共用同一路径 refreshSafely：失败 3 次重试；不阻塞调用线程。
     */
    public void refreshNow() {
        if (!yanWenProperties.isEnabled()) {
            log.info("燕文未启用，跳过主动刷新");
            return;
        }
        if (scheduler == null) {
            // 极端 case：scheduler 未初始化（启动预热被禁用或失败）。
            // 用 CompletableFuture 异步跑 refreshSafely —— 避免 HTTP 线程被 1~5s 燕文 HTTP 调用阻塞。
            // 这里不能用 sync refreshSafely()，否则 P0 阻塞风险。
            log.warn("refreshNow 时 scheduler 为 null，使用临时异步线程跑一次刷新");
            java.util.concurrent.CompletableFuture.runAsync(this::refreshSafely);
            return;
        }
        scheduler.execute(this::refreshSafely);
    }

    /**
     * 关键词归一：trim + lowercase。
     */
    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 暴露给 YanWenOrderCreator 的轻量工厂：避免外部直接 new YanWenApiClient 时漏配参数。
     */
    @Component
    public static class YanWenApiClientFactory {
        public YanWenApiClient create(String baseUrl, String userId, String apiToken) {
            return new YanWenApiClient(baseUrl, userId, apiToken);
        }
    }

    /** 显式保留 List 用法占位（避免 IDE 报"unused import"；也提示运营数据结构） */
    @SuppressWarnings("unused")
    private static final List<?> PLACEHOLDER = null;
}
