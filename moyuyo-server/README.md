# MOYUYO 后端服务 (moyuyo-server)

宠物用品电商后端，含 C 端 API、管理后台 BFF、支付/物流/营销等业务域。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| JDK | 25 (Temurin) |
| Spring Boot | 3.5.16 |
| Maven | 3.9+ |
| MyBatis-Plus | 3.5.9 |
| MySQL | 8.0 |
| Redis | 7 |
| Elasticsearch | 8.13 |
| RocketMQ | 5.3 |
| Flyway | 10.22 |
| JJWT | 0.12.6 |

## 模块结构

```
moyuyo-server/
├── moyuyo-common/   # 公共 DTO / Result / 异常 / 过滤器 / 工具类
├── moyuyo-dao/      # MyBatis-Plus Entity / Mapper
├── moyuyo-service/  # 业务 Service（admin + user 两域并列）
├── moyuyo-api/      # REST Controller 层（启动模块）
└── moyuyo-admin/    # Vue 3 管理后台（构建产物打包进 moyuyo-api 静态资源）
```

## 环境准备

1. 安装 JDK 25 与 Maven 3.9+
2. 安装 Docker 与 Docker Compose（生产环境）
3. 复制 `.env.example` 为 `.env` 并填入真实密钥（**禁止提交到 Git**）

## 本地开发（dev profile）

```bash
# 启动 MySQL / Redis
docker compose up -d mysql redis

# 编译并启动（dev profile 自动加载演示数据）
mvn -pl moyuyo-api spring-boot:run \
  -Dspring-boot.run.profiles=dev
```

应用启动后：
- API：`http://localhost:8080/api`
- 管理后台：`http://localhost:8080/admin`
- OpenAPI 文档：`http://localhost:8080/swagger-ui.html`

## 生产部署（prod profile）

### 1. 准备环境变量

完整变量清单见 `.env.example`。生产环境必填且不可缺省：

```bash
MYSQL_PASSWORD=<强密码,16+ 位>
REDIS_PASSWORD=<强密码,16+ 位>
JWT_SECRET=<base64 随机 32 字节>
API_SIGN_SECRET=<32+ 字符>
ADMIN_USERNAME=moyuyo_admin   # 不能是 admin/root/administrator
ADMIN_EMAIL=admin@moyuyo.com
ADMIN_PASSWORD=<强密码,12+ 位>
STRIPE_SECRET_KEY=sk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx
PAYPAL_CLIENT_ID=xxx
PAYPAL_CLIENT_SECRET=xxx
PAYPAL_WEBHOOK_ID=xxx
PAYPAL_ALLOWED_ORIGINS=https://moyuyo.com,https://admin.moyuyo.com
ELASTICSEARCH_URIS=https://es-node1:9200,https://es-node2:9200
ELASTICSEARCH_PASSWORD=<强密码>
ELASTICSEARCH_TRUSTSTORE_PASSWORD=changeit
MOYUYO_CORS_ORIGINS=https://moyuyo.com,https://admin.moyuyo.com
```

### 2. 构建并启动

```bash
# 编译
mvn clean package -DskipTests

# 启动全套服务
docker compose --env-file .env up -d

# 查看启动日志
docker compose logs -f app
```

### 3. 启动期必填校验

`ProdConfigValidator` 会在 prod 启动时校验所有密钥/连接，任一缺失则**立即中止**启动并在日志中清晰列出缺失项。

### 4. 健康检查

```bash
curl http://localhost:8080/actuator/health
# 期望返回 {"status":"UP"}
```

## 备份与恢复

```powershell
# MySQL 每日备份（推荐加入 Windows 任务计划，凌晨 3 点执行）
.\scripts\backup\backup-mysql.ps1 `
  -ContainerName moyuyo-mysql `
  -DbName moyuyo_prod `
  -BackupDir D:\backups\moyuyo\mysql

# MySQL 恢复（覆盖目标库，二次确认）
.\scripts\backup\restore-mysql.ps1 `
  -ContainerName moyuyo-mysql `
  -DbName moyuyo_prod `
  -BackupFile D:\backups\moyuyo\mysql\moyuyo_moyuyo_prod_20260804_030000.sql.gz

# ES 索引快照
.\scripts\backup\backup-elasticsearch.ps1 `
  -ContainerName moyuyo-elasticsearch `
  -RepositoryName moyuyo_backup `
  -BackupDir D:\backups\moyuyo\es
```

## CI/CD

- `backend-ci.yml`：PR 触发 `mvn validate + verify`，含 Checkstyle + JaCoCo
- `backend-cd.yml`：push main 触发构建 Docker 镜像并推送到 ghcr.io

## 测试

```bash
# 全部测试
mvn verify

# 仅单测
mvn test

# 指定模块
mvn -pl moyuyo-api -am test
```

## 运维 Runbook

详见 [`docs/RUNBOOK.md`](docs/RUNBOOK.md)：常见故障（OOM/磁盘满/支付回调风暴/证书过期）与应急流程。

## 安全注意事项

1. 所有密钥通过环境变量注入，禁止硬编码
2. `.env` 必须加入 `.gitignore`（已默认）
3. 生产环境必须启用 HTTPS，由前置 Nginx/1Panel 反向代理终止 TLS
4. 数据库与 ES 仅绑定 127.0.0.1，禁止暴露公网
5. 定期轮转 `JWT_SECRET`、`ADMIN_PASSWORD`、数据库密码
6. 监控 `moyuyo-error.log` 中的 `Webhook 签名校验失败` 事件，突发增长意味着有人在探测

## License

内部项目，未授权不得外发。
