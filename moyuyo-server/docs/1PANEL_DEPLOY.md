# MOYUYO 1Panel 部署指南

本文档介绍在 1Panel 轻量服务器管理面板上部署 MOYUYO 后端的完整流程。

## 1. 环境要求

| 组件 | 最低版本 | 推荐 |
| --- | --- | --- |
| 1Panel | 1.10+ | 最新 |
| Docker | 24+ | 26+ |
| Docker Compose | v2 | v2.20+ |
| 内存 | 4 GB | 8 GB+ |
| CPU | 2 核 | 4 核+ |
| 磁盘 | 60 GB SSD | 100 GB+ |

## 2. 安装 Docker / Compose（1Panel 默认已安装）

如果未安装，在 1Panel 面板 **应用商店** → **Docker** 自行安装。

## 3. 上传项目代码

将 `moyuyo-server` 目录上传到 1Panel 服务器，例如：

```bash
scp -r moyuyo-server root@<1panel-server-ip>:/opt/moyuyo/
```

## 4. 准备 `.env`

```bash
cd /opt/moyuyo/moyuyo-server
cp .env.example .env
chmod 600 .env
```

使用 `openssl` 生成强随机密钥：

```bash
# JWT 签名密钥（至少 32 字节）
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env

# API 签名密钥（至少 32 字符）
sed -i "s|^API_SIGN_SECRET=.*|API_SIGN_SECRET=$(openssl rand -hex 32)|" .env

# MySQL / Redis / ES 密码
sed -i "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)|" .env
sed -i "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)|" .env
sed -i "s|^ELASTICSEARCH_PASSWORD=.*|ELASTICSEARCH_PASSWORD=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)|" .env

# 管理员密码（至少 12 位，混合大小写+数字+特殊字符）
sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$(openssl rand -base64 16 | tr -dc 'A-Za-z0-9!@#%^&*' | head -c 16)|" .env
```

手工补齐：Stripe / PayPal / WooCommerce 等第三方密钥（见 `.env.example`）。

## 5. 构建并启动

```bash
# 1. 编译 jar（首次会下载大量依赖，约 5-10 分钟）
mvn -DskipTests package -B

# 2. 构建 Docker 镜像
docker compose build app

# 3. 启动所有服务
docker compose --env-file .env up -d

# 4. 查看启动日志
docker compose logs -f app
```

启动成功后等待约 60 秒，应用应进入就绪状态。

## 6. 健康检查

```bash
# 本机回环访问（端口已在 docker-compose 中绑定 127.0.0.1）
curl http://127.0.0.1:8080/actuator/health
# 期望：{"status":"UP"}
```

## 7. 配置 1Panel 反向代理（Nginx / OpenResty）

进入 1Panel 面板 → **网站** → **创建网站** → **反向代理**：

| 项 | 值 |
| --- | --- |
| 主域名 | `api.moyuyo.com` |
| 协议 | HTTPS（先用 1Panel 自动申请 Let's Encrypt 证书） |
| 反代地址 | `http://127.0.0.1:8080` |
| 启用 WebSocket | 否 |
| 启用 gzip | 是 |

### 7.1 关键 Nginx 配置（手工追加）

1Panel 默认配置只覆盖静态资源 / 转发。需要在 **配置文件** 中**追加**以下内容以补齐生产安全头与限流：

```nginx
# ============== API 服务（api.moyuyo.com） ==============
server {
    listen 80;
    server_name api.moyuyo.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.moyuyo.com;

    ssl_certificate     /opt/1panel/certs/api.moyuyo.com/cert.pem;
    ssl_certificate_key /opt/1panel/certs/api.moyuyo.com/key.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;

    # 限制上传大小（与后端 tomcat.max-swallow-size 一致）
    client_max_body_size 20m;

    # 安全响应头（与 SecurityHeadersFilter 互补；防止应用配置遗漏）
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # 隐藏版本号
    server_tokens off;

    # 不暴露 actuator（仅内网访问）
    location /actuator/ {
        allow 10.0.0.0/8;
        allow 172.16.0.0/12;
        allow 192.168.0.0/16;
        deny all;
        proxy_pass http://127.0.0.1:8080;
    }

    # 限流（API 接口）
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

    # 管理后台（强制 HTTPS + IP 白名单，可选）
    location / {
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:8080;
    }
}

# ============== 管理后台（admin.moyuyo.com） ==============
# 可选：与管理后台域名隔离，单独配置 IP 白名单
server {
    listen 443 ssl http2;
    server_name admin.moyuyo.com;

    ssl_certificate     /opt/1panel/certs/admin.moyuyo.com/cert.pem;
    ssl_certificate_key /opt/1panel/certs/admin.moyuyo.com/key.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # 仅允许办公网 IP（按需调整）
    allow 203.0.113.0/24;
    deny all;

    location / {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

应用配置后，在 1Panel 面板 → **网站** → **配置** → **重载 OpenResty**。

## 8. 数据库备份（每日定时）

进入 1Panel → **计划任务** → **新建任务**：

```bash
# 每日凌晨 3 点备份
0 3 * * * /opt/moyuyo/moyuyo-server/scripts/backup/backup-mysql.sh \
  --container moyuyo-mysql \
  --db moyuyo_prod \
  --backup-dir /opt/1panel/backup/mysql
```

将脚本 `scripts/backup/backup-mysql.sh` 放到该路径（容器化脚本见仓库）。

## 9. 监控接入

1. 在 1Panel **应用商店** 安装 **Prometheus** 与 **Grafana**
2. 复制 `monitoring/prometheus.yml` 到 Prometheus 配置目录
3. 复制 `monitoring/alerts/moyuyo-alerts.yml` 作为告警规则
4. 在 Grafana 导入 JVM / Spring Boot Dashboard

## 10. 常见问题

### 10.1 容器反复重启

查看 `docker compose logs app` 的最后 50 行：
- 若出现 `[FATAL] 生产环境启动失败：以下必填配置缺失`：检查 `.env` 是否完整
- 若出现 `Connection refused: mysql`：MySQL 未就绪，等待 healthcheck 通过

### 10.2 启动后健康检查持续 DOWN

ES 集群首次启动需要 30-60 秒生成证书。可临时将 `application-prod.yml` 中
`management.health.elasticsearch.enabled: false` 调整后重启（仅作调试）。

### 10.3 磁盘满

```bash
# 清理 ES 旧索引
docker exec moyuyo-elasticsearch \
  curl -kfs -u elastic:$ELASTICSEARCH_PASSWORD \
  -XDELETE 'https://localhost:9200/old-index-*'

# 清理日志
docker exec moyuyo-server \
  find /var/log/moyuyo -name "*.log.*.gz" -mtime +7 -delete
```

### 10.4 升级应用版本

```bash
cd /opt/moyuyo/moyuyo-server
git pull
mvn -DskipTests package -B
docker compose build app
docker compose up -d app
```

## 11. 卸载

```bash
cd /opt/moyuyo/moyuyo-server
docker compose --env-file .env down -v   # -v 同时删除 volumes（会清空数据！）
```

如需保留数据，去掉 `-v`。