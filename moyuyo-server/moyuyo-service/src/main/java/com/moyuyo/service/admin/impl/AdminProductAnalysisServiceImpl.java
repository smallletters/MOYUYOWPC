package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.entity.FavoriteEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.admin.entity.SearchLogEntity;
import com.moyuyo.dao.admin.mapper.SearchLogMapper;
import com.moyuyo.dao.mapper.BrowsingHistoryMapper;
import com.moyuyo.dao.mapper.CartMapper;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.dao.mapper.FavoriteMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.service.admin.AdminProductAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台商品分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null") // Eclipse JDT 误报：Stream + Lombok @Data 实体推断的 @Nonnull 冲突
public class AdminProductAnalysisServiceImpl implements AdminProductAnalysisService {

  private final ProductMapper productMapper;
  private final OrderItemMapper orderItemMapper;
  private final CategoryMapper categoryMapper;
  private final FavoriteMapper favoriteMapper;
  private final BrowsingHistoryMapper browsingHistoryMapper;
  private final CartMapper cartMapper;
  private final SearchLogMapper searchLogMapper;
  private final ProductReviewMapper productReviewMapper;

  @Override
  public Map<String, Object> overview() {
    // 总商品数（一次 COUNT(*)）
    long totalProductCount = productMapper.selectCount(new LambdaQueryWrapper<>());
    // 在售商品数：用 LambdaQueryWrapper 让 MyBatis-Plus 自动按方言生成 boolean 比较（MySQL=`=1`，PG=`=TRUE`）
    long activeProductCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOnSale, true));

    // 总销量（聚合所有 OrderItem.quantity 之和）
    long totalSales = orderItemMapper.selectMaps(
        new QueryWrapper<OrderItemEntity>().select("SUM(quantity) AS total")
    ).stream()
        .findFirst()
        .map(r -> ((Number) r.get("total")).longValue())
        .orElse(0L);

    // 总收藏数 / 总浏览量：分别走 COUNT（与 overview 命中不同的表）
    Long totalFavorites = favoriteMapper.selectCount(new LambdaQueryWrapper<>());
    Long totalViews = browsingHistoryMapper.selectCount(new LambdaQueryWrapper<>());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalProductCount", totalProductCount);
    result.put("activeProductCount", activeProductCount);
    result.put("totalSales", totalSales);
    result.put("totalFavorites", totalFavorites);
    result.put("totalViews", totalViews);
    return result;
  }

  @Override
  public List<Map<String, Object>> topSales(int limit) {
    // 按销量排序查询商品
    Page<ProductEntity> page = productMapper.selectPage(
        new Page<>(1, limit),
        new LambdaQueryWrapper<ProductEntity>()
            .orderByDesc(ProductEntity::getSales));

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProductEntity product : page.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", product.getId());
      item.put("name", product.getName());
      item.put("sales", product.getSales());
      item.put("price", product.getPrice());
      item.put("stock", product.getStock());
      result.add(item);
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> report(LocalDate startDate, LocalDate endDate) {
    // 防止无时间范围时一次返回上万条商品撑爆前端 / DB；强制上限 500
    List<ProductEntity> products = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>()
            .orderByDesc(ProductEntity::getSales)
            .last("LIMIT 500"));

    // 区间销量与收入走 SQL 聚合，按 product_id 分组直接 SUM，不再全表加载 OrderItem
    // SUM(price * quantity) 计算区间收入（用下单快照价，保证切换时间范围时 revenue 同步变化）
    QueryWrapper<OrderItemEntity> aggWrapper = new QueryWrapper<OrderItemEntity>()
        .select("product_id",
                "SUM(quantity) AS period_sales",
                "SUM(price * quantity) AS period_revenue")
        .isNotNull("product_id")
        .groupBy("product_id");
    if (startDate != null) {
      aggWrapper.ge("create_time", startDate.atStartOfDay());
    }
    if (endDate != null) {
      aggWrapper.le("create_time", endDate.atTime(LocalTime.MAX));
    }
    Map<Long, Long> periodSales = new HashMap<>();
    Map<Long, BigDecimal> periodRevenue = new HashMap<>();
    for (Map<String, Object> row : orderItemMapper.selectMaps(aggWrapper)) {
      Object pid = row.get("product_id");
      if (pid == null) continue;
      long id = ((Number) pid).longValue();
      periodSales.put(id, (Number) row.get("period_sales") == null ? 0L : ((Number) row.get("period_sales")).longValue());
      periodRevenue.put(id, row.get("period_revenue") == null ? BigDecimal.ZERO : new BigDecimal(row.get("period_revenue").toString()));
    }

    // 按商品ID聚合：浏览量、收藏量、加购量（一次性 SQL 聚合，避免 N+1）
    Map<Long, Long> viewStats = countProductViews();
    Map<Long, Long> favStats = countProductFavorites();
    Map<Long, Long> cartAddsByProduct = batchCartAddsByProduct(
        products.stream().map(ProductEntity::getId).collect(Collectors.toSet()));

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProductEntity product : products) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", product.getId());
      item.put("productName", product.getName());
      item.put("price", product.getPrice());
      item.put("stock", product.getStock());
      item.put("sales", product.getSales());
      item.put("views", viewStats.getOrDefault(product.getId(), 0L));
      item.put("favorites", favStats.getOrDefault(product.getId(), 0L));
      item.put("cartAdds", cartAddsByProduct.getOrDefault(product.getId(), 0L));
      // 区间销量与收入：从 SQL 聚合结果取值，O(1)
      item.put("periodSales", periodSales.getOrDefault(product.getId(), 0L));
      item.put("revenue", periodRevenue.getOrDefault(product.getId(), BigDecimal.ZERO));

      result.add(item);
    }
    return result;
  }

  // ==================== 新品表现追踪 ====================

  @Override
  public Map<String, Object> newProductTracking(int days) {
    int safeDays = days <= 0 ? 7 : Math.min(days, 90);
    LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

    // 取窗口内上架的全部商品（移除 LIMIT 50：避免截断导致前后周期口径不一致）
    List<ProductEntity> newProducts = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>()
            .ge(ProductEntity::getCreateTime, since)
            .orderByDesc(ProductEntity::getCreateTime));

    // 当前周期新品销量
    long currentSales = newProducts.stream()
        .mapToLong(p -> Optional.ofNullable(p.getSales()).orElse(0).longValue()).sum();

    // 上一周期对比：再往前 safeDays 天的商品，作为同期对照
    LocalDateTime prevSince = since.minusDays(safeDays);
    List<ProductEntity> prevProducts = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>()
            .ge(ProductEntity::getCreateTime, prevSince)
            .lt(ProductEntity::getCreateTime, since));
    long prevSales = prevProducts.stream()
        .mapToLong(p -> Optional.ofNullable(p.getSales()).orElse(0).longValue()).sum();

    String salesChange = formatPercentChange(currentSales, prevSales);

    // 一次性批量查询所有新品的浏览量，避免 N+1
    Set<Long> newProductIds = newProducts.stream().map(ProductEntity::getId).collect(Collectors.toSet());
    Map<Long, Long> viewByProduct = batchViewsByProduct(newProductIds);
    long newProductViews = newProductIds.stream().mapToLong(id -> viewByProduct.getOrDefault(id, 0L)).sum();

    // 上一周期浏览量（按"已上架期间"窗口过滤：prevSince ~ since）
    Set<Long> prevProductIds = prevProducts.stream().map(ProductEntity::getId).collect(Collectors.toSet());
    Map<Long, Long> prevViewByProduct = batchViewsByProductBetween(prevProductIds, prevSince, since);
    long prevViews = prevProductIds.stream().mapToLong(id -> prevViewByProduct.getOrDefault(id, 0L)).sum();

    String conversion = newProductViews > 0
        ? String.format("%.1f%%", currentSales * 100.0 / newProductViews)
        : "0.0%";
    // 转化率同比：prevConv = prevSales/prevViews（且 prevViews 仅计 prevProducts 上架期间）
    double prevConv = prevViews > 0 ? prevSales * 100.0 / prevViews : 0;
    double currConv = newProductViews > 0 ? currentSales * 100.0 / newProductViews : 0;
    String conversionChange = formatPercentValue(currConv - prevConv, prevConv);

    // 近 N 天趋势：销量走真实订单；转化率趋势只统计"新品"窗口，避免被全量商品稀释
    List<Long> salesTrend = buildSalesTrend(safeDays);
    List<Long> convTrend = newProductIds.isEmpty()
        ? salesTrend
        : buildConversionTrend(safeDays, newProductIds);

    // 列表：取销量 Top5（用已查好的 viewByProduct，避免再查数据库）
    List<Map<String, Object>> list = new ArrayList<>();
    newProducts.stream()
        .sorted(Comparator.comparing(ProductEntity::getSales,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(5)
        .forEach(p -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("name", p.getName());
          item.put("launchAt", p.getCreateTime() != null ? p.getCreateTime().toLocalDate().toString() : "");
          item.put("sales", Optional.ofNullable(p.getSales()).orElse(0));
          long views = viewByProduct.getOrDefault(p.getId(), 0L);
          String conv = views > 0
              ? String.format("%.1f%%", Optional.ofNullable(p.getSales()).orElse(0) * 100.0 / views)
              : "0.0%";
          item.put("conversion", conv);
          list.add(item);
        });

    List<Map<String, Object>> metrics = new ArrayList<>();
    metrics.add(buildMetric("新品销量", String.valueOf(currentSales), salesChange, true, salesTrend));
    metrics.add(buildMetric("新品转化率", conversion, conversionChange, true, convTrend));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("metrics", metrics);
    result.put("list", list);
    return result;
  }

  // ==================== 滞销商品预警 ====================

  @Override
  public List<Map<String, Object>> slowMovingProducts() {
    LocalDateTime since30 = LocalDateTime.now().minusDays(30);
    // 候选：上架 ≥30 天的商品（onSale=true 且 createTime <= now-30d）
    List<ProductEntity> candidates = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>()
            .eq(ProductEntity::getOnSale, true)
            .le(ProductEntity::getCreateTime, since30));

    // 取近 30 天订单明细按 productId 聚合销量
    Map<Long, Long> sales30Map = loadSales30Map();

    List<Map<String, Object>> list = new ArrayList<>();
    for (ProductEntity p : candidates) {
      int sales30 = sales30Map.getOrDefault(p.getId(), 0L).intValue();
      int stock = Optional.ofNullable(p.getStock()).orElse(0);
      // 滞销阈值：库存>0 且 30 天销量 <= 5
      if (stock <= 0 || sales30 > 5) continue;
      // 周转天数 = 库存 / max(30天日均销量, 1/30)
      // 0.033 是 1/30，避免销量为 0 时除零
      double avgDaily = Math.max(sales30 / 30.0, 0.033);
      long turnoverDays = (long) Math.ceil(stock / avgDaily);
      // 封顶到 999，避免 UI 显示几万天
      turnoverDays = Math.min(turnoverDays, 999L);
      String level = turnoverDays >= 60 ? "danger" : "warning";

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", p.getName());
      item.put("stock", stock);
      item.put("sales30", sales30);
      item.put("days", turnoverDays);
      item.put("level", level);
      list.add(item);
    }
    // 按周转天数倒序
    list.sort((a, b) -> Long.compare((long) b.get("days"), (long) a.get("days")));
    return list.size() > 20 ? list.subList(0, 20) : list;
  }

  // ==================== 流转率概览 ====================

  @Override
  public Map<String, Object> turnoverOverview() {
    // 用一条 SQL 取 4 个总数（4 张表各一个子查询），减少网络往返与连接占用
    // 子查询形式让 MySQL/PostgreSQL 都能正确解析；不依赖具体方言
    // 表名以实际 Entity 上的 @TableName 为准：mo_favorites（注意复数）
    Map<String, Object> agg = orderItemMapper.selectMaps(
        new QueryWrapper<OrderItemEntity>()
            .select(
                "(SELECT COUNT(*) FROM mo_browsing_history) AS views",
                "(SELECT COUNT(*) FROM mo_cart) AS carts",
                "(SELECT COUNT(*) FROM mo_favorites) AS favorites",
                "(SELECT COUNT(*) FROM mo_order_item) AS orders"
            )
            .last("LIMIT 1")
    ).stream().findFirst().orElse(new HashMap<>());
    long totalViews = ((Number) agg.getOrDefault("views", 0)).longValue();
    long totalCart = ((Number) agg.getOrDefault("carts", 0)).longValue();
    long totalFavorites = ((Number) agg.getOrDefault("favorites", 0)).longValue();
    long totalOrders = ((Number) agg.getOrDefault("orders", 0)).longValue();

    String avgTurnover = totalViews > 0
        ? String.format("%.1f%%", totalOrders * 100.0 / totalViews)
        : "0.0%";
    String cartRate = totalViews > 0
        ? String.format("%.1f%%", totalCart * 100.0 / totalViews)
        : "0.0%";
    String favRate = totalViews > 0
        ? String.format("%.1f%%", totalFavorites * 100.0 / totalViews)
        : "0.0%";

    List<Map<String, Object>> metrics = new ArrayList<>();
    metrics.add(metric("平均流转率", avgTurnover));
    metrics.add(metric("加购率", cartRate));
    metrics.add(metric("收藏率", favRate));

    // 分类排行：取一级分类，按其下商品累计的浏览/成交排序
    List<CategoryEntity> topCategories = categoryMapper.selectList(
        new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getLevel, 1));
    Map<Long, Long> productViewMap = countProductViews();
    // 用 SQL 聚合 productId -> 销量，避免把整张 order_item 拉进内存
    Map<Long, Long> productOrderMap = new HashMap<>();
    List<Map<String, Object>> orderAggRows = orderItemMapper.selectMaps(
        new QueryWrapper<OrderItemEntity>()
            .select("product_id, SUM(quantity) as total")
            .groupBy("product_id"));
    for (Map<String, Object> r : orderAggRows) {
      Object pid = r.get("product_id");
      Object total = r.get("total");
      if (pid == null || total == null) continue;
      productOrderMap.put(((Number) pid).longValue(), ((Number) total).longValue());
    }

    List<Map<String, Object>> categories = new ArrayList<>();
    for (CategoryEntity c : topCategories) {
      List<ProductEntity> products = productMapper.selectList(
          new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getCategoryId, c.getId()));
      if (products.isEmpty()) continue;
      long views = products.stream().mapToLong(p -> productViewMap.getOrDefault(p.getId(), 0L)).sum();
      long deals = products.stream().mapToLong(p -> productOrderMap.getOrDefault(p.getId(), 0L)).sum();
      String rate = views > 0
          ? String.format("%.1f%%", deals * 100.0 / views)
          : "0.0%";
      String level = deals * 100.0 / Math.max(views, 1) >= 60 ? "high"
          : deals * 100.0 / Math.max(views, 1) >= 50 ? "medium" : "low";

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", c.getName());
      item.put("views", formatCount(views));
      item.put("deals", formatCount(deals));
      item.put("rate", rate);
      item.put("level", level);
      categories.add(item);
    }
    categories.sort((a, b) -> {
      double ra = parsePercent((String) a.get("rate"));
      double rb = parsePercent((String) b.get("rate"));
      return Double.compare(rb, ra);
    });
    if (categories.size() > 5) categories = categories.subList(0, 5);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("metrics", metrics);
    result.put("categories", categories);
    return result;
  }

  // ==================== 热门搜索词 ====================

  @Override
  public List<Map<String, Object>> hotSearchKeywords() {
    // 按关键词聚合近 30 天搜索次数，分母分子时间窗口对齐，避免 >100%
    LocalDateTime since30 = LocalDateTime.now().minusDays(30);
    List<Map<String, Object>> raw = searchLogMapper.selectMaps(
        new QueryWrapper<SearchLogEntity>()
            .select("keyword, COUNT(*) as cnt")
            .isNotNull("keyword")
            .ne("keyword", "")
            .ge("create_time", since30)
            .groupBy("keyword")
            .orderByDesc("cnt")
            .last("LIMIT 10"));

    long maxCount = raw.isEmpty() ? 0L
        : ((Number) raw.get(0).get("cnt")).longValue();

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : raw) {
      String keyword = (String) row.get("keyword");
      long cnt = ((Number) row.get("cnt")).longValue();
      // 加购率 = 该关键词命中商品的近 30 天加购量 / 该关键词近 30 天搜索次数
      // 因为商品名称 LIKE 匹配会让一个关键词命中多个商品，加购量天然 > 搜索次数。
      // 这里用 min(..., cnt) 截到 100%，更符合"搜索 → 加购"的真实漏斗语义。
      long cartForKw = countCartAddsForKeywordInWindow(keyword, since30);
      long capped = Math.min(cartForKw, cnt);
      String cartRate = cnt > 0
          ? String.format("%.1f%%", capped * 100.0 / cnt)
          : "0.0%";
      double percent = maxCount > 0 ? cnt * 100.0 / maxCount : 0;

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("keyword", keyword);
      item.put("count", cnt);
      item.put("cartRate", cartRate);
      item.put("percent", Math.round(percent * 10) / 10.0);
      result.add(item);
    }
    return result;
  }

  // ==================== 评价分析 ====================

  @Override
  public Map<String, Object> reviewAnalysis() {
    long total = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>().eq(ProductReviewEntity::getStatus, "APPROVED"));
    long positive = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>()
            .eq(ProductReviewEntity::getStatus, "APPROVED")
            .ge(ProductReviewEntity::getRating, 4));
    long neutral = productReviewMapper.selectCount(
        new LambdaQueryWrapper<ProductReviewEntity>()
            .eq(ProductReviewEntity::getStatus, "APPROVED")
            .eq(ProductReviewEntity::getRating, 3));
    long negative = total - positive - neutral;

    String posRate = total > 0 ? String.format("%.1f%%", positive * 100.0 / total) : "0.0%";
    String neuRate = total > 0 ? String.format("%.1f%%", neutral * 100.0 / total) : "0.0%";
    String negRate = total > 0 ? String.format("%.1f%%", negative * 100.0 / total) : "0.0%";

    // 评分分布：1-5 星各自的占比
    Map<Long, Long> ratingCount = productReviewMapper.selectMaps(
        new QueryWrapper<ProductReviewEntity>()
            .select("rating, COUNT(*) as cnt")
            .eq("status", "APPROVED")
            .groupBy("rating"))
        .stream()
        .collect(Collectors.toMap(
            m -> ((Number) m.get("rating")).longValue(),
            m -> ((Number) m.get("cnt")).longValue()));

    List<Map<String, Object>> distribution = new ArrayList<>();
    for (int star = 5; star >= 1; star--) {
      long cnt = ratingCount.getOrDefault((long) star, 0L);
      int percent = total > 0 ? (int) Math.round(cnt * 100.0 / total) : 0;
      Map<String, Object> d = new LinkedHashMap<>();
      d.put("stars", star);
      d.put("count", cnt);
      d.put("percent", percent);
      distribution.add(d);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", total);
    result.put("positive", posRate);
    result.put("neutral", neuRate);
    result.put("negative", negRate);
    result.put("distribution", distribution);
    return result;
  }

  // ==================== 高频评价关键词 ====================

  @Override
  public List<Map<String, Object>> reviewKeywords() {
    // 取最近 200 条已通过评价的 content
    List<ProductReviewEntity> reviews = productReviewMapper.selectList(
        new LambdaQueryWrapper<ProductReviewEntity>()
            .eq(ProductReviewEntity::getStatus, "APPROVED")
            .isNotNull(ProductReviewEntity::getContent)
            .orderByDesc(ProductReviewEntity::getCreateTime)
            .last("LIMIT 200"));

    // 关键词总频次 + 按评价分桶的频次（用于决定每个关键词的情感颜色）
    Map<String, Integer> totalFreq = new HashMap<>();
    // positive = rating >= 4, neutral = rating == 3, negative = rating <= 2
    Map<String, int[]> bucketFreq = new HashMap<>();
    for (ProductReviewEntity r : reviews) {
      String content = r.getContent();
      if (content == null) continue;
      Integer rating = r.getRating();
      // 简单中文分词：连续 2-6 个汉字作为一个片段
      String cleaned = content.replaceAll("[^\\u4e00-\\u9fa5]", " ");
      String[] tokens = cleaned.split("\\s+");
      // 同一条评价里重复出现的同一关键词只计 1 次，避免反复堆叠
      Set<String> uniqueTokens = new HashSet<>();
      for (String t : tokens) {
        if (t.length() >= 2 && t.length() <= 6) {
          uniqueTokens.add(t);
        }
      }
      if (uniqueTokens.isEmpty()) continue;
      // 无论有无评分，都计入关键词总频次；无评分的不参与情感归类
      for (String t : uniqueTokens) {
        totalFreq.merge(t, 1, Integer::sum);
      }
      if (rating == null) continue;
      int bucketIdx;
      if (rating >= 4) {
        bucketIdx = 0; // positive
      } else if (rating == 3) {
        bucketIdx = 1; // neutral
      } else {
        bucketIdx = 2; // negative
      }
      for (String t : uniqueTokens) {
        bucketFreq.computeIfAbsent(t, k -> new int[3])[bucketIdx]++;
      }
    }

    List<Map<String, Object>> result = new ArrayList<>();
    totalFreq.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(12)
        .forEach(e -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("keyword", e.getKey());
          item.put("count", e.getValue());
          // 按该关键词在哪种评价里出现得最多来决定颜色，更贴合实际语义
          int[] b = bucketFreq.get(e.getKey());
          String type;
          if (b == null) {
            type = "info";
          } else if (b[0] >= b[2] * 2) {
            type = "success";
          } else if (b[2] > b[0]) {
            type = "danger";
          } else {
            type = "warning";
          }
          item.put("type", type);
          result.add(item);
        });
    return result;
  }

  // ==================== 库存健康度 ====================

  @Override
  public Map<String, Object> inventoryHealth() {
    Map<Long, Long> sales30Map = loadSales30Map();

    List<ProductEntity> products = productMapper.selectList(new LambdaQueryWrapper<>());
    int totalStock = products.stream().mapToInt(p -> Optional.ofNullable(p.getStock()).orElse(0)).sum();
    int slowCount = 0, normalCount = 0, lowCount = 0;
    for (ProductEntity p : products) {
      int stock = Optional.ofNullable(p.getStock()).orElse(0);
      // 零库存归为"紧缺"（卖断货）；三项百分比之和为 100%
      if (stock <= 0) {
        lowCount++;
        continue;
      }
      long sales30 = sales30Map.getOrDefault(p.getId(), 0L);
      long turnoverDays = (long) Math.ceil(stock / Math.max(sales30 / 30.0, 0.033));
      if (turnoverDays >= 60) slowCount++;
      else if (turnoverDays <= 14) lowCount++; // 14 天内卖完=紧缺
      else normalCount++;
    }
    // 分母=全量 SKU（含零库存），与 totalStock 表达"所有 SKU"口径一致；
    // 健康占比=normalCount / totalSku，即"所有 SKU 中库存周转健康的比例"
    int totalSku = slowCount + normalCount + lowCount;
    String healthRate = totalSku > 0
        ? String.format("%d%%", Math.round(normalCount * 100.0 / totalSku))
        : "0%";

    List<Map<String, Object>> items = new ArrayList<>();
    items.add(buildInvItem("滞销", slowCount, totalSku, "slow"));
    items.add(buildInvItem("正常", normalCount, totalSku, "normal"));
    items.add(buildInvItem("紧缺", lowCount, totalSku, "low"));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalStock", totalStock);
    result.put("healthRate", healthRate);
    result.put("items", items);
    return result;
  }

  // ==================== 库存周转天数排行 ====================

  @Override
  public List<Map<String, Object>> inventoryTurnoverRanking() {
    Map<Long, Long> sales30Map = loadSales30Map();

    List<ProductEntity> products = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>().gt(ProductEntity::getStock, 0));

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProductEntity p : products) {
      int stock = Optional.ofNullable(p.getStock()).orElse(0);
      long sales30 = sales30Map.getOrDefault(p.getId(), 0L);
      // avgDaily 用 double：避免销量为 0 时除零，0.033 = 1/30 作为下界
      double avgDaily = Math.max(sales30 / 30.0, 0.033);
      long days = (long) Math.ceil(stock / avgDaily);
      // 封顶到 999
      days = Math.min(days, 999L);
      String level = days >= 60 ? "danger" : days >= 30 ? "warning" : "success";
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", p.getName());
      item.put("stock", stock);
      item.put("days", days);
      item.put("level", level);
      result.add(item);
    }
    result.sort((a, b) -> Long.compare((long) b.get("days"), (long) a.get("days")));
    return result.size() > 20 ? result.subList(0, 20) : result;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 加载"近 30 天订单销量按商品聚合"。
   * <p>
   * 三个接口（slowMovingProducts / inventoryHealth / inventoryTurnoverRanking）都需要这个聚合。
   * 用 SQL 直接 GROUP BY 避免把近 30 天整张订单明细加载进内存。
   */
  private Map<Long, Long> loadSales30Map() {
    LocalDateTime since30 = LocalDateTime.now().minusDays(30);
    Map<Long, Long> map = new HashMap<>();
    for (Map<String, Object> row : orderItemMapper.selectMaps(
        new QueryWrapper<OrderItemEntity>()
            .select("product_id, SUM(quantity) AS total")
            .isNotNull("product_id")
            .ge("create_time", since30)
            .groupBy("product_id"))) {
      Object pid = row.get("product_id");
      if (pid == null) continue;
      map.put(((Number) pid).longValue(),
          row.get("total") == null ? 0L : ((Number) row.get("total")).longValue());
    }
    return map;
  }

  /** 按商品ID聚合浏览量 */
  private Map<Long, Long> countProductViews() {
    List<Map<String, Object>> rows = browsingHistoryMapper.selectMaps(
        new QueryWrapper<com.moyuyo.dao.entity.BrowsingHistoryEntity>()
            .select("product_id, COUNT(*) as cnt")
            .groupBy("product_id"));
    Map<Long, Long> map = new HashMap<>();
    for (Map<String, Object> r : rows) {
      map.put(((Number) r.get("product_id")).longValue(),
          ((Number) r.get("cnt")).longValue());
    }
    return map;
  }

  /** 按商品ID聚合收藏量（SQL 聚合，避免加载全表） */
  private Map<Long, Long> countProductFavorites() {
    Map<Long, Long> map = new HashMap<>();
    List<Map<String, Object>> rows = favoriteMapper.selectMaps(
        new QueryWrapper<FavoriteEntity>()
            .select("product_id, COUNT(*) AS cnt")
            .groupBy("product_id"));
    for (Map<String, Object> r : rows) {
      Object pid = r.get("product_id");
      if (pid == null) continue;
      map.put(((Number) pid).longValue(), ((Number) r.get("cnt")).longValue());
    }
    return map;
  }

  /** 批量按商品ID聚合加购量（避免 N+1） */
  private Map<Long, Long> batchCartAddsByProduct(Set<Long> productIds) {
    Map<Long, Long> map = new HashMap<>();
    if (productIds == null || productIds.isEmpty()) return map;
    List<Map<String, Object>> rows = cartMapper.selectMaps(
        new QueryWrapper<CartEntity>()
            .select("product_id, COUNT(*) AS cnt")
            .in("product_id", productIds)
            .groupBy("product_id"));
    for (Map<String, Object> r : rows) {
      Object pid = r.get("product_id");
      if (pid == null) continue;
      map.put(((Number) pid).longValue(), ((Number) r.get("cnt")).longValue());
    }
    return map;
  }

  /** 关键词带来的加购量近似：含该关键词的商品在时间窗口内被加购次数之和 */
  private long countCartAddsForKeywordInWindow(String keyword, LocalDateTime since) {
    if (keyword == null || keyword.isEmpty()) return 0;
    // LIKE 全表扫描成本高，限制最多匹配 200 个商品并加时间排序避免 N+1 把库打爆
    List<ProductEntity> matched = productMapper.selectList(
        new LambdaQueryWrapper<ProductEntity>()
            .like(ProductEntity::getName, keyword)
            .orderByDesc(ProductEntity::getCreateTime)
            .last("LIMIT 200"));
    if (matched.isEmpty()) return 0;
    Set<Long> ids = matched.stream().map(ProductEntity::getId).collect(Collectors.toSet());
    Long sum = cartMapper.selectCount(
        new LambdaQueryWrapper<CartEntity>()
            .in(CartEntity::getProductId, ids)
            .ge(CartEntity::getCreateTime, since));
    return sum == null ? 0L : sum;
  }

  /** 批量查询一组商品的浏览量，返回 productId -> 浏览量 映射（避免 N+1） */
  private Map<Long, Long> batchViewsByProduct(Set<Long> productIds) {
    Map<Long, Long> map = new HashMap<>();
    if (productIds == null || productIds.isEmpty()) return map;
    List<Map<String, Object>> rows = browsingHistoryMapper.selectMaps(
        new QueryWrapper<com.moyuyo.dao.entity.BrowsingHistoryEntity>()
            .select("product_id, COUNT(*) as cnt")
            .in("product_id", productIds)
            .groupBy("product_id"));
    for (Map<String, Object> r : rows) {
      map.put(((Number) r.get("product_id")).longValue(),
          ((Number) r.get("cnt")).longValue());
    }
    return map;
  }

  /** 批量查询一组商品在指定时间窗内的浏览量（用于"上一周期浏览量"严格按上架期间计算） */
  private Map<Long, Long> batchViewsByProductBetween(Set<Long> productIds, LocalDateTime from, LocalDateTime to) {
    Map<Long, Long> map = new HashMap<>();
    if (productIds == null || productIds.isEmpty()) return map;
    List<Map<String, Object>> rows = browsingHistoryMapper.selectMaps(
        new QueryWrapper<com.moyuyo.dao.entity.BrowsingHistoryEntity>()
            .select("product_id, COUNT(*) as cnt")
            .in("product_id", productIds)
            .ge("create_time", from)
            .lt("create_time", to)
            .groupBy("product_id"));
    for (Map<String, Object> r : rows) {
      map.put(((Number) r.get("product_id")).longValue(),
          ((Number) r.get("cnt")).longValue());
    }
    return map;
  }

  /** 近 N 天按天聚合的"新品转化率趋势"。
   * <p>
   * 真实口径：当日的"新品浏览量 / 新品订单销量"。
   * 用 SQL 聚合按 (date, product_id) 计算后再叠加，避免加载全表。
   * productIds 为空时返回全 0 列表（调用方应降级为销量趋势）。
   */
  private List<Long> buildConversionTrend(int days, Set<Long> productIds) {
    int safeDays = Math.min(Math.max(days, 1), 30);
    LocalDateTime since = LocalDateTime.now().minusDays(safeDays - 1L).withHour(0).withMinute(0).withSecond(0);
    if (productIds == null || productIds.isEmpty()) {
      List<Long> zeros = new ArrayList<>();
      for (int i = 0; i < safeDays; i++) zeros.add(0L);
      return zeros;
    }
    // 当日新品浏览量：按 product_id 过滤 + 按日期分组（SQL 聚合）
    Map<LocalDate, Long> dailyViews = new HashMap<>();
    List<Map<String, Object>> viewRows = browsingHistoryMapper.selectMaps(
        new QueryWrapper<com.moyuyo.dao.entity.BrowsingHistoryEntity>()
            .select("DATE(create_time) AS d, COUNT(*) AS cnt")
            .in("product_id", productIds)
            .ge("create_time", since)
            .groupBy("d"));
    for (Map<String, Object> r : viewRows) {
      dailyViews.put(LocalDate.parse(r.get("d").toString()), ((Number) r.get("cnt")).longValue());
    }
    // 当日新品订单销量：同上
    Map<LocalDate, Long> dailySales = new HashMap<>();
    List<Map<String, Object>> saleRows = orderItemMapper.selectMaps(
        new QueryWrapper<OrderItemEntity>()
            .select("DATE(create_time) AS d, SUM(quantity) AS total")
            .in("product_id", productIds)
            .ge("create_time", since)
            .groupBy("d"));
    for (Map<String, Object> r : saleRows) {
      dailySales.put(LocalDate.parse(r.get("d").toString()), ((Number) r.get("total")).longValue());
    }
    List<Long> trend = new ArrayList<>();
    for (int i = safeDays - 1; i >= 0; i--) {
      LocalDate d = LocalDate.now().minusDays(i);
      long v = dailyViews.getOrDefault(d, 0L);
      long s = dailySales.getOrDefault(d, 0L);
      long pct = v > 0 ? Math.round(s * 100.0 / v) : 0L;
      trend.add(pct);
    }
    return trend;
  }

  /** 近 N 天按天聚合的销量趋势 */
  private List<Long> buildSalesTrend(int days) {
    int safeDays = Math.min(Math.max(days, 1), 30);
    LocalDateTime since = LocalDateTime.now().minusDays(safeDays - 1L).withHour(0).withMinute(0).withSecond(0);
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>().ge(OrderItemEntity::getCreateTime, since));
    Map<LocalDate, Long> dayMap = items.stream()
        .collect(Collectors.groupingBy(
            i -> i.getCreateTime().toLocalDate(),
            Collectors.summingLong(OrderItemEntity::getQuantity)));
    List<Long> trend = new ArrayList<>();
    for (int i = safeDays - 1; i >= 0; i--) {
      LocalDate d = LocalDate.now().minusDays(i);
      trend.add(dayMap.getOrDefault(d, 0L));
    }
    return trend;
  }

  private Map<String, Object> buildMetric(String label, String value, String change, boolean up, List<Long> trend) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("label", label);
    m.put("value", value);
    m.put("change", change);
    m.put("up", up);
    m.put("trend", trend);
    return m;
  }

  private Map<String, Object> metric(String label, String value) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("label", label);
    m.put("value", value);
    return m;
  }

  private Map<String, Object> buildInvItem(String label, int count, int total, String level) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("label", label);
    m.put("count", count);
    m.put("percent", total > 0 ? Math.round(count * 100.0 / total) : 0);
    m.put("level", level);
    return m;
  }

  /** 同比百分比变化字符串 */
  private String formatPercentChange(long current, long prev) {
    if (prev <= 0) {
      return current > 0 ? "+100%" : "0%";
    }
    double pct = (current - prev) * 100.0 / prev;
    String sign = pct >= 0 ? "+" : "";
    return String.format("%s%.1f%%", sign, pct);
  }

  /** 同比百分比变化字符串（用于"百分比"类型的指标，如转化率）。
   * 与 formatPercentChange 的区别是 prev 为 0 时不能简单地当 +inf，否则会产生误导。
   * 此时返回 NEW 表示"从无到有"，更符合业务语义。 */
  private String formatPercentValue(double current, double prev) {
    if (prev <= 0) {
      if (current <= 0) return "0%";
      return "NEW"; // 从 0 到非 0，业务上用 NEW 更直观
    }
    double pct = (current - prev) * 100.0 / prev;
    // 兜底截断，避免极端值溢出显示
    if (Double.isInfinite(pct) || pct > 9999) return "+9999%";
    if (pct < -9999) return "-9999%";
    String sign = pct >= 0 ? "+" : "";
    return String.format("%s%.1f%%", sign, pct);
  }

  /** 将数字格式化为 k / w 单位 */
  private String formatCount(long n) {
    if (n >= 10000) return String.format("%.1fw", n / 10000.0);
    if (n >= 1000) return String.format("%.1fk", n / 1000.0);
    return String.valueOf(n);
  }

  private double parsePercent(String s) {
    if (s == null || s.isEmpty()) return 0;
    try {
      return Double.parseDouble(s.replace("%", ""));
    } catch (Exception e) {
      return 0;
    }
  }
}
