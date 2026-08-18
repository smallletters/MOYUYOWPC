#!/usr/bin/env bash
# ============================================================
# MOYUYO MySQL CA 证书初始化脚本
#
# 用途：从 MySQL 服务端拉取 CA 证书，导出为 PKCS12 格式，
#      由 docker-compose 挂载到应用容器作为 truststore，
#      启用 MySQL TLS 服务端证书校验（verifyServerCertificate=true）。
#
# 适用场景：1Panel / 任何自建 MySQL 8.0+ 部署
# 运行位置：MySQL 服务端所在主机（或能访问 MySQL 3306 端口的运维主机）
# 运行频率：仅需运行一次（证书未过期前无需重做）
#
# 用法：
#   MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 \
#   MYSQL_TRUSTSTORE_DIR=/opt/moyuyo/certs \
#   bash scripts/init-mysql-truststore.sh
# ============================================================

set -euo pipefail

# ---- 参数校验 ----
: "${MYSQL_HOST:?MYSQL_HOST must be set (e.g. 127.0.0.1)}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_TRUSTSTORE_DIR:=/opt/moyuyo/certs}"

# 默认密码生成（生产环境务必显式传入 MOYUYO_MYSQL_TRUSTSTORE_PASSWORD）
: "${MYSQL_TRUSTSTORE_PASSWORD:=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)}"

OUTPUT_PATH="${MYSQL_TRUSTSTORE_DIR}/mysql-ca.p12"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "${TMPDIR}"' EXIT

echo "==> 步骤 1/4：从 MySQL 服务端拉取 CA 证书"
mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user=root \
      --execute="SHOW GLOBAL VARIABLES LIKE 'have_ssl';" >/dev/null

# 通过 mysql 客户端触发 TLS 连接，让 openssl 能从服务端拉取证书链
# 兼容 MySQL 5.7+ / 8.0+ / MariaDB 10.x
SSL_OUTPUT=$(openssl s_client -connect "${MYSQL_HOST}:${MYSQL_PORT}" -starttls mysql \
             -servername "${MYSQL_HOST}" </dev/null 2>/dev/null || true)

if [[ -z "${SSL_OUTPUT}" ]]; then
    echo "[FATAL] 无法从 ${MYSQL_HOST}:${MYSQL_PORT} 拉取证书，请检查："
    echo "  1. MySQL 是否启用 SSL（have_ssl=YES）"
    echo "  2. 防火墙是否允许本机访问 ${MYSQL_PORT}"
    echo "  3. openssl 版本（要求 OpenSSL 1.1+）"
    exit 1
fi

echo "${SSL_OUTPUT}" | sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' \
    > "${TMPDIR}/mysql-ca.pem"

if [[ ! -s "${TMPDIR}/mysql-ca.pem" ]]; then
    echo "[FATAL] 未能从服务端响应中提取到证书"
    exit 1
fi
echo "    证书已保存到 ${TMPDIR}/mysql-ca.pem"

echo "==> 步骤 2/4：导出为 PKCS12 格式"
mkdir -p "${MYSQL_TRUSTSTORE_DIR}"
openssl pkcs12 -export -in "${TMPDIR}/mysql-ca.pem" \
               -out "${TMPDIR}/mysql-ca.p12" \
               -name "moyuyo-mysql-ca" \
               -password "pass:${MYSQL_TRUSTSTORE_PASSWORD}" \
               -noiter -nomaciter

echo "==> 步骤 3/4：拷贝到目标目录"
mv "${TMPDIR}/mysql-ca.p12" "${OUTPUT_PATH}"
chmod 644 "${OUTPUT_PATH}"

echo "==> 步骤 4/4：写入 .env 提示"
ENV_FILE=".env"
if [[ -f "${ENV_FILE}" ]]; then
    # 仅当 .env 存在时追加提示信息，不覆盖已有配置
    if ! grep -q "^MYSQL_TRUSTSTORE_PASSWORD=" "${ENV_FILE}"; then
        echo "" >> "${ENV_FILE}"
        echo "# 由 init-mysql-truststore.sh 生成于 $(date -u +%FT%TZ)" >> "${ENV_FILE}"
        echo "MYSQL_TRUSTSTORE_PASSWORD=${MYSQL_TRUSTSTORE_PASSWORD}" >> "${ENV_FILE}"
        echo "MYSQL_TRUSTSTORE_PATH=${MYSQL_TRUSTSTORE_DIR}/mysql-ca.p12" >> "${ENV_FILE}"
        echo "    已写入 ${ENV_FILE}"
    else
        echo "    ${ENV_FILE} 中已存在 MYSQL_TRUSTSTORE_PASSWORD，请手动同步（避免覆盖）"
        echo "    当前生成的密码：${MYSQL_TRUSTSTORE_PASSWORD}"
    fi
else
    echo "    .env 不存在，请手动创建并填入："
    echo "    MYSQL_TRUSTSTORE_PASSWORD=${MYSQL_TRUSTSTORE_PASSWORD}"
    echo "    MYSQL_TRUSTSTORE_PATH=${MYSQL_TRUSTSTORE_DIR}/mysql-ca.p12"
fi

echo ""
echo "============================================="
echo " MySQL Truststore 初始化完成"
echo "============================================="
echo " PKCS12 文件：${OUTPUT_PATH}"
echo " 密码：${MYSQL_TRUSTSTORE_PASSWORD}"
echo ""
echo " 下一步："
echo "   1. 在 .env 中确认 MYSQL_TRUSTSTORE_PATH / MYSQL_TRUSTSTORE_PASSWORD 已设置"
echo "   2. docker compose up -d（应用容器会自动挂载证书）"
echo "   3. 检查应用启动日志：grep 'truststore' /var/log/moyuyo/moyuyo.log"