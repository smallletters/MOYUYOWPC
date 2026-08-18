# MOYUYO Server 容量规划

## 性能基准（来自 k6 压测 smoke-test.js）

测试环境：单机 Docker Compose（4C8G）
| 指标 | 烟雾测试 | 压测 | 秒杀峰值 |
|------|----------|------|----------|
| QPS（稳态） | 200 | 500 | 2000 |
| P95 响应 | < 500ms | < 1s | < 2s |
| P99 响应 | < 1s | < 3s | < 5s |
| 错误率 | < 1% | < 5% | < 10% |
| 并发用户 | 50 | 200 | 500 |

## 容量估算

### 业务量假设（首年）
- DAU：10,000
- 日订单：5,000 笔
- 峰值时段（10:00-11:00）：1,500 笔订单/小时 = 25 笔/分钟 = **0.4 笔/秒**
- 秒杀峰值：100 笔/秒

### 资源需求（生产环境）

#### 单实例配置（推荐）
| 资源 | 规格 | 数量 |
|------|------|------|
| 应用节点 | 4C8G | 2 起步（HA）|
| MySQL 主 | 8C32G + SSD 500G | 1 |
| MySQL 从 | 8C32G + SSD 500G | 1（只读）|
| Redis | 4C16G + 32G | 1（主从或 Cluster）|
| Elasticsearch | 8C16G + SSD 200G | 3（master + 2 data）|
| Prometheus | 4C8G + SSD 200G | 1 |
| Grafana | 2C4G | 1 |
| Loki | 4C8G + SSD 500G | 1 |

### 自动扩缩容触发条件

**应用节点扩容**（K8s HPA）：
```yaml
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 70
- type: Pods
  pods:
    metric:
      name: http_requests_per_second
    target:
      type: AverageValue
      averageValue: "500"
```

最小副本：2（HA）；最大副本：10（成本上限）

### 数据库容量规划

**订单表（最大增长表）**：
- 单月订单：~150,000 行（含秒杀等大促）
- 单行大小：~5 KB（含 JSON 字段）
- 月增长：750 MB
- 保留期：13 个月（合规要求）
- 年增长：~10 GB

**已实施分区**：
- `mo_order`：按月分区（V20260804_02 迁移）
- 自动滚动：`scripts/maintenance/order-partition-rollover.ps1`

**索引策略**：
- `mo_user.email`、`mo_user.username` 唯一索引
- `mo_order(user_id, status)`、`mo_order(create_time)` 复合索引
- `mo_payment.transaction_id` 唯一索引
- `mo_operation_log` 4 类索引

### Redis 容量估算

- JWT 黑名单：~10K 用户 × 100 字节 = 1 MB
- 分布式锁：~50 个活跃锁 × 200 字节 = 10 KB
- 用户限流：~10K 用户 × 200 字节 = 2 MB
- 验证码：~1K × 100 字节 = 100 KB
- 缓存：~10K 商品详情 × 50 KB = 500 MB

**总内存需求**：约 1 GB，建议分配 4 GB。

### Elasticsearch 容量估算

- 单商品索引文档：~5 KB
- 商品总数（首年）：~100,000
- 索引大小：~500 MB
- 含 IK + pinyin 分词：~1.5 GB
- 副本 × 3 节点：~4.5 GB

**总磁盘需求**：建议每个 data 节点 50 GB（含未来增长）。

## 压测脚本使用

### 安装 k6

```bash
# macOS
brew install k6

# Windows
choco install k6

# Docker
docker pull grafana/k6
```

### 烟雾测试（本地验证）

```bash
# 应用启动后
k6 run scripts/loadtest/smoke-test.js
```

### 下单压测

```bash
# 需先创建 loadtest 用户
# 注册：POST /api/v1/auth/register
k6 run scripts/loadtest/checkout-stress.js
```

### 输出报告

```bash
k6 run --out json=result.json scripts/loadtest/smoke-test.js
```

将 result.json 导入 Grafana 或 [k6 cloud](https://app.k6.io) 进行深度分析。

## SLA 设计目标

| 指标 | 目标 | 实测 |
|------|------|------|
| 可用性（季度） | 99.95% | 待压测 |
| P95 响应时间 | < 500ms | 待压测 |
| P99 响应时间 | < 1s | 待压测 |
| 错误率 | < 0.1% | 待压测 |
| 故障恢复 RTO | < 15 分钟 | 由 backup + restore 验证 |
| 数据恢复 RPO | < 5 分钟 | 由 backup 策略保证 |

## 成本优化建议

1. **数据库连接池**：HikariCP 20 连接 × 2 节点 = 40 总连接（MySQL `max_connections` 至少 100）
2. **JVM 堆**：4 GB（避免过大导致 GC 停顿）
3. **日志保留**：30 天 + 压缩（gzip 后约 30% 原始大小）
4. **ES 副本数**：生产 1 副本即可（与单节点性能相比）
5. **Redis 持久化**：AOF + RDB 双写

## 待优化项（未来版本）

- [ ] 读写分离（MySQL binlog → 从库）
- [ ] OLAP 报表库（ClickHouse / Doris 处理 RFM）
- [ ] 商品图片 CDN（减轻源站带宽）
- [ ] 流式导出（POI SXSSF 替代当前 POI）
- [ ] 缓存预热（应用启动时预热热门商品）

## 故障演练预案

每季度执行一次故障演练：
1. **MySQL 主库宕机**：从库切换 + 应用重连
2. **Redis 不可用**：本地降级（跳过限流 / 分布式锁）
3. **ES 集群 red**：DB 兜底搜索（LIKE 模糊匹配降级）
4. **网络分区**：2 实例部署时会出现脑裂，需 3+ 实例部署
5. **数据库连接耗尽**：HikariCP 报警 + 自动扩容