# MOYUYO 后端 · Docker 容器清单（打印版）

> 用途：服务器运维现场速查 / 交接手册 / 故障排查参考
> 适用：moyuyo-server 后端 8 个 Docker 容器
> 打印建议：A4 纵向，正反面各 1 页（共 2 页）

---

## 一、容器总览

| 序号 | 容器名称 | 镜像 | 端口映射 | 用途分类 | 是否常驻 |
|:----:|---------|------|---------|---------|:------:|
| 1 | moyuyo-server | moyuyo-server | 8080:8080 | 应用服务 | 是 |
| 2 | moyuyo-mysql | mysql:8.0 | 3306:3306 | 数据存储 | 是 |
| 3 | moyuyo-redis | redis:7.2-alpine | 6380:6379 | 缓存中间件 | 是 |
| 4 | moyuyo-elasticsearch | elasticsearch | 9200:9200 | 搜索引擎 | 是 |
| 5 | moyuyo-rocketmq-namesrv | apache/rocketmq | 9876:9876 | 消息队列（路由）| 是 |
| 6 | moyuyo-rocketmq-broker | apache/rocketmq | 10909:10909 | 消息队列（存储）| 是 |
| 7 | moyuyo-mysql-certs-init | eclipse-temurin | 无 | 一次性初始化 | 否 |
| 8 | moyuyo-es-certs-init | elasticsearch | 无 | 一次性初始化 | 否 |

> 注：7、8 号容器为初始化容器（init-only），运行一次后退出，无需持续运行。

---

## 二、容器逐一说明

### 1. moyuyo-server（核心应用）

- 镜像：`moyuyo-server`（自定义构建，Dockerfile 位于 moyuyo-server 目录）
- 端口：宿主机 8080 → 容器 8080
- 作用：MOYUYO 后端 API 服务，对外提供所有业务接口，处理 APP 端与管理侧的 HTTP 请求
- 依赖：MySQL、Redis、Elasticsearch、RocketMQ（缺一不可启动失败）
- 数据卷：`moyuyo-uploads`（用户/管理员上传文件）、`moyuyo-logs`（应用日志）
- 健康检查：HTTP `/actuator/health`

### 2. moyuyo-mysql（主数据库）

- 镜像：`mysql:8.0`
- 端口：宿主机 3306 → 容器 3306
- 作用：存储用户、商品、订单、营销、社区等全部业务数据
- 数据卷：`moyuyo-mysql-data`（持久化数据库文件）
- 默认账号：`moyuyo` / 业务库：`moyuyo_dev`（生产应改为 `moyuyo_prod`）
- 生产建议：开启 TLS 加密（配合 moyuyo-mysql-certs-init 容器）

### 3. moyuyo-redis（缓存中间件）

- 镜像：`redis:7.2-alpine`
- 端口：宿主机 6380 → 容器 6379
- 作用：热点数据缓存、Session、限流、Token 黑名单、分布式锁
- 数据卷：`moyuyo-redis-data`（持久化 RDB/AOF）
- 说明：宿主机使用 6380 是为了避开本机可能已存在的 6379 端口

### 4. moyuyo-elasticsearch（全文搜索）

- 镜像：`elasticsearch`（官方镜像，版本对齐 docker-compose.yml）
- 端口：宿主机 9200 → 容器 9200
- 作用：商品/内容全文检索、日志分析
- 数据卷：`moyuyo-es-data`（持久化索引数据）
- 生产建议：启用 x-pack security + HTTPS（配合 moyuyo-es-certs-init 容器）

### 5. moyuyo-rocketmq-namesrv（消息队列 · 路由）

- 镜像：`apache/rocketmq`（Namesrv 模式）
- 端口：宿主机 9876 → 容器 9876
- 作用：RocketMQ 的 NameServer，负责 Broker 注册与路由发现
- 特点：轻量、无状态、生产环境可部署多个互不依赖

### 6. moyuyo-rocketmq-broker（消息队列 · 存储）

- 镜像：`apache/rocketmq`（Broker 模式）
- 端口：宿主机 10909 → 容器 10909（fastListen 端口）
- 作用：RocketMQ 的 Broker，消息的实际存储与转发
- 依赖：必须先启动 namesrv 并能解析到 `rocketmq-namesrv` 主机名
- 数据卷：`moyuyo-rocketmq-data`（持久化 commitlog/consumerqueue）

### 7. moyuyo-mysql-certs-init（MySQL 证书初始化）

- 镜像：`eclipse-temurin`（基于 Temurin JRE 运行环境）
- 端口：无（仅跑一次性任务后退出）
- 作用：基于 MySQL 自签 CA 自动导出 PKCS12 信任库，供应用容器启用 TLS 连接
- 数据卷：`moyuyo-mysql-certs`（生成的证书文件）
- 触发：仅在首次部署或证书过期时执行；正常运行时已 exit
- 验证：`docker logs moyuyo-mysql-certs-init` 末尾无报错即可

### 8. moyuyo-es-certs-init（ES 证书初始化）

- 镜像：`elasticsearch`（复用 ES 镜像生成证书）
- 端口：无（仅跑一次性任务后退出）
- 作用：为 ES 集群生成安全证书（HTTPS + 节点间认证）
- 数据卷：`moyuyo-es-certs`（生成的 elasticsearch.p12）
- 触发：仅在首次部署或证书过期时执行；正常运行时已 exit
- 验证：`docker logs moyuyo-es-certs-init` 末尾无报错即可

---

## 三、整体架构关系

```
+---------------------------+
|      moyuyo-server        |  应用层（API）
+------+--------+-----------+
       |        |       |
       v        v       v
+--------+ +--------+ +--------+ +----------------+
| mysql  | | redis  | |   ES   | | rocketmq-broker|
+--------+ +--------+ +--------+ +-------+--------+
   ^                            ^           ^
   |                            |           |
+-----------+            +------------+ +-----------------+
| mysql-    |            | es-certs-  | | rocketmq-       |
| certs-init|            | init       | | namesrv         |
+ (初始化)  +            + (初始化)   + + (路由注册)      +
```

---

## 四、依赖与启动顺序

启动顺序严格遵循以下链路：

1. `moyuyo-mysql-certs-init`（首先生成 MySQL 信任库证书）
2. `moyuyo-mysql`（数据库启动）
3. `moyuyo-redis`（缓存启动）
4. `moyuyo-es-certs-init`（生成 ES 证书）
5. `moyuyo-elasticsearch`（ES 启动）
6. `moyuyo-rocketmq-namesrv`（Namesrv 启动）
7. `moyuyo-rocketmq-broker`（Broker 依赖 Namesrv）
8. `moyuyo-server`（应用启动，依赖前 6 个全部就绪）

> 实际部署由 `docker-compose.yml` 的 `depends_on` + `healthcheck` 自动编排，无需人工按序执行。

---

## 五、常用运维命令速查

```bash
# 查看所有容器运行状态
docker ps -a --filter "name=moyuyo-"

# 实时查看应用日志（最近 100 行 + 持续输出）
docker logs -f --tail 100 moyuyo-server

# 进入 MySQL 容器执行 SQL
docker exec -it moyuyo-mysql mysql -u moyuyo -p moyuyo_dev

# 进入 Redis 容器查看键
docker exec -it moyuyo-redis redis-cli -a <密码>

# 重启单个服务（不影响其他容器）
docker restart moyuyo-server

# 查看资源占用
docker stats moyuyo-server moyuyo-mysql moyuyo-redis

# 重新生成证书（首部署或证书过期）
docker compose up mysql-certs-init es-certs-init
```

---

## 六、关键数据卷对照表

| 卷名 | 挂载到容器 | 作用 | 备份优先级 |
|------|----------|------|:---------:|
| moyuyo-mysql-data | /var/lib/mysql | 数据库文件 | 极高 |
| moyuyo-redis-data | /data | Redis 持久化 | 中 |
| moyuyo-es-data | /usr/share/elasticsearch/data | 索引数据 | 高 |
| moyuyo-rocketmq-data | /root/store | 消息日志 | 中 |
| moyuyo-uploads | /app/uploads | 用户上传文件 | 高 |
| moyuyo-logs | /var/log/moyuyo | 应用日志 | 低 |
| moyuyo-mysql-certs | /opt/mysql-certs | MySQL 证书 | 低 |
| moyuyo-es-certs | /opt/es-certs | ES 证书 | 低 |

---

## 七、故障定位优先级

应用层（moyuyo-server）异常时，按以下顺序排查：

1. `moyuyo-server` 日志 → 业务异常
2. `moyuyo-mysql` → 连接失败 / SQL 报错
3. `moyuyo-redis` → 缓存超时 / Session 失效
4. `moyuyo-elasticsearch` → 搜索接口 500
5. `moyuyo-rocketmq-broker` → 异步消息积压
6. `moyuyo-rocketmq-namesrv` → 路由失败（broker 无法注册）
7. `*-certs-init` 容器 → TLS 握手失败（重启后证书丢失）

---

文档版本：v1.0
生成时间：2026-09-21
适用范围：moyuyo-server 当前生产 docker-compose 拓扑
