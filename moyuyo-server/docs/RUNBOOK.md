# MOYUYO 运维 Runbook

本文档面向生产环境运维工程师，覆盖常见故障的诊断与应急流程。

## 目录

1. [启动失败](#1-启动失败)
2. [支付回调异常](#2-支付回调异常)
3. [磁盘 / 内存告警](#3-磁盘--内存告警)
4. [数据库连接耗尽](#4-数据库连接耗尽)
5. [Elasticsearch 不可用](#5-elasticsearch-不可用)
6. [RocketMQ 消息积压](#6-rocketmq-消息积压)
7. [证书 / 密钥轮转](#7-证书--密钥轮转)
8. [应急回滚](#8-应急回滚)

---

## 1. 启动失败

**症状**：`docker compose up -d app` 后容器反复重启，`docker logs moyuyo-server` 报错。

### 1.1 必填配置缺失

日志中包含 `ProdConfigValidator` 输出的 `[FATAL] 生产环境启动失败：以下必填配置缺失或非法`：

```text
=========================================================
[FATAL] 生产环境启动失败：以下必填配置缺失或非法：
  - STRIPE_SECRET_KEY (对应配置: payment.stripe.secret-key)
  - JWT_SECRET 长度不足 32 字符（当前 24）
请在 .env 或容器环境变量中显式设置后重启。
=========================================================
```

**处置**：
1. 编辑 `.env` 补齐缺失项
2. 重启：`docker compose up -d app`

### 1.2 Flyway 校验失败

日志：`Migration checksum mismatch for migration version X`。

**处置**：
1. **生产环境严禁执行 `flyway repair`**，应回滚到上一个稳定版本的 JAR
2. 排查是否有人修改了已发布迁移脚本
3. 必要时手动 `DELETE FROM flyway_schema_history WHERE version = 'X'`（需 DBA 评估）

### 1.3 mo_order 分区 DDL 失败

仅在首次生产部署且单表数据 > 1000 万行时可能超时。

**处置**：
1. 改用 `pt-online-schema-change` 或 `gh-ost` 重做
2. 或先在低峰期手动 ALTER 成功后再启动 app

---

## 2. 支付回调异常

### 2.1 监控信号

```bash
# 实时查看 webhook 异常
docker exec moyuyo-server tail -f /var/log/moyuyo/moyuyo-error.log | grep -i "webhook"

# 关键日志：
# "Webhook 签名校验失败"     → 攻击探测，预期会少量发生
# "Stripe webhook 找不到订单"  → 真实订单但 metadata 丢失
# "PayPal webhook 找不到对应支付记录" → paypalOrderId 与本系统不一致
```

### 2.2 Stripe 回调风暴

**症状**：日志中 Stripe 回调 QPS 远超正常订单量。

**处置**：
1. 登录 Stripe Dashboard → Webhooks，查看事件类型与频率
2. 临时调整 `application-prod.yml` 中 `paymentApi` 限流值或拉黑异常 IP（Nginx 层）
3. 检查 `redis` 中 `idempotent:webhook:*` key 数量（异常多 = 重放攻击）

### 2.3 PayPal 回调签名失败

**症状**：所有 PayPal webhook 都报"证书 URL 不在白名单内"。

**处置**：
1. 确认 PayPal 推送的 `paypal-cert-url` 仍是 `https://api-m.paypal.com/...` 或 `https://api-m.sandbox.paypal.com/...`
2. 若 PayPal 更换了证书分发域，更新 `PaymentServiceImpl.verifyPayPalSignature` 中的白名单
3. 同时检查 `paypalWebhookId` 是否与 Dashboard 一致

---

## 3. 磁盘 / 内存告警

### 3.1 JVM OOM

**症状**：`docker logs moyuyo-server` 出现 `OutOfMemoryError` 或容器被 OOMKilled。

**应急**：
1. 临时扩容：`docker compose up -d --scale app=2`（需前置负载均衡）
2. 拉取 heap：`jcmd <pid> GC.heap_dump /tmp/heap.hprof`，导出后用 MAT 分析
3. 长期方案：调整 `Dockerfile` 中 `JVM_XMS` / `JVM_XMX`，启用 ZGC（已默认开启）

### 3.2 磁盘满

**症状**：`/var/log/moyuyo` 占满磁盘。

**应急**：
```bash
# 清理已压缩的历史日志
docker exec moyuyo-server find /var/log/moyuyo -name "*.log.*.gz" -mtime +7 -delete

# 长期方案：logback 已配置 max-history=30 自动滚动
```

---

## 4. 数据库连接耗尽

**症状**：应用日志 `HikariPool-1 - Connection is not available, request timed out`。

**排查**：
```sql
-- 查看当前连接
SHOW PROCESSLIST;
-- 查看是否锁等待
SELECT * FROM information_schema.INNODB_TRX WHERE trx_started < NOW() - INTERVAL 60 SECOND;
```

**应急**：
1. 杀掉长事务：`KILL <trx_mysql_thread_id>;`
2. 临时调大 `application-prod.yml` 中 `maximum-pool-size`（默认 20，可调到 50）
3. 长期方案：审计慢 SQL，加索引

---

## 5. Elasticsearch 不可用

**症状**：`/actuator/health` 显示 `elasticsearch: DOWN`，商品搜索 500。

**排查**：
```bash
# 检查 ES 集群
docker exec moyuyo-elasticsearch curl -kfs -u elastic:$ELASTICSEARCH_PASSWORD \
  https://localhost:9200/_cluster/health?pretty
```

**应急**：
1. ES 单节点挂掉：等待容器自动重启（`restart: unless-stopped`）
2. 磁盘满：扩容 ES 数据卷
3. 证书过期：参考 [7. 证书 / 密钥轮转](#7-证书--密钥轮转)
4. **临时降级**：商品搜索不可用不影响下单，可临时关闭 `ProductSearchService` 切到 DB LIKE 查询

---

## 6. RocketMQ 消息积压

**症状**：订单超时取消不及时，`OrderTimeoutConsumer` lag 持续增长。

**排查**：
```bash
docker exec moyuyo-rocketmq-broker sh mqadmin consumerProgress -g moyuyo-producer-group
```

**应急**：
1. 横向扩容 app 实例（消费者会自动 rebalance）
2. 临时关闭消费者后重启，让积压消息走单线程（避免并发问题）

---

## 7. 证书 / 密钥轮转

### 7.1 JWT_SECRET 轮转

**警告**：轮转 JWT_SECRET 会让所有现有 Token 失效（用户需重新登录）。

```bash
# 1. 生成新密钥
openssl rand -base64 48

# 2. 更新 .env 中的 JWT_SECRET
# 3. 重启 app（建议低峰期）
docker compose up -d app

# 4. 验证
curl http://localhost:8080/actuator/health
```

### 7.2 数据库密码轮转

```bash
# 1. MySQL 端修改密码
docker exec moyuyo-mysql mysql -uroot -p$OLD_PWD -e \
  "ALTER USER 'moyuyo'@'%' IDENTIFIED BY '$NEW_PWD'; FLUSH PRIVILEGES;"

# 2. 更新 .env 中的 MYSQL_PASSWORD，重启 app
docker compose up -d app
```

### 7.3 ES TLS 证书轮转

```bash
# 1. 在 ES 节点生成新证书
docker exec moyuyo-elasticsearch \
  bin/elasticsearch-certutil cert -days 365

# 2. 替换 /usr/share/elasticsearch/config/certs/elasticsearch.p12

# 3. 重启 ES 与 app
docker compose restart elasticsearch
docker compose up -d app
```

---

## 8. 应急回滚

### 8.1 应用回滚到上一版本

```bash
# 列出最近 3 个镜像
docker image ls ghcr.io/moyuyo/server --format "{{.Tag}}\t{{.CreatedAt}}"

# 用旧 tag 启动
docker compose -f docker-compose.yml up -d \
  --env-file .env \
  moyuyo-server:v20260803
```

### 8.2 数据库回滚

详见 [`scripts/backup/restore-mysql.ps1`](../scripts/backup/restore-mysql.ps1)。

**警告**：回滚会丢失回滚点之后的所有写入，务必先备份当前数据。

### 8.3 配置回滚

```bash
# 1. 恢复上一版 .env
cp .env.bak .env

# 2. 重启
docker compose up -d app
```

---

## 监控接入（建议）

生产环境应接入：

| 监控项 | 工具 | 阈值 |
| --- | --- | --- |
| JVM Heap | Prometheus + Micrometer | > 80% 告警 |
| HTTP 5xx 比例 | Prometheus + Actuator | > 1% 告警 |
| Webhook 签名失败率 | ELK / Loki | > 10/min 告警 |
| 数据库连接池使用率 | HikariCP Metrics | > 90% 告警 |
| ES 健康 | ES 自带 health API | status != green 告警 |
| 磁盘空间 | node_exporter | < 20% 告警 |

---

## 紧急联系

- 支付网关问题：Stripe/PayPal 工单
- 服务器：1Panel 控制台
- 数据库：DBA on-call
