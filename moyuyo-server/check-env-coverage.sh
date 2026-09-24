#!/usr/bin/env bash
# CI 检查：application-prod.yml 中通过 ${ENV} 占位符引用的环境变量，
# 是否都在 docker-compose.yml 的 app.environment 里被注入（包括硬编码值）。
# 防止"yml 改了但 compose 没透传"导致生产部署拿到 yml 默认值（静默配置漂移）。
# 与 check-env-coverage.ps1 是同一逻辑的两个平台实现（CI 用 bash，本地用 PowerShell）。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_YML="$SCRIPT_DIR/moyuyo-api/src/main/resources/application-prod.yml"
COMPOSE_YML="$SCRIPT_DIR/docker-compose.yml"

if [ ! -f "$PROD_YML" ]; then
    echo "[check-env-coverage] 未找到 application-prod.yml: $PROD_YML" >&2
    exit 1
fi
if [ ! -f "$COMPOSE_YML" ]; then
    echo "[check-env-coverage] 未找到 docker-compose.yml: $COMPOSE_YML" >&2
    exit 1
fi

# 抓 yml 中所有 ${XXX} 的 key（仅大写 + 下划线开头）
yml_keys_file=$(mktemp)
grep -oE '\$\{[A-Z_][A-Z0-9_]*' "$PROD_YML" | sed 's/\${//' | sort -u > "$yml_keys_file"

# 抓 compose 中所有 KEY: 形式的 key（覆盖 ${XXX} 透传 与 硬编码值 两种情况）：
#   - 大写 + 下划线开头的行首 key
#   - 缩进若干空格后接 KEY: 形式（匹配 app.environment 段的多层缩进）
compose_keys_file=$(mktemp)
grep -oE '^[ ]*[A-Z_][A-Z0-9_]*:' "$COMPOSE_YML" | tr -d ': ' | sort -u > "$compose_keys_file"

# diff：yml_keys - compose_keys 即为"未注入的 env"
# comm 在两个文件任一为空时返回非零，加 `|| true` 兜底避免 set -o pipefail 误触发
missing_file=$(mktemp)
comm -23 "$yml_keys_file" "$compose_keys_file" > "$missing_file" || true

# 过滤：yml 中 ${...} 但 compose 中以"硬编码完全相同字符串"出现的也算注入（如 SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}）
# 这里仅检查 key 名是否出现，因为 compose 是 .env 驱动，运维可改 .env 改值；硬编码 key 也在 compose 里覆盖了

missing_count=$(wc -l < "$missing_file" | tr -d ' ')

if [ "$missing_count" -gt 0 ]; then
    echo "[check-env-coverage] FAIL: prod yml 用到但 compose 未注入的环境变量（$missing_count 个）：" >&2
    while IFS= read -r key; do
        [ -z "$key" ] && continue
        echo "  - $key" >&2
    done < "$missing_file"
    echo "[check-env-coverage] 修复方式：在 docker-compose.yml app.environment 中追加 'KEY: \${KEY:-default}' 或硬编码'KEY: value'" >&2
    rm -f "$yml_keys_file" "$compose_keys_file" "$missing_file"
    exit 1
fi

yml_count=$(wc -l < "$yml_keys_file" | tr -d ' ')
echo "[check-env-coverage] OK: application-prod.yml 中 $yml_count 个 env 全部已在 compose 注入"

rm -f "$yml_keys_file" "$compose_keys_file" "$missing_file"