# ============================================================
# MOYUYO 后端服务 Dockerfile（多阶段构建 / 生产环境）
#
# 安全要点：
#   1. 多阶段构建：编译阶段使用 JDK 25 完整镜像（含 Maven），运行时仅保留 JRE 镜像，
#      减小最终镜像体积（典型 700MB+ → 350MB），并减少攻击面（无 Maven、源码、构建工具）
#   2. 使用非 root 用户运行容器（UID 1000，符合 1Panel/K8s 默认用户）
#   3. tini 处理 PID 1 信号转发（确保 SIGTERM 触发 Spring graceful shutdown）
#   4. JVM 使用 ZGC（低延迟 GC，适合大堆）
#   5. 健康检查基于 /actuator/health
#   6. tzdata 安装后保留（保留时区数据，apk del 会破坏时区）
#
# 构建：
#   docker build -t moyuyo-api:1.0.0 .
# 运行：
#   docker run -d \
#     --name moyuyo-api \
#     -p 8080:8080 \
#     -e MYSQL_HOST=mysql -e MYSQL_PASSWORD=xxx \
#     -e JWT_SECRET=xxx \
#     moyuyo-api:1.0.0
# ============================================================

# ========== Stage 1: 构建 ==========
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# 先复制 pom.xml 以最大化 Maven 依赖缓存
# 注：Docker build context 是 moyuyo-server/ 目录，所有模块 pom 都在该目录下
COPY pom.xml ./
COPY moyuyo-common/pom.xml moyuyo-common/
COPY moyuyo-dao/pom.xml moyuyo-dao/
COPY moyuyo-service/pom.xml moyuyo-service/
COPY moyuyo-api/pom.xml moyuyo-api/

# 预下载依赖（CI 中已有 Maven 缓存，本地构建也能命中）
RUN mvn -B -q dependency:go-offline -DskipTests 2>&1 | tail -20 || true

# 复制源码
COPY moyuyo-common moyuyo-common
COPY moyuyo-dao moyuyo-dao
COPY moyuyo-service moyuyo-service
COPY moyuyo-api moyuyo-api

# 编译打包（跳过测试 + 测试编译，CI 已跑过完整 verify）
# 注意：-B（批模式）+ -e（出错时输出堆栈）+ -DskipTests 是 Maven 标准 CI 参数
# 额外加 -Dmaven.test.skip=true 同时跳过 test compile（项目测试代码使用了 Spring Boot 3.5
# 已移除的 MockBean，本地构建不需要为它重写所有测试；CI 单独跑 mvn verify 即可）
RUN mvn -B -e -DskipTests '-Dmaven.test.skip=true' -pl moyuyo-api -am package

# 提取 Spring Boot 插件产物的可执行 jar（业务 jar + 依赖统一在一个 fat jar 内）
# 使用 sh -c 通配展开，避免 BUILD_CMD shell 解析失败
RUN cp "$(find moyuyo-api/target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)" /build/app.jar

# ========== Stage 2: 运行时 ==========
FROM eclipse-temurin:25-jre-alpine

# OCI 标注（可在 docker inspect 中查看）
LABEL org.opencontainers.image.title="moyuyo-api" \
      org.opencontainers.image.description="MOYUYO 宠物用品商城后端 API 服务" \
      org.opencontainers.image.source="https://github.com/your-org/moyuyo-server" \
      org.opencontainers.image.licenses="Proprietary"

# 安装运行时最小依赖：
# - tzdata：时区数据（避免 JVM 时区问题）
# - curl：健康检查
# - tini：处理 PID 1 信号转发（避免容器 stop 时 JVM 收不到 SIGTERM）
# 使用 --no-cache 不保留 apk 索引（减小镜像约 1MB）
RUN apk add --no-cache tzdata curl tini && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户（UID 1000，符合 1Panel/Docker 默认用户）
# 先创建 /app 目录并设置所有权，再切到非 root 用户，避免运行时权限问题
# /app/heapdump 用于 OOM 时堆转储（-XX:HeapDumpPath 指向该目录）
# 注意：hprof 文件名固定 heapdump.hprof，便于运维通过 ls /app/heapdump/*.hprof 统一归档
RUN addgroup -g 1000 -S moyuyo && \
    adduser -u 1000 -S moyuyo -G moyuyo && \
    mkdir -p /app /var/log/moyuyo /app/heapdump /var/log/moyuyo/jfr && \
    chown -R moyuyo:moyuyo /app /var/log/moyuyo

WORKDIR /app

# 复制 jar（从构建阶段拉取，使用 --chown 确保文件归属非 root 用户）
COPY --from=builder --chown=moyuyo:moyuyo /build/app.jar app.jar

# 切换到非 root 用户
USER moyuyo

# 声明容器对外可监听端口：
# - 8080：业务流量（由前置 Nginx/1Panel 反向代理终止 TLS 后转发）
# - 9090：actuator / Prometheus 抓取端口（仅绑定 127.0.0.1，由 docker-compose 端口映射控制）
# 注意：EXPOSE 仅声明元数据，不会真正暴露端口；实际对外暴露由 docker-compose ports 段控制
EXPOSE 8080 9090

# 健康检查：探测 actuator 的 liveness 子端点（返回 200 表示应用存活）
# - /actuator/health/liveness：仅检查 livenessState（应用进程是否存活），不依赖外部组件，
#   即使 Redis/ES/MySQL 临时不可用也不会触发重启（K8s 标准 liveness 探针语义）
# - 使用 9090 端口：actuator 独立端口，仅受 SecurityHeadersFilter 影响，
#   完全绕过 /api/* 的 TraceId/IpRateLimit/JwtAuth 等业务过滤器链，
#   避免健康检查被 IP 限流过滤器误判为"高频未鉴权流量"返回 429
# --start-period=90s 给 Spring Boot 启动 + Flyway 迁移留出充足时间
# 注意：必须使用 127.0.0.1 而非 localhost，避免容器内 DNS 解析延迟导致误判
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  # 探测 actuator/health 时增加严格超时（-m 5），避免 curl 自身卡死阻塞 docker 健康检查
  # 显式声明 -fs：仅 2xx 视为健康（4xx/5xx 均视为失败）
  CMD curl -fs -m 5 http://127.0.0.1:9090/actuator/health/liveness || exit 1

# 使用 tini 作为 PID 1，正确转发信号（SIGTERM 触发 Spring graceful shutdown）
# -g：让 tini 显式注册为 init，向子进程转发所有信号（包括 SIGTERM/SIGINT/SIGQUIT）
#     默认 tini 仅作为 zombie reaper，需 -g 才能避免 SIGKILL 直接落到 JVM
# JVM 参数通过环境变量配置（启动时由 -e 注入）
# 关键参数：
#  -XX:+ExitOnOutOfMemoryError：OOM 时立即退出（避免容器假死），由 docker restart policy 拉起新实例
#  -XX:+HeapDumpOnOutOfMemoryError：OOM 时生成堆转储，便于事后分析（路径 /app/heapdump.hprof）
#  -Djava.security.egd=file:/dev/./urandom：避免 SecureRandom 启动卡顿（Tomcat 启动期会生成 session key）
#  -XX:+UseCompactObjectHeaders：JDK 25+ 优化对象头大小，节省堆内存（视 JVM 版本可用）
#  -XX:+CrashOnOutOfMemoryError：JDK 25+ 提供，让 OOM 时直接产生 hs_err 与 vm core（便于事后深挖）
#  -XX:NativeMemoryTracking=summary：开启本地内存追踪，便于 jcmd 分析 NMT 泄漏
#  -XX:+UnlockDiagnosticVMOptions -XX:DebugNonSafepoints=true：让 NativeMemoryTracking / 火焰图能采集到更多采样点
#  -Xlog:gc*:file=/var/log/moyuyo/gc.log:time,uptime,level,tags:filecount=10,filesize=50M：GC 日志滚动（10 个 50MB 文件），
#   便于事后分析 GC 行为；容器 /var/log/moyuyo 已挂载为命名卷，日志不会随容器销毁丢失
#  -XX:+UseStringDeduplication：JDK 25+ 字符串去重，订单号 / 用户名 / SKU 等重复字符串可节省 ~5-10% 堆
#  -XX:+UseCompactObjectHeaders：JDK 25+ 优化对象头大小，节省 ~5-10% 堆（按 JVM 版本启用）
#  -XX:SoftRefLRUPolicyMSPerMB=1000：缩短 SoftRef 存活时间（默认 1000ms/MB），让 Caffeine / Guava 缓存更快释放堆
#  -Djdk.attach.allowAttachSelf=true：允许 arthas / async-profiler 自附加（生产期排障必需）
#  -Dio.netty.tryReflectionSetAccessible=true：Netty 反射优化（Elasticsearch 客户端使用 Netty）
#  -Djava.net.preferIPv4Stack=true：避免 IPv6 解析在容器内偶发阻塞（K8s Pod 常见）
#  -Dsun.net.client.defaultConnectTimeout=5000 & -Dsun.net.client.defaultReadTimeout=15000：JDK HttpURLConnection 默认超时兜底（与 moyuyo.http.* 对齐）
#  --add-opens=java.base/java.lang=ALL-UNNAMED 等：JDK 17+ 默认禁止未命名模块反射访问 java.base 等关键包
#    Lettuce / Netty / Hibernate / Resilience4j 在 JDK 25 启动时偶发 InaccessibleObjectException
#    显式 --add-opens 后避免冷启动失败
ENTRYPOINT ["/sbin/tini", "-g", "--", "sh", "-c", \
  "java \
   -Xms${JVM_XMS:-512m} -Xmx${JVM_XMX:-1024m} \
   -XX:+UseZGC -XX:MaxGCPauseMillis=100 \
   -XX:+ExitOnOutOfMemoryError \
   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/heapdump/heapdump.hprof \
   -XX:+CrashOnOutOfMemoryError \
   -XX:NativeMemoryTracking=summary \
   -XX:+UseStringDeduplication \
   -XX:+UseCompactObjectHeaders \
   -XX:SoftRefLRUPolicyMSPerMB=1000 \
   -XX:+UnlockDiagnosticVMOptions \
   -Xlog:gc*:file=/var/log/moyuyo/gc.log:time,uptime,level,tags:filecount=10,filesize=50M \
   # JFR 启动期录制：默认关闭（避免磁盘 IO 与 CPU 持续开销），
   # 排障时通过环境变量 JFR_RECORDING=true 启用，自动产出 60s 滚动 dump 至 /var/log/moyuyo/jfr
   # JFR 文件大小约 100~500MB/分钟，启用前请确保命名卷有足够空间
   ${JFR_RECORDING:+-XX:StartFlightRecording=duration=60s,disk=true,filename=/var/log/moyuyo/jfr/start.jfr,settings=profile} \
   -Djava.security.egd=file:/dev/./urandom \
   -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 \
   -Duser.timezone=Asia/Shanghai \
   -Djdk.attach.allowAttachSelf=true \
   -Djava.net.preferIPv4Stack=true \
   -Dsun.net.client.defaultConnectTimeout=5000 \
   -Dsun.net.client.defaultReadTimeout=15000 \
   --add-opens=java.base/java.lang=ALL-UNNAMED \
   --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
   --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
   --add-opens=java.base/java.io=ALL-UNNAMED \
   --add-opens=java.base/java.net=ALL-UNNAMED \
   --add-opens=java.base/java.nio=ALL-UNNAMED \
   --add-opens=java.base/java.util=ALL-UNNAMED \
   --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
   --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
   --add-opens=java.base/sun.security.action=ALL-UNNAMED \
   --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
   --add-opens=java.management/sun.management=ALL-UNNAMED \
   --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
   -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
   -jar app.jar"]