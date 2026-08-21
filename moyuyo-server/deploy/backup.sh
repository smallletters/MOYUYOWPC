#!/usr/bin/env bash
# ============================================================
# MOYUYO 每日备份脚本
#
# 备份内容：
#   - MySQL 全量逻辑备份（mysqldump + gzip）
#   - 应用日志归档
#   - ES 索引快照（可选，需 ES 已配置 snapshot repo）
#
# 用法：
#   sudo ./deploy/backup.sh                  # 全量备份
#   sudo ./deploy/backup.sh --mysql-only    # 仅备份数据库
#   sudo ./deploy/backup.sh --retention 30  # 备份保留天数（默认 30）
#
# 配合 1Panel 计划任务：每天凌晨 3:00 执行
# ============================================================

set -Eeuo pipefail
trap 'err "备份脚本异常退出于第 ${LINENO} 行"' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

ENV_FILE="$PROJECT_DIR/.env"
# BACKUP_ROOT 允许环境变量覆盖（默认 /opt/moyuyo/backup，便于 dev / 测试覆盖到本地）
BACKUP_ROOT="${BACKUP_ROOT:-/opt/moyuyo/backup}"
MYSQL_BACKUP_DIR="$BACKUP_ROOT/mysql"
LOG_BACKUP_DIR="$BACKUP_ROOT/logs"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date '+%F %T')]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%F %T')]${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%F %T')]${NC} $*"; }

# ---- 参数解析 ----
mysql_only=false
while [ $# -gt 0 ]; do
    case "$1" in
        --mysql-only)  mysql_only=true; shift ;;
        --retention)   RETENTION_DAYS="$2"; shift 2 ;;
        *) err "未知参数：$1"; exit 1 ;;
    esac
done

# ---- 加载 .env ----
if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
else
    err ".env 不存在：$ENV_FILE"
    exit 1
fi

# ---- 创建目录 ----
mkdir -p "$MYSQL_BACKUP_DIR" "$LOG_BACKUP_DIR"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

# ---- MySQL 备份 ----
backup_mysql() {
    local file="$MYSQL_BACKUP_DIR/moyuyo_${MYSQL_DATABASE:-moyuyo_prod}_${TIMESTAMP}.sql.gz"
    log "开始备份 MySQL → $file"

    if ! docker ps --format '{{.Names}}' | grep -q '^moyuyo-mysql$'; then
        warn "MySQL 容器未运行，跳过"
        return 0
    fi

    # --single-transaction 保证一致性 + --routines + --triggers + --events
    # --master-data=2 记录 binlog 位点（便于 PITR）
    # 使用 PIPESTATUS 捕获管道中 mysqldump 的退出码（避免 set -o pipefail 误判）
    local dump_status
    docker exec moyuyo-mysql sh -c "exec mysqldump \
        -u root -p\"\${MYSQL_ROOT_PASSWORD}\" \
        --single-transaction --quick --routines --triggers --events \
        --master-data=2 --hex-blob \
        \"\${MYSQL_DATABASE}\"" 2>/dev/null | gzip -9 > "$file"
    dump_status=${PIPESTATUS[0]}

    if [ "$dump_status" -ne 0 ]; then
        err "mysqldump 失败，退出码：$dump_status"
        rm -f "$file"
        return 1
    fi
    if [ ! -s "$file" ]; then
        err "MySQL 备份失败，文件为空"
        rm -f "$file"
        return 1
    fi
    local size=$(du -h "$file" | cut -f1)
    log "MySQL 备份完成：$file ($size)"
}

# ---- 应用日志归档 ----
backup_logs() {
    if [ "$mysql_only" = true ]; then return 0; fi

    local file="$LOG_BACKUP_DIR/moyuyo-logs_${TIMESTAMP}.tar.gz"
    log "归档应用日志 → $file"

    if ! docker ps --format '{{.Names}}' | grep -q '^moyuyo-server$'; then
        warn "应用容器未运行，跳过"
        return 0
    fi

    local tar_status
    docker exec moyuyo-server sh -c "tar czf - /var/log/moyuyo" > "$file" 2>/dev/null
    tar_status=$?
    if [ "$tar_status" -ne 0 ]; then
        warn "日志归档失败，tar 退出码：$tar_status（非阻塞）"
        rm -f "$file"
        return 0
    fi
    if [ -s "$file" ]; then
        local size=$(du -h "$file" | cut -f1)
        log "日志归档完成：$file ($size)"
    fi
}

# ---- 清理过期备份 ----
cleanup_old() {
    log "清理 ${RETENTION_DAYS} 天前的备份..."
    find "$MYSQL_BACKUP_DIR" -name "*.sql.gz" -mtime +${RETENTION_DAYS} -delete -print 2>/dev/null | head -10
    find "$LOG_BACKUP_DIR" -name "*.tar.gz" -mtime +${RETENTION_DAYS} -delete -print 2>/dev/null | head -10

    local mysql_count=$(find "$MYSQL_BACKUP_DIR" -name "*.sql.gz" 2>/dev/null | wc -l)
    local log_count=$(find "$LOG_BACKUP_DIR" -name "*.tar.gz" 2>/dev/null | wc -l)
    log "当前保留：MySQL ${mysql_count} 份，日志 ${log_count} 份"
}

# ---- 主流程 ----
log "===== MOYUYO 备份开始（保留 ${RETENTION_DAYS} 天）====="
backup_mysql
backup_logs
cleanup_old
log "===== 备份完成 ====="
log "备份目录：$BACKUP_ROOT"