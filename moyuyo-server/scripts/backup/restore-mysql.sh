#!/usr/bin/env bash
# ============================================================
# MySQL 还原脚本（Bash 版，1Panel Linux 推荐）
# 用法：
#   ./restore-mysql.sh <backup-file.sql.gz>
# 警告：覆盖目标库，务必先备份当前数据！
# ============================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "用法: $0 <backup-file.sql.gz> [--yes]"
  echo "  --yes 跳过二次确认"
  exit 1
fi

BACKUP_FILE="$1"
AUTO_YES="false"
if [ "${2:-}" = "--yes" ]; then
  AUTO_YES="true"
fi

CONTAINER_NAME="${CONTAINER_NAME:-moyuyo-mysql}"
DB_NAME="${DB_NAME:-moyuyo_prod}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[FATAL] 备份文件不存在: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "即将还原数据库："
echo "  容器  : ${CONTAINER_NAME}"
echo "  数据库: ${DB_NAME}"
echo "  备份  : ${BACKUP_FILE}"
echo
echo "警告：此操作将覆盖 ${DB_NAME} 中现有数据！"
if [ "${AUTO_YES}" != "true" ]; then
  read -r -p "确认继续？输入 YES 继续： " CONFIRM
  if [ "${CONFIRM}" != "YES" ]; then
    echo "已取消"
    exit 0
  fi
fi

# 解压后通过容器内 mysql 还原
gunzip -c "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" \
  sh -c "mysql -u root -p\$MYSQL_ROOT_PASSWORD ${DB_NAME}"

echo "[$(date +%H:%M:%S)] 还原完成: ${BACKUP_FILE}"