# MOYUYO 一键部署脚本

> 适用：1Panel 轻量服务器（Debian/Ubuntu/CentOS），已安装 Docker 24+ 与 Docker Compose V2。

## 目录结构

```
deploy/
├── README.md            # 本文件
├── package-upload.ps1   # Windows 本地打包（运行后产出 .zip）
├── deploy.sh            # 服务器端一键部署主脚本
├── init-env.sh          # 自动生成 .env 强随机密钥
├── backup.sh            # 每日备份（MySQL + 日志）
└── update.sh            # 在线升级 + 一键回滚
```

## 5 分钟上手流程

### 步骤 1：本地打包

在 Windows PowerShell 中进入项目根目录：

```powershell
cd D:\MOYUYOWPC\moyuyo-server
.\deploy\package-upload.ps1
```

输出文件：`deploy\moyuyo-server_<时间戳>.zip`

### 步骤 2：上传到服务器

任选一种方式：

| 方式 | 命令 |
| --- | --- |
| 1Panel 文件管理器 | 直接拖拽 zip 到 `/opt/moyuyo/` |
| WinSCP / FileZilla | 上传到 `/opt/moyuyo/` |
| PowerShell + OpenSSH | `scp deploy\moyuyo-server_*.zip root@<IP>:/opt/moyuyo/` |

### 步骤 3：服务器端解压与部署

```bash
ssh root@<1panel-ip>

cd /opt/moyuyo
# 解压（如上传的是 zip）
unzip -o moyuyo-server_*.zip -d moyuyo-server

cd moyuyo-server
chmod +x deploy/*.sh

# 首次部署：自动生成 .env 强随机密钥
./deploy/init-env.sh

# 编辑 .env 补全第三方密钥（Stripe / PayPal / WooCommerce）和真实域名
nano .env

# 一键拉起整套服务（MySQL + Redis + ES + RocketMQ + App）
./deploy/deploy.sh
```

### 步骤 4：1Panel 配置反向代理

在 1Panel 面板 → 网站 → 反向代理：

| 域名 | 反代目标 | 备注 |
| --- | --- | --- |
| `api.your-domain.com` | `http://127.0.0.1:8080` | API 与管理后台共用 8080 |
| 或 `your-domain.com` | `http://127.0.0.1:8080` | 单一域名 |

**HTTPS**：在 1Panel 申请 Let's Encrypt 证书并启用。

**HSTS**：在 1Panel 网站设置 → 配置 Nginx 头部：

```
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
```

### 步骤 5：放行健康检查（可选）

`actuator` 端口 9090 已绑定 127.0.0.1，仅供本机 Prometheus 抓取，**无需对外暴露**。

---

## 常用命令

```bash
# 查看运行状态
sudo ./deploy/deploy.sh --status

# 修改代码后重新构建 + 重启
sudo ./deploy/deploy.sh --rebuild

# 仅重启某个容器
cd /opt/moyuyo/moyuyo-server
docker compose --env-file .env restart app

# 查看实时日志
docker compose --env-file .env logs -f app

# 进入容器调试
docker compose --env-file .env exec app sh

# 每日备份
sudo ./deploy/backup.sh

# 在线升级（保留旧镜像以便回滚）
sudo ./deploy/update.sh

# 一键回滚到上一个版本
sudo ./deploy/update.sh --rollback
```

---

## 定时任务（1Panel 计划任务）

| 时间 | 命令 | 作用 |
| --- | --- | --- |
| 每天 03:00 | `sudo /opt/moyuyo/moyuyo-server/deploy/backup.sh` | MySQL + 日志备份 |
| 每周日 04:00 | `docker system prune -af --filter "until=168h"` | 清理 7 天前的悬挂镜像 |

---

## 常见问题

### Q1：首次部署等了 10 分钟还没起来？

**A**：首次会拉取 elasticsearch:8.13.4、rocketmq:5.3.2 等基础镜像（约 1.5GB），国内网络可能 5-10 分钟。如卡在 `Pulling elasticsearch`，可手动预拉取：

```bash
docker pull elasticsearch:8.13.4
docker pull mysql:8.0.36
docker pull redis:7.2-alpine
docker pull apache/rocketmq:5.3.2
```

### Q2：`ProdConfigValidator` 阻断启动？

**A**：`.env` 中有占位符未替换。重新运行：

```bash
sudo ./deploy/init-env.sh --force
# 然后编辑 .env 补全 Stripe / PayPal / WooCommerce 真实密钥
```

### Q3：升级后健康检查失败？

**A**：`update.sh` 会自动回滚到上一个镜像。如果自动回滚也失败：

```bash
docker images | grep moyuyo-api
# 查看所有历史镜像
docker compose --env-file .env down app
docker tag moyuyo-api:previous moyuyo-api:latest
docker compose --env-file .env up -d app
```

### Q4：磁盘满了怎么办？

```bash
# 查看占用
docker system df
du -sh /opt/moyuyo/*

# 清理旧备份（保留 30 天）
find /opt/moyuyo/backup -mtime +30 -delete

# 清理悬挂镜像
docker image prune -af

# 清理应用日志（容器内）
docker exec moyuyo-server sh -c "find /var/log/moyuyo -name '*.log.*' -mtime +7 -delete"
```

### Q5：如何重置管理员密码？

```bash
docker compose --env-file .env exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" moyuyo_prod \
  -e "UPDATE mo_admin_user SET password_hash = '\$(new BCrypt hash)' WHERE username = 'moyuyo_admin';"
```

生成 BCrypt hash（在应用容器内）：

```bash
docker compose --env-file .env exec app sh
java -cp app.jar org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
```

---

## 安全检查清单（部署后 5 分钟自查）

- [ ] `curl http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`
- [ ] `curl http://127.0.0.1:8080/admin/` 返回 200 HTML
- [ ] 浏览器访问 `https://your-domain.com/admin/` 能打开登录页
- [ ] 用默认管理员账号登录后台成功
- [ ] `docker compose ps` 所有服务都是 `healthy`
- [ ] `.env` 权限是 `600`（`stat -c '%a' .env`）
- [ ] MySQL truststore 已生成（`ls -la /opt/moyuyo/certs/`）
- [ ] 1Panel 反向代理已启用 HTTPS + HSTS
- [ ] 服务器防火墙已放行 80/443，关闭 8080/9090 对外

---

## 后续运维

- 监控接入：见 [../monitoring/prometheus.yml](../monitoring/prometheus.yml) 与 [../monitoring/alerts/moyuyo-alerts.yml](../monitoring/alerts/moyuyo-alerts.yml)
- 故障应急：见 [../docs/RUNBOOK.md](../docs/RUNBOOK.md)
- 容量规划：见 [../docs/CAPACITY_PLANNING.md](../docs/CAPACITY_PLANNING.md)
- 性能审计：见 [../docs/PERFORMANCE_AUDIT.md](../docs/PERFORMANCE_AUDIT.md)