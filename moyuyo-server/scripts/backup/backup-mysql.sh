#!/usr/bin/env bash
# ============================================================
# MySQL 自动备份脚本（Bash 版，1Panel Linux 推荐）
# 配合 1Panel 计划任务每日凌晨 3 点运行：
#   0 3 * * * /opt/moyuyo/moyuyo-server/scripts/backup/backup-mysql.sh
# ============================================================
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-moyuyo-mysql}"
DB_NAME="${DB_NAME:-moyuyo_prod}"
BACKUP_DIR="${BACKUP_DIR:-/opt/1panel/backup/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

mkdir -p "${BACKUP_DIR}"

TS=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/moyuyo_${DB_NAME}_${TS}.sql.gz"

echo "[$(date +%H:%M:%S)] 开始备份 MySQL: ${DB_NAME} -> ${BACKUP_FILE}"

# 容器内执行 mysqldump 并通过管道压缩到宿主机
docker exec "${CONTAINER_NAME}" sh -c \
  "mysqldump -u root -p\$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers --events ${DB_NAME} | gzip" \
  > "${BACKUP_FILE}"

# 校验备份文件
FILE_SIZE=$(stat -c %s "${BACKUP_FILE}" 2>/dev/null || stat -f %z "${BACKUP_FILE}")
if [ "${FILE_SIZE}" -lt 1024 ]; then
  echo "[FATAL] 备份文件异常（仅 ${FILE_SIZE} 字节），疑似空备份" >&2
  rm -f "${BACKUP_FILE}"
  exit 1
fi

echo "[$(date +%H:%M:%S)] 备份成功: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

# 清理过期备份
find "${BACKUP_DIR}" -maxdepth 1 -name "moyuyo_${DB_NAME}_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete

echo "[$(date +%H:%M:%S)] 清理 ${RETENTION_DAYS} 天前的旧备份完成"