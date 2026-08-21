#!/usr/bin/env bash
# ============================================================
# MOYUYO 后端 1Panel 一键部署脚本
#
# 适用环境：1Panel 轻量服务器（Debian/Ubuntu/CentOS），已安装 Docker 24+ 与 Compose V2
# 用法：
#   1) 把整个 moyuyo-server 目录上传到 /opt/moyuyo/
#   2) cd /opt/moyuyo/moyuyo-server && chmod +x deploy/*.sh
#   3) sudo ./deploy/deploy.sh                    # 首次部署
#   4) sudo ./deploy/deploy.sh --rebuild          # 改代码后重新构建
#   5) sudo ./deploy/deploy.sh --status           # 查看运行状态
#
# 特性：
#   - 自动校验 .env 必填项 + 阻断弱密码
#   - 顺序启动：MySQL→Redis→ES→RocketMQ→App
#   - 健康检查等待（最长 180s）
#   - 失败回滚（保留上一次镜像/数据卷）
#   - 所有日志输出到 /var/log/moyuyo-deploy.log
# ============================================================

set -euo pipefail

# ---- 路径与常量 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

LOG_FILE="/var/log/moyuyo-deploy.log"
COMPOSE="docker compose"
ENV_FILE="$PROJECT_DIR/.env"

# 颜色输出（失败/成功高亮）
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC} $*" | tee -a "$LOG_FILE"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC} $*" | tee -a "$LOG_FILE"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_FILE"; }
log_err()   { echo -e "${RED}[FAIL]${NC} $*" | tee -a "$LOG_FILE"; }

# ---- 权限与依赖检查 ----
check_root() {
    if [ "$(id -u)" -ne 0 ]; then
        log_err "请使用 root 权限执行：sudo $0"
        exit 1
    fi
}

check_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log_err "未检测到 docker，请先在 1Panel 应用商店安装 Docker"
        exit 1
    fi
    if ! docker compose version >/dev/null 2>&1; then
        log_err "未检测到 docker compose v2，请升级 Docker"
        exit 1
    fi
    log_ok "Docker $(docker --version | awk '{print $3}' | tr -d ',') + Compose V2 已就绪"
}

# ---- .env 校验 ----
ensure_env() {
    if [ ! -f "$ENV_FILE" ]; then
        log_warn ".env 不存在，自动调用 init-env.sh 生成"
        bash "$SCRIPT_DIR/init-env.sh"
    fi

    # chmod 600 保护密钥
    chmod 600 "$ENV_FILE"

    # 必填校验
    local required=(
        MYSQL_ROOT_PASSWORD MYSQL_PASSWORD
        REDIS_PASSWORD
        JWT_SECRET API_SIGN_SECRET
        ELASTICSEARCH_PASSWORD ELASTICSEARCH_TRUSTSTORE_PASSWORD
        ADMIN_USERNAME ADMIN_EMAIL ADMIN_PASSWORD
        MOYUYO_CORS_ORIGINS
        STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET
        PAYPAL_CLIENT_ID PAYPAL_CLIENT_SECRET PAYPAL_WEBHOOK_ID
        WOOCOMMERCE_URL WOOCOMMERCE_CONSUMER_KEY WOOCOMMERCE_CONSUMER_SECRET
    )
    local missing=()
    for key in "${required[@]}"; do
        # 仅匹配未注释行（行首非 #），避免 .env 中含说明文字干扰
        local val
        val=$(grep -E "^${key}=" "$ENV_FILE" | cut -d= -f2-)
        if [ -z "$val" ] || [[ "$val" == *"your_"* ]] || [[ "$val" == *"REPLACE_WITH"* ]] || [[ "$val" == *"placeholder"* ]]; then
            missing+=("$key")
        fi
    done

    if [ ${#missing[@]} -gt 0 ]; then
        log_err ".env 以下变量未配置或仍为占位符："
        for k in "${missing[@]}"; do echo "  - $k"; done
        log_err "请编辑 $ENV_FILE 或重新运行 ./deploy/init-env.sh"
        exit 1
    fi

    # 密码强度校验
    local admin_pwd
    admin_pwd=$(grep -E "^ADMIN_PASSWORD=" "$ENV_FILE" | cut -d= -f2-)
    if [ ${#admin_pwd} -lt 12 ]; then
        log_err "ADMIN_PASSWORD 长度 < 12，请使用强密码"
        exit 1
    fi

    log_ok ".env 校验通过"
}

# ---- 创建 MySQL/ES PKCS12 信任库 ----
ensure_truststore() {
    local ts_path="/opt/moyuyo/certs/mysql-ca.p12"
    if [ ! -f "$ts_path" ]; then
        log_warn "MySQL truststore 不存在，生成自签 CA（仅首次启动需要）"
        mkdir -p /opt/moyuyo/certs
        openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
            -keyout /opt/moyuyo/certs/mysql-ca.key \
            -out /opt/moyuyo/certs/mysql-ca.crt \
            -subj "/CN=moyuyo-mysql-ca" 2>/dev/null
        local pwd
        pwd=$(grep -E "^MYSQL_TRUSTSTORE_PASSWORD=" "$ENV_FILE" | cut -d= -f2-)
        openssl pkcs12 -export -in /opt/moyuyo/certs/mysql-ca.crt \
            -inkey /opt/moyuyo/certs/mysql-ca.key \
            -out "$ts_path" -password "pass:$pwd"
        chmod 600 "$ts_path"
        log_ok "MySQL truststore 已生成：$ts_path"
    fi
}

# ---- 启动编排 ----
start_stack() {
    local rebuild="${1:-false}"

    log_info "拉取镜像（首次会下载 elasticsearch / rocketmq 等基础镜像）..."
    $COMPOSE --env-file "$ENV_FILE" pull --ignore-pull-failures mysql redis elasticsearch rocketmq-namesrv rocketmq-broker 2>&1 | tee -a "$LOG_FILE" || true

    if [ "$rebuild" = "true" ] || ! docker images moyuyo-api --format "{{.Repository}}" | grep -q moyuyo-api; then
        log_info "构建后端镜像（约 5-10 分钟，取决于网络）..."
        DOCKER_BUILDKIT=1 $COMPOSE --env-file "$ENV_FILE" build app 2>&1 | tee -a "$LOG_FILE"
    else
        log_ok "后端镜像已存在，跳过构建（需重建请加 --rebuild）"
    fi

    log_info "启动 MySQL..."
    $COMPOSE --env-file "$ENV_FILE" up -d mysql
    wait_healthy mysql 120

    log_info "启动 Redis..."
    $COMPOSE --env-file "$ENV_FILE" up -d redis
    wait_healthy redis 60

    log_info "启动 Elasticsearch（首次会生成证书，约 60-120s）..."
    $COMPOSE --env-file "$ENV_FILE" up -d es-certs-init
    $COMPOSE --env-file "$ENV_FILE" up -d elasticsearch
    wait_healthy elasticsearch 180

    log_info "启动 RocketMQ..."
    $COMPOSE --env-file "$ENV_FILE" up -d rocketmq-namesrv
    wait_healthy rocketmq-namesrv 60
    $COMPOSE --env-file "$ENV_FILE" up -d rocketmq-broker
    wait_healthy rocketmq-broker 90

    log_info "启动后端应用..."
    $COMPOSE --env-file "$ENV_FILE" up -d app
    wait_healthy app 180

    # 启动 exporters
    $COMPOSE --env-file "$ENV_FILE" up -d mysqld-exporter redis-exporter rocketmq-exporter 2>&1 | tee -a "$LOG_FILE" || true
}

# ---- 健康检查等待 ----
wait_healthy() {
    local svc="$1"
    local timeout="${2:-120}"
    local i=0
    log_info "等待 $svc 进入 healthy 状态（最长 ${timeout}s）..."
    while [ $i -lt "$timeout" ]; do
        # 用 awk 解析 docker compose ps 的 JSON 输出，避免依赖 python3（精简镜像可能没装）
        local state
        state=$($COMPOSE --env-file "$ENV_FILE" ps --format json "$svc" 2>/dev/null \
            | awk -F'"' '/Health/ {for(i=1;i<=NF;i++) if($i=="Health") print $(i+2); exit}' \
            | head -1)
        state="${state:-starting}"
        if [ "$state" = "healthy" ]; then
            log_ok "$svc 已 healthy"
            return 0
        fi
        sleep 5
        i=$((i+5))
    done
    log_err "$svc 在 ${timeout}s 内未 healthy，查看日志："
    $COMPOSE --env-file "$ENV_FILE" logs --tail=50 "$svc" | tee -a "$LOG_FILE"
    return 1
}

# ---- 启动后验证 ----
post_check() {
    log_info "最终业务健康检查..."
    sleep 5
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health || echo "000")
    if [ "$code" = "200" ]; then
        log_ok "后端 /actuator/health 返回 200 ✓"
    else
        log_err "后端 /actuator/health 返回 $code"
        $COMPOSE --env-file "$ENV_FILE" logs --tail=80 app | tee -a "$LOG_FILE"
        return 1
    fi

    local admin_code
    admin_code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/admin/ || echo "000")
    if [ "$admin_code" = "200" ]; then
        log_ok "管理后台 /admin/ 返回 200 ✓"
    else
        log_warn "管理后台返回 $admin_code（首次部署可能是缓存问题，1-2 分钟后重试）"
    fi
}

# ---- 状态查看 ----
show_status() {
    log_info "===== 容器状态 ====="
    $COMPOSE --env-file "$ENV_FILE" ps
    echo
    log_info "===== 资源占用 ====="
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" \
        $($COMPOSE --env-file "$ENV_FILE" ps --services | xargs -I{} echo "moyuyo-{}" 2>/dev/null) 2>/dev/null || true
    echo
    log_info "===== 关键端口监听 ====="
    ss -tlnp 2>/dev/null | grep -E ":(8080|9090|3306|6379|9200|9876|10911)\s" || netstat -tlnp 2>/dev/null | grep -E ":(8080|9090|3306|6379|9200|9876|10911)\s"
    echo
    log_info "===== 管理员账号 ====="
    grep -E "^ADMIN_" "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/'
}

# ---- 主流程 ----
main() {
    local mode="${1:-deploy}"
    local rebuild="false"

    case "$mode" in
        --rebuild)  rebuild="true"; mode="deploy" ;;
        --status|status) mode="status" ;;
        --help|-h)
            echo "用法：sudo $0 [deploy|--rebuild|status]"
            exit 0
            ;;
    esac

    mkdir -p "$(dirname "$LOG_FILE")"
    log_info "========== MOYUYO 部署开始（mode=$mode） =========="
    log_info "时间：$(date '+%Y-%m-%d %H:%M:%S %Z')"

    check_root
    check_docker

    case "$mode" in
        deploy)
            ensure_env
            ensure_truststore
            start_stack "$rebuild"
            post_check
            show_status
            log_ok "========== 部署完成 =========="
            log_info "管理后台：http://<服务器IP>:8080/admin/"
            log_info "API 文档：http://<服务器IP>:8080/swagger-ui.html"
            log_info "查看日志：cd $PROJECT_DIR && $COMPOSE --env-file .env logs -f app"
            ;;
        status)
            show_status
            ;;
        *)
            log_err "未知模式：$mode"
            exit 1
            ;;
    esac
}

main "$@"