# MOYUYO 生产部署前安全检查清单

> 适用于每次发版前的最后一道质量门禁。任何一项未达标，禁止上线。

## 0. 上线前 24 小时检查（人工逐项确认）

### 0.1 密钥与配置
- [ ] `.env` 中所有 `your_xxx` 占位符已替换（`ProdConfigValidator` 启动期会阻断，但人工复核更可靠）
- [ ] `JWT_SECRET` ≥ 32 字符、含大小写+数字+特殊字符（生成命令：`openssl rand -base64 48`）
- [ ] `API_SIGN_SECRET` ≥ 32 字符（同上）
- [ ] `MYSQL_PASSWORD` ≥ 16 字符
- [ ] `REDIS_PASSWORD` ≥ 8 字符
- [ ] `ELASTICSEARCH_PASSWORD` ≥ 16 字符
- [ ] `ADMIN_PASSWORD` ≥ 12 字符、含大小写+数字+特殊字符
- [ ] `MYSQL_TRUSTSTORE_PASSWORD` ≥ 8 字符（与 MySQL CA 证书对齐）
- [ ] `ELASTICSEARCH_TRUSTSTORE_PASSWORD` ≥ 8 字符
- [ ] `STRIPE_SECRET_KEY` 以 `sk_live_` 开头（严禁 `sk_test_`）
- [ ] `WOOCOMMERCE_URL` 以 `https://` 开头、不含 `localhost`
- [ ] `MOYUYO_CORS_ORIGINS` 仅含公司自有域名（严禁 `*` / `null` / `example.com`）

### 0.2 数据库迁移
- [ ] Flyway 迁移脚本已纳入 CI 校验（`failBuildOnCVSS=7` 与 OWASP Dependency-Check）
- [ ] 生产数据库 `flyway_schema_history` 中无孤儿迁移（与 CI 校验的 hash 一致）
- [ ] 已执行 V0~V8 全量回归测试（参考 README 中的「Flyway 校验失败」处理流程）
- [ ] 备份策略：mysqldump / xtrabackup 已配置且最近 24h 内验证可恢复

### 0.3 网络与防火墙
- [ ] MySQL 仅绑定 `127.0.0.1:3306`（应用通过容器网络访问）
- [ ] Redis 仅绑定 `127.0.0.1:6379`
- [ ] ES 仅绑定 `127.0.0.1:9200`（HTTPS + xpack security 开启）
- [ ] 应用业务端口 `8080` 仅绑定 `127.0.0.1`（由 1Panel/Nginx 反代）
- [ ] Actuator 端口 `9090` 仅绑定 `127.0.0.1`（仅 Prometheus 可达）
- [ ] 反向代理（Nginx / 1Panel）已配置：
  - `server_tokens off`（隐藏 nginx 版本）
  - TLS 1.2/1.3 only（禁用 SSLv3、TLS 1.0/1.1）
  - 安全头：`X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy`
  - HSTS：`max-age=31536000; includeSubDomains`
  - rate limit：`limit_req_zone`（防 CC 攻击）

### 0.4 Docker 镜像
- [ ] 镜像以非 root 用户运行（Dockerfile 已用 `USER moyuyo`，UID 1000）
- [ ] `/actuator/**` 在反代层配置 IP 白名单（仅 Prometheus 与内网监控可访问）
- [ ] 容器启动项 `stop_grace_period=45s`（与 Spring `spring.lifecycle.timeout-per-shutdown-phase=30s` 对齐）
- [ ] 镜像已启用 provenance + SBOM（`backend-cd.yml` 中 `provenance: true` / `sbom: true`）
      并通过 `cosign verify-attestation` 或 `docker buildx imagetools inspect` 校验通过
      （满足 SLSA L1 供应链可追溯要求）
- [ ] CD 流程中 post-push Trivy 扫描无 CRITICAL/HIGH 漏洞；否则镜像已被删除、需修复后重新构建

## 1. 启动期校验（ProdConfigValidator 自动完成）

启动日志应输出：

```
[prod] 启动期必填配置校验通过（19 项 + 2 软校验项）
```

若失败则阻断启动，并列出所有缺失项。**禁止**注释掉 `ProdConfigValidator` 跳过校验。

## 2. 运行时自动护栏（已部署项，无需人工介入）

| 防护层 | 配置位置 | 作用 |
| --- | --- | --- |
| IP 限流（Redis 滑动窗口） | `IpRateLimitFilter` | 保护登录/支付/下单/退款/优惠券/积分等敏感端点，每 IP 60 次/分钟 |
| 用户限流 | `UserRateLimitFilter` | 已登录用户接口防单用户抢占配额 |
| 接口级 @RateLimiter | `OrderController` / `PayController` 等 | 关键写接口（orderCreate 200/min、paymentApi 300/min、refundBatch 20/min） |
| 熔断器 | `application-prod.yml` | ES（50% 失败率）、WooCommerce（60%）、邮件（70%） |
| 安全响应头 | `SecurityHeadersConfig` | HSTS / X-Frame-Options / CSP / Referrer-Policy |
| XSS 净化 | `XssSanitizer` | OWASP HTML Sanitizer 净化用户提交 |
| SQL 注入防护 | MyBatis `#{} | 所有 Mapper 强制使用预编译 |
| BCrypt 密码 | `PasswordEncoderConfig` | cost=12（OWASP 推荐） |
| JWT 签名 | `JwtUtil` | HS256，密钥从环境变量注入 |
| API 请求签名 | `SignatureFilter` | HMAC-SHA256 签名校验（防重放、防伪造） |
| 操作审计 | `OperationLogAspect` | 关键写操作异步落库 `mo_operation_log` |
| 审计保留清理 | `OperationLogRetentionJob` | 默认保留 90 天（GDPR 合规） |
| CORS 校验 | `WebMvcConfig` | 显式 origin 白名单（禁止 `*`） |
| MySQL TLS | `application-prod.yml` | `useSSL=true&requireSSL=true&verifyServerCertificate=true` |
| ES TLS | `application-prod.yml` | HTTPS + 自签证书校验 |

## 3. 发版 Checklist（每次发布必走）

- [ ] 已通过 CI 全部流水线（mvn verify、dependency-check、checkstyle）
- [ ] 已在 staging 环境完成冒烟测试（`docs/1PANEL_DEPLOY.md` 中的 §7 E2E 清单）
- [ ] 已生成并备份 `.env`（含所有强随机密钥）
- [ ] 数据库已备份（`mysqldump` 或 `xtrabackup`）
- [ ] 当前版本镜像已 tag（如 `moyuyo-api:1.0.0`），便于快速回滚
- [ ] 变更日志（CHANGELOG）已记录本次发版的功能/修复/影响范围
- [ ] 监控告警已就位：Prometheus 抓取 / Grafana 看板 / Alertmanager 告警规则
- [ ] 1Panel 反向代理已指向新版本（蓝绿/灰度）
- [ ] 灰度期间密切关注：日志无 ERROR 风暴、`moyuyo_ip_rate_limit_fail_open_total == 0`、
      `hikaricp_connections_usage < 80%`、`http_server_requests_seconds{quantile="0.99"} < 1s`
- [ ] 灰度完成后，全量切换流量，旧版本镜像保留 7 天便于回滚

## 4. 回滚预案

每次发版前必须确认以下回滚手段可用：

| 故障类型 | 回滚手段 | 预计耗时 |
| --- | --- | --- |
| 应用代码 Bug | 1Panel 反向代理切回旧版本镜像 tag | < 1 分钟 |
| 数据库迁移失败 | 立即停服 → DBA 评估 → 必要时手动 `DELETE FROM flyway_schema_history WHERE version='X'` | 5-15 分钟 |
| 配置错误 | 编辑 `.env` → `docker compose up -d app` | 2-5 分钟 |
| 数据损坏 | 1Panel 备份恢复（需提前验证备份可恢复） | 30 分钟 - 数小时 |
| 整个集群灾难 | 切换 DNS 到备用集群 + 启用异地备份 | 数小时 |

## 5. 已知限制与未来优化项

| 项 | 现状 | 未来优化 |
| --- | --- | --- |
| 单体应用 | 4 模块（common/dao/service/api）打包为单一 fat jar | 拆分 admin-bff 与 user-api 两套部署，灰度更精细 |
| Redis 单点 | 单 Redis 容器 | 升级 Redis Sentinel / Cluster |
| RocketMQ 单点 | 单 NameSrv + 单 Broker | 双 NameSrv + 双 Broker + Dledger 模式 |
| ES 单点 | single-node + HTTPS | 至少 3 节点 + ES ILM |
| 异地多活 | 仅单机房 | 双机房 + 数据库主从 + 流量调度 |
| 灰度发布 | 仅镜像级别 | 引入 Open Service Mesh（OSM）按 header / userId 灰度 |

---

> 最后更新：2026-08-06
> 维护人：SRE / 后端负责人
> 紧急联系：通过 1Panel 报警通道或值班手机