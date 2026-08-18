# Performance Audit Notes

本文件记录生产环境需要关注的 SQL 性能问题与索引建议。

## 已识别 N+1 / 性能风险

### 1. 订单列表（管理后台）

**位置**：`moyuyo-service` 的 `OrderServiceImpl.listOrders` → `OrderMapper.selectPage`

**风险**：当前仅查订单本体，订单项 `getOrderItems(orderId)` 在 controller 拼装 VO 时调用 → 每订单一次查询 → N+1。

**建议**：
- 管理后台订单列表展示已用 `selectPage`，列表接口本身没问题
- **详情接口** `getOrderDetail` 在 controller 层需要时再 `getOrderItems(orderId)`，单订单场景 OK
- 若需要"订单列表含商品预览"，应改造为 `LEFT JOIN mo_order_item` 一次性查出，避免 N+1

### 2. 社区评论 userId 查昵称

**位置**：`CommunityServiceImpl.getComments`

**当前实现**：
```java
List<Long> userIds = comments.stream().map(CommunityCommentEntity::getUserId).distinct()...
userMapper.selectBatchIds(userIds)  // ✅ 批量查，不是 N+1
```

**状态**：已优化（一次 `IN` 查询），OK。

### 3. 订单管理后台导出

**位置**：`AdminOrderOpsController.export`

**风险**：导出大表（10 万行+）会 OOM。

**建议**：
- 当前 `limit-for-period: 5` 限流已防止并发导出
- 导出任务化（异步 + 进度查询）：参考 `dataExport` 限流实例
- 未来可改为流式 CSV 导出（边读边写，避免全表加载）

## 已应用的索引（部分）

- `mo_order`：按 `(user_id, status)` 与 `(create_time)` 索引（见 V0~V8 迁移脚本）
- `mo_user`：邮箱唯一索引、用户名唯一索引
- `mo_payment`：按 `transaction_id` 唯一索引
- `mo_operation_log`：4 类索引（user/time、type/time、ip/time、create_time）
- `mo_product`：按 `on_sale, category_id, brand_id` 等组合索引
- `mo_order` 主键已改为复合 `(id, create_time)`（见 V20260804_02 分区变更）

## 高频慢查询建议

### 1. 商品搜索（DB fallback）

当 ES 不可用时，`ProductServiceImpl` 走 DB 模糊查询 `LIKE '%keyword%'`，**不会走索引**。

**建议**：
- 必须保证 ES 始终可用（配置熔断器 `elasticsearch`）
- ES 完全不可用时降级到 DB 精确匹配（按 SPU 编码、分类 ID 等）
- 不要在生产开启 DB `LIKE '%xxx%'` 模糊搜索

### 2. 订单状态聚合统计

管理后台 RFM 分析、漏斗统计等场景需要 `GROUP BY` 聚合，参考 `OrderMapper.xml` 的 `selectRfmData`：
- 已加 `GROUP BY o.user_id` 索引（如无应补）
- 生产环境数据量大时考虑 OLAP（ClickHouse / Doris）

## 生产监控指标

- **慢查询**：启用 MySQL slow_query_log + Loki 收集
- **DB 连接池**：HikariCP `maximum-pool-size: 20`（当前配置），监控 `HikariPool` 指标：
  - `hikaricp_connections_usage_pending`：等待连接数（>0 表示连接耗尽）
  - `hikaricp_connections_usage_acquire_seconds`：获取连接耗时（>1s 异常）
- **JVM GC**：监控 ZGC 暂停时长（应 <100ms）
- **Redis**：监控 `redis_commands_duration_seconds` 与 `redis_memory_used_bytes`

## 配置项

- `spring.datasource.hikari.maximum-pool-size: 20`（生产环境建议按并发量调优）
- `spring.elasticsearch.connection-timeout: 5s`：ES 客户端超时（避免拖垮主流程）
- `resilience4j.circuitbreaker.elasticsearch.failure-rate-threshold: 50`：ES 50% 失败即熔断

## 后续优化（未实施）

1. **读写分离**：高负载场景分离主从（MySQL binlog → 从库读查询）
2. **缓存预热**：商品详情、库存数用 Redis 缓存（注意双写一致性）
3. **数据库连接池拆分**：订单库、用户库、商品库分库（高并发场景）
4. **慢 SQL 看板**：接入 Druid / Prometheus + SQL 解析
5. **批量导出优化**：流式 CSV / Excel 导出（POI SXSSF）
6. **CDN 静态资源**：商品图片走 CDN，减轻源站流量