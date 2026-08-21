#!/usr/bin/env bash
# ============================================================
# MOYUYO 在线升级脚本
#
# 流程：
#   1) 备份当前镜像（save → /opt/moyuyo/backup/images/）
#   2) 拉取新代码（git pull 或 rsync 后由调用方传入）
#   3) 重新编译 + 构建新镜像（保留旧镜像以便回滚）
#   4) 滚动重启应用（蓝绿：启动新容器验证后再停旧容器）
#   5) 健康检查不通过则自动回滚到旧镜像
#
# 用法：
#   sudo ./deploy/update.sh                    # 默认升级到最新代码
#   sudo ./deploy/update.sh --tag v1.2.0       # 升级到指定 Git tag
#   sudo ./deploy/update.sh --rollback         # 回滚到上一个镜像
# ============================================================

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

ENV_FILE="$PROJECT_DIR/.env"
COMPOSE="docker compose"
IMAGE_BACKUP_DIR="/opt/moyuyo/backup/images"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[$(date '+%F %T')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%F %T')]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%F %T')]${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%F %T')]${NC} $*"; }

# ---- 参数解析 ----
target_tag=""
rollback=false
while [ $# -gt 0 ]; do
    case "$1" in
        --tag)      target_tag="$2"; shift 2 ;;
        --rollback) rollback=true; shift ;;
        *) err "未知参数：$1"; exit 1 ;;
    esac
done

[ "$(id -u)" -ne 0 ] && { err "请使用 root 执行"; exit 1; }

# ---- 备份当前镜像 ----
backup_image() {
    local img="$1"
    if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${img}$"; then
        warn "镜像 $img 不存在，跳过备份"
        return
    fi
    mkdir -p "$IMAGE_BACKUP_DIR"
    local file="$IMAGE_BACKUP_DIR/$(echo "$img" | tr '/:' '_').tar"
    log "备份镜像 → $file"
    docker save "$img" -o "$file"
    gzip "$file"
    ok "镜像备份完成：${file}.gz"
}

# ---- 健康检查（使用 9090 端口的 actuator liveness 探针，独立 + 仅 127.0.0.1）----
health_check() {
    local max="${1:-60}"  # 默认 60 次 × 2s = 120s，覆盖 Spring Boot 冷启动
    local i=0
    local health_url="http://127.0.0.1:9090/actuator/health/liveness"
    # 兜底：如果 9090 没起来（极少数 dev 环境未配独立端口），也试 8080
    while [ $i -lt $max ]; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$health_url" 2>/dev/null || echo "000")
        if [ "$code" != "200" ]; then
            code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://127.0.0.1:8080/actuator/health/liveness" 2>/dev/null || echo "000")
        fi
        if [ "$code" = "200" ]; then
            ok "健康检查通过 (第 ${i} 次 / 上限 ${max})"
            return 0
        fi
        sleep 2
        i=$((i+1))
    done
    err "健康检查失败（${max} 次重试均未 200）"
    return 1
}

# ---- 查找当前部署的镜像标签（兼容 latest / 1.0.0 / current） ----
detect_current_image() {
    # docker compose 会自动给构建的镜像打 <service>:latest 标签
    docker images --format "{{.Repository}}:{{.Tag}}" | grep -E "^moyuyo-api:(latest|1\.0\.0|current)$" | head -1 || echo ""
}

# ---- 回滚 ----
do_rollback() {
    warn "执行回滚..."
    local last_backup
    last_backup=$(ls -t "$IMAGE_BACKUP_DIR"/moyuyo-api*.tar.gz 2>/dev/null | head -1 || true)
    if [ -z "$last_backup" ]; then
        err "未找到备份镜像（$IMAGE_BACKUP_DIR），无法回滚"
        err "如需手动恢复：docker load -i <backup.tar.gz> && docker compose --env-file .env up -d app"
        exit 1
    fi
    log "恢复备份镜像：$last_backup"
    docker load -i "$last_backup"
    $COMPOSE --env-file "$ENV_FILE" up -d app
    if health_check 90; then
        ok "回滚成功"
    else
        err "回滚后仍不健康，请人工介入"
        exit 1
    fi
}

# ---- 主流程：升级 ----
do_update() {
    # 1. 备份当前镜像（用 docker compose 构建出来的实际标签）
    local current_img
    current_img=$(detect_current_image)
    if [ -n "$current_img" ]; then
        backup_image "$current_img"
    else
        warn "未检测到 moyuyo-api 镜像（首次部署？跳过备份）"
    fi

    # 2. 拉取代码（如果当前是 git 仓库）
    if [ -d "$PROJECT_DIR/.git" ] && [ -z "$target_tag" ]; then
        log "git pull 拉取最新代码..."
        git pull --ff-only || { err "git pull 失败，请检查冲突"; exit 1; }
    elif [ -n "$target_tag" ] && [ -d "$PROJECT_DIR/.git" ]; then
        log "切换到 tag: $target_tag"
        git fetch --tags
        git checkout "$target_tag"
    fi

    # 3. 重新编译
    log "编译新版本（约 60-120s）..."
    if ! mvn -DskipTests package -B -q 2>&1 | tee /tmp/moyuyo-build.log; then
        err "编译失败，查看 /tmp/moyuyo-build.log"
        exit 1
    fi

    # 4. 标记旧镜像为 previous（保留一份用于极端情况下的双层回滚）
    if [ -n "$current_img" ]; then
        log "标记旧镜像 ($current_img) 为 previous..."
        docker rmi moyuyo-api:previous 2>/dev/null || true
        docker tag "$current_img" moyuyo-api:previous
    fi

    # 5. 构建新镜像
    log "构建新镜像..."
    DOCKER_BUILDKIT=1 $COMPOSE --env-file "$ENV_FILE" build app

    # 6. 重启 app
    log "滚动重启应用..."
    $COMPOSE --env-file "$ENV_FILE" up -d app

    # 7. 健康检查（更长超时，因为冷启动 + Flyway 迁移可能耗 60-90s）
    if health_check 90; then
        ok "升级成功"
        # 清理 7 天前的旧镜像备份
        find "$IMAGE_BACKUP_DIR" -name "*.tar.gz" -mtime +7 -delete 2>/dev/null || true
    else
        err "健康检查失败，开始回滚"
        do_rollback
        exit 1
    fi
}

if [ "$rollback" = true ]; then
    do_rollback
else
    do_update
fi