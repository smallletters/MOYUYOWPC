# MOYUYO 后端部署文档（生产环境）

> 适用：1Panel 轻量服务器 / 任意已装 Docker 24+ 的 Linux 主机
> 流程对应：`deploy/package-upload.ps1` → `deploy/init-env.sh` → `deploy/deploy.sh` → 1Panel 反向代理 → 备份 / 升级
> 端口：业务 **8080**（仅 127.0.0.1）、监控 **9090**（仅 127.0.0.1），由前置反向代理对外暴露 80/443
> 涉及文件：`Dockerfile`、`docker-compose.yml`、`.env.example`、`moyuyo-api/src/main/resources/{application.yml, application-prod.yml}`、`deploy/*`

---

## 1. 部署架构一览

```
Internet
   │
   ▼ HTTPS (80/443)
┌─────────────────────────────┐
│  1Panel 反向代理 (OpenResty)│  ← 终止 TLS、HSTS、白名单、限流
│  api.your-domain.com        │
└────────────┬────────────────┘
             │ 127.0.0.1:8080
             ▼
┌─────────────────────────────┐
│  moyuyo-server (Spring Boot)│  ← Java 25 / Spring Boot 3.5.16 / JDK JRE Alpine
│  业务 :8080   监控 :9090    │     /admin（SPA）+ /api（REST）+ /actuator
└──┬───────┬───────┬───────┬───┘
   │       │       │       │
   ▼       ▼       ▼       ▼
 MySQL   Redis    ES      RocketMQ
 :3306   :6379   :9200    :9876 / :10911
```

**端口绑定**：所有依赖服务端口在 `docker-compose.yml` 中均绑定 `127.0.0.1`，**对外仅 8080/9090 可见**，由 1Panel Nginx 转发。

---

## 2. 服务器环境要求

| 组件 | 最低 | 推荐 |
|---|---|---|
| 操作系统 | Debian 12 / Ubuntu 22.04 / CentOS 9 | Debian 12 |
| 1Panel | 1.10+ | 最新 |
| Docker | 24+ | 26+ |
| Docker Compose | V2（`docker compose` 命令） | V2.20+ |
| 内存 | 8 GB | 16 GB+ |
| CPU | 4 核 | 8 核+ |
| 磁盘 | 80 GB SSD | 120 GB+ |
| 内核 | 支持 `memlock`（ES 需要 `bootstrap.memory_lock: true`） | 5.15+ |

> ⚠️ `bootstrap.memory_lock=true` 要求宿主 limits 解除 unlimited 或满足 ES 启动堆，否则 ES 启动失败。

---

## 3. 服务器初始准备

```bash
# 1. SSH 登录
ssh root@<server-ip>

# 2. 创建部署目录
mkdir -p /opt/moyuyo
cd /opt/moyuyo

# 3. （可选）解除 memlock 限制以满足 ES bootstrap.memory_lock
echo -e "* soft memlock unlimited\n* hard memlock unlimited" >> /etc/security/limits.conf

# 4. 确认 Docker / Compose 已就绪
docker --version            # 期望 24+
docker compose version      # 期望 v2.20+
```

---

## 4. 本地打包（Windows PowerShell）

仓库自带 `deploy/package-upload.ps1`，会自动排除 `target/`、`.git/`、`node_modules/`、`.env` 等：

```powershell
cd D:\MOYUYOWPC\moyuyo-server
.\deploy\package-upload.ps1
```

输出：`deploy\moyuyo-server_<时间戳>.zip`（典型 10~30 MB）。

> ⚠️ 必须在**独立 PowerShell 窗口**运行（避免 IDE 文件锁）。
> ⚠️ `.env` 已被显式排除，避免误把本地密钥传上服务器。

---

## 5. 上传到服务器

任选一种：

| 方式 | 命令 |
|---|---|
| 1Panel 文件管理器 | 把 zip 拖拽到 `/opt/moyuyo/` |
| WinSCP / FileZilla | 上传到 `/opt/moyuyo/` |
| scp（OpenSSH） | `scp deploy\moyuyo-server_*.zip root@<IP>:/opt/moyuyo/` |

---

## 6. 服务器端解压与 .env 生成

```bash
ssh root@<1panel-ip>
cd /opt/moyuyo

# 1. 解压
unzip -o moyuyo-server_*.zip -d moyuyo-server
cd moyuyo-server

# 2. 给脚本加执行权限
chmod +x deploy/*.sh

# 3. 生成强随机 .env（覆盖 .env.example 占位项）
./deploy/init-env.sh
#    --force 覆盖已有 .env
```

`init-env.sh` 会自动生成：

- MySQL root / 应用 / 监控账号密码
- Redis / Redis-Exporter 密码
- JWT_SECRET（48 字节 base64）、API_SIGN_SECRET（32 字节 hex）
- ES 密码、ES truststore 密码
- ADMIN_USERNAME（避免 admin/root）、ADMIN_PASSWORD（16 位）
- CORS 域名占位（待你手工替换为真实域名）
- 第三方密钥（Stripe / PayPal / WooCommerce）**只打印缺失提醒**，需手工填写

输出末尾会打印：

```
==== 下一步 ====
1) 编辑 .env 补全 Stripe / PayPal / WooCommerce 第三方密钥
2) 编辑 .env 修改 MOYUYO_CORS_ORIGINS 为真实域名
3) 执行 ./deploy/deploy.sh 开始拉起服务
```

编辑 `.env`：

```bash
nano .env
```

**必须手工替换的字段**（脚本无法生成）：

```bash
# Stripe（https://dashboard.stripe.com/apikeys）
STRIPE_SECRET_KEY=sk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx

# PayPal（https://developer.paypal.com/dashboard/applications）
PAYPAL_CLIENT_ID=xxx
PAYPAL_CLIENT_SECRET=xxx
PAYPAL_WEBHOOK_ID=xxx

# WooCommerce（WooCommerce 后台 → 设置 → 高级 → REST API）
WOOCOMMERCE_URL=https://your-store.com
WOOCOMMERCE_CONSUMER_KEY=ck_xxx
WOOCOMMERCE_CONSUMER_SECRET=cs_xxx

# 真实前端域名（逗号分隔，禁止 *）
MOYUYO_CORS_ORIGINS=https://admin.your-domain.com,https://www.your-domain.com
```

---

## 7. 一键部署

```bash
sudo ./deploy/deploy.sh
```

`deploy.sh` 自动化步骤：

1. **权限与依赖检查**：必须 root，Docker / Compose v2 必须就绪
2. **.env 校验**：必填项缺失 / 占位符（`your_xxx` / `REPLACE_WITH_xxx`）→ 阻断启动
3. **生成 MySQL truststore**：`/opt/moyuyo/certs/mysql-ca.p12`（首次部署）
4. **拉取基础镜像**：`mysql:8.0.36` / `redis:7.2-alpine` / `elasticsearch:8.13.4` / `apache/rocketmq:5.3.2` / `prom/mysqld-exporter:v0.15.1` / `oliver006/redis_exporter:v1.58.0` / `apache/rocketmq-exporter:0.0.3`
5. **构建后端镜像**（首次约 5-10 分钟）：
   - 多阶段：`maven:3.9-eclipse-temurin-25` 构建 → `eclipse-temurin:25-jre-alpine` 运行
   - 非 root 用户（UID 1000）、`tini` 处理 PID 1、ZGC + NMT + HeapDump
6. **按顺序启动**：MySQL → Redis → ES（首次生成证书 60-120s）→ RocketMQ（namesrv + broker）→ app → exporters
7. **健康检查等待**：每个服务最长 180s 超时
8. **最终验证**：本地 `curl http://127.0.0.1:8080/actuator/health` 必须返回 200 + `{"status":"UP"}`

### 启动成功的输出特征

```
[INFO] 拉取镜像...
[INFO] 构建后端镜像...
[INFO] 启动 MySQL...
[ OK ] mysql 已 healthy
[INFO] 启动 Redis...
[ OK ] redis 已 healthy
[INFO] 启动 Elasticsearch...
[ OK ] elasticsearch 已 healthy
[INFO] 启动 RocketMQ...
[ OK ] rocketmq-namesrv 已 healthy
[ OK ] rocketmq-broker 已 healthy
[INFO] 启动后端应用...
[ OK ] app 已 healthy
[ OK ] 后端 /actuator/health 返回 200 ✓
[ OK ] 管理后台 /admin/ 返回 200 ✓
========== 部署完成 ==========
```

---

## 8. 1Panel 反向代理配置

> 完整 Nginx 模板与解释见 [`docs/1PANEL_DEPLOY.md`](./1PANEL_DEPLOY.md) 第 7 节。下面是最小化配置。

进入 **1Panel → 网站 → 创建网站 → 反向代理**：

| 项 | 值 |
|---|---|
| 主域名 | `api.your-domain.com`（或 `your-domain.com`） |
| 协议 | HTTPS（先在 **证书** 中申请 Let's Encrypt） |
| 反代地址 | `http://127.0.0.1:8080` |
| 启用 WebSocket | 否 |
| 启用 gzip | 是 |

进入 **配置文件**，在 `server { ... }` 中追加：

```nginx
client_max_body_size 20m;

# 安全响应头（与 SecurityHeadersFilter 互补）
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
server_tokens off;

# 不暴露 actuator（仅内网访问）
location /actuator/ {
    allow 127.0.0.1;
    deny all;
    proxy_pass http://127.0.0.1:8080;
}

# API 接口限流
location /api/ {
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=200r/s;
    limit_req zone=api_limit burst=400 nodelay;
    limit_conn_zone $binary_remote_addr zone=api_conn:10m;
    limit_conn api_conn 50;

    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_pass http://127.0.0.1:8080;
}

location / {
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_pass http://127.0.0.1:8080;
}
```

最后 **重载 OpenResty**。

---

## 9. 防火墙与端口规划

服务器侧**仅放行 80/443** 给公网，依赖服务全部回环：

```bash
# UFW 示例
ufw default deny incoming
ufw allow 80/tcp
ufw allow 443/tcp
# （可选）仅办公网访问 SSH
ufw allow from <your-office-ip> to any port 22
ufw enable

# 验证：8080/9090/3306/6379/9200/9876 不应在公网监听
ss -tlnp | grep -E ":(8080|9090|3306|6379|9200|9876|10911)"
# 期望：127.0.0.1:xxxx，而非 0.0.0.0:xxxx
```

---

## 10. 验证清单

部署完成 5 分钟内逐项检查：

- [ ] `curl http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://127.0.0.1:8080/admin/` → 200 HTML（admin SPA）
- [ ] `docker compose --env-file .env ps` → 所有服务 `healthy`
- [ ] `ls -la /opt/moyuyo/certs/mysql-ca.p12` → 文件存在，`600` 权限
- [ ] `stat -c '%a' .env` → `600`
- [ ] 浏览器 `https://api.your-domain.com/admin/` → 能打开登录页
- [ ] 用 `.env` 中 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 登录后台成功
- [ ] 触发一次业务接口（如访问首页）→ Prometheus 9090 看到 `http_server_requests_seconds_count` 增长

---

## 11. 日常运维命令

```bash
cd /opt/moyuyo/moyuyo-server

# 查看所有服务状态
sudo ./deploy/deploy.sh --status

# 改代码后重新构建 + 重启（保留 .env 与 volumes）
sudo ./deploy/deploy.sh --rebuild

# 仅重启某个服务
docker compose --env-file .env restart app

# 查看应用实时日志
docker compose --env-file .env logs -f app

# 进入应用容器调试
docker compose --env-file .env exec app sh

# 查看 JVM 堆 / OOM dump（容器内）
ls /app/heapdump/

# 每日备份（MySQL + 日志，保留 30 天）
sudo ./deploy/backup.sh
sudo ./deploy/backup.sh --mysql-only        # 仅备份数据库
sudo ./deploy/backup.sh --retention 60      # 保留 60 天

# 在线升级 + 自动回滚
sudo ./deploy/update.sh
sudo ./deploy/update.sh --tag v1.2.0        # 升级到指定 Git tag
sudo ./deploy/update.sh --rollback          # 回滚到上一个镜像
```

---

## 12. 备份策略

`deploy/backup.sh` 包含：

- **MySQL 全量逻辑备份**：`mysqldump --single-transaction --master-data=2 --routines --triggers --events --hex-blob`，gzip 压缩
- **应用日志归档**：`tar czf /var/log/moyuyo`，便于事后分析
- **过期清理**：`find -mtime +30 -delete`（可调）

挂到 1Panel 计划任务：

| 时间 | 命令 | 作用 |
|---|---|---|
| 每天 03:00 | `sudo /opt/moyuyo/moyuyo-server/deploy/backup.sh` | MySQL + 日志备份 |
| 每周日 04:00 | `docker system prune -af --filter "until=168h"` | 清理 7 天前的悬挂镜像 |

备份目录结构（默认）：

```
/opt/moyuyo/backup/
├── mysql/                   # mysqldump .sql.gz
│   └── moyuyo_moyuyo_prod_20260824_030000.sql.gz
├── logs/                    # 应用日志归档
│   └── moyuyo-logs_20260824_030000.tar.gz
└── images/                  # update.sh 保留的旧镜像（回滚用）
    └── moyuyo-api_latest.tar.gz
```

---

## 13. 在线升级与回滚

`deploy/update.sh` 流程：

1. 备份当前镜像到 `/opt/moyuyo/backup/images/`
2. `git pull`（或切到指定 tag）
3. `mvn -DskipTests package` 编译新 jar
4. `docker compose build app` 构建新镜像
5. `docker compose up -d app` 滚动重启
6. 健康检查 `curl http://127.0.0.1:9090/actuator/health/liveness`（**应用端口独立 9090**，绕过业务过滤器链）
7. 不健康自动 `do_rollback`：从 `images/` 加载旧镜像并重启

```bash
# 升级到最新代码
sudo ./deploy/update.sh

# 升级到指定 Git tag
sudo ./deploy/update.sh --tag v1.2.0

# 紧急回滚
sudo ./deploy/update.sh --rollback
```

> ⚠️ **优雅停机**：`docker-compose.yml` 中 `stop_grace_period: 45s` + Spring lifecycle 30s + OperationLog drain 10s，让 SIGTERM 期间未完成的审计/事务安全落库。

---

## 14. 监控接入（可选）

部署完成后：

1. 1Panel **应用商店** 安装 Prometheus + Grafana
2. Prometheus 配置参考 `monitoring/prometheus.yml`（已内置 6 个 exporter 抓取 job + alertmanager）
3. 告警规则拷贝 `monitoring/alerts/moyuyo-alerts.yml`（OOM / 磁盘 / 支付回调 / 限流 fail-open / 审计队列溢出 / ES red / RocketMQ 堆积）
4. Grafana 导入官方 Spring Boot / JVM Dashboard，数据源选 Prometheus

---

## 15. 常见问题

### Q1：等了 10 分钟还没起来？

`Pulling elasticsearch` 阶段会下载 ~1.5GB 基础镜像，国内网络可能 5-10 分钟。可手动预拉：

```bash
docker pull elasticsearch:8.13.4
docker pull mysql:8.0.36
docker pull redis:7.2-alpine
docker pull apache/rocketmq:5.3.2
docker pull eclipse-temurin:25-jre-alpine
```

### Q2：`ProdConfigValidator` 阻断启动？

`.env` 中存在 `your_xxx` / `REPLACE_WITH_xxx` / 空值占位符。重新生成：

```bash
sudo ./deploy/init-env.sh --force
# 然后编辑 .env 补全 Stripe / PayPal / WooCommerce 真实密钥
```

### Q3：升级后健康检查失败？

`update.sh` 自动回滚到旧镜像。如回滚也失败：

```bash
docker images | grep moyuyo-api
docker compose --env-file .env down app
docker tag moyuyo-api:previous moyuyo-api:latest
docker compose --env-file .env up -d app
```

### Q4：磁盘满了怎么办？

```bash
docker system df                         # 看 Docker 占盘
du -sh /opt/moyuyo/*                     # 看主机目录
find /opt/moyuyo/backup -mtime +30 -delete
docker image prune -af
docker exec moyuyo-server sh -c "find /var/log/moyuyo -name '*.log.*' -mtime +7 -delete"
```

### Q5：重置管理员密码

```bash
docker compose --env-file .env exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" moyuyo_prod \
  -e "UPDATE mo_admin_user SET password_hash = '\$2a\$12\$...新 hash...' WHERE username = '$(grep ^ADMIN_USERNAME= .env | cut -d= -f2)';"
```

生成 BCrypt hash（在应用容器内）：

```bash
docker compose --env-file .env exec app sh
java -cp app.jar org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
# 输入明文，复制输出 hash
```

### Q6：`./deploy/deploy.sh` 报权限错误？

必须 `root` 执行（容器需要创建 `/opt/moyuyo/certs`、挂载卷等）。SSH 后用 `sudo` 或 `su -` 切换。

### Q7：actuator 健康检查返回 DOWN 但容器还在运行？

进入容器看细节：

```bash
docker compose --env-file .env exec app sh
curl -s http://127.0.0.1:9090/actuator/health | jq
```

通常是某个依赖（ES / Redis）临时不可用导致整体 DOWN。liveness 子端点只检查进程存活：

```bash
curl http://127.0.0.1:9090/actuator/health/liveness
# K8s readiness/liveness 探针必须指向这个端点，否则会被 404 阻断
```

### Q8：访问管理后台提示 CORS 错误？

`.env` 中 `MOYUYO_CORS_ORIGINS` 没有当前访问域名（注意要写完整 scheme + 端口，如 `https://admin.your-domain.com`）。修改后重启：

```bash
# 修改 .env 后
docker compose --env-file .env restart app
```

---

## 16. 卸载

```bash
cd /opt/moyuyo/moyuyo-server
docker compose --env-file .env down      # 保留 volumes（数据/日志）
docker compose --env-file .env down -v   # 删除 volumes（清空所有数据！）
```

清理宿主目录：

```bash
rm -rf /opt/moyuyo/{certs,backup}
rm -f /opt/moyuyo/moyuyo-server_*.zip
```

---

## 17. 相关文档

- [`README.md`](../../README.md) — 项目总览、技术栈、CI/CD
- [`docs/1PANEL_DEPLOY.md`](./1PANEL_DEPLOY.md) — 1Panel 反向代理 + Nginx 模板（HSTS / IP 白名单 / 限流）
- [`docs/RUNBOOK.md`](./RUNBOOK.md) — 常见故障（OOM / 磁盘满 / 支付回调风暴 / 证书过期）应急流程
- [`docs/PRODUCTION_CHECKLIST.md`](./PRODUCTION_CHECKLIST.md) — 部署前自查（密钥 / 网络 / CORS / 健康检查 / 备份）
- [`docs/CAPACITY_PLANNING.md`](./CAPACITY_PLANNING.md) — 容量规划与扩缩容策略
- [`deploy/README.md`](../deploy/README.md) — 一键部署脚本说明
- `monitoring/prometheus.yml` + `monitoring/alerts/moyuyo-alerts.yml` — Prometheus 抓取 + 告警规则