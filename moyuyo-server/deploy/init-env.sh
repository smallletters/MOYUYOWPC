#!/usr/bin/env bash
# ============================================================
# MOYUYO .env 强随机密钥生成脚本
#
# 自动用 openssl 生成符合 ProdConfigValidator 校验要求的密钥
# 用法：sudo ./deploy/init-env.sh [--force]
#   --force  覆盖现有 .env（默认会保留已有值并仅补缺失项）
# ============================================================

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env"
EXAMPLE_FILE="$PROJECT_DIR/.env.example"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# ---- 强随机生成函数 ----
gen_password()  { openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c "${1:-24}"; }
gen_jwt()       { openssl rand -base64 48; }
gen_sign()      { openssl rand -hex 32; }
gen_truststore(){ openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24; }
gen_admin_pwd() { openssl rand -base64 16 | tr -dc 'A-Za-z0-9!@#%^&*' | head -c 16; }
gen_username()  { echo "moyuyo_admin_$(openssl rand -hex 3)"; }

# ---- 工具函数 ----
set_or_replace() {
    local key="$1"
    local value="$2"
    if grep -qE "^${key}=" "$ENV_FILE" 2>/dev/null; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
    else
        echo "${key}=${value}" >> "$ENV_FILE"
    fi
}

get_value() {
    grep -E "^${1}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true
}

is_placeholder() {
    local val="$1"
    [ -z "$val" ] || [[ "$val" == *"your_"* ]] || [[ "$val" == *"REPLACE_WITH"* ]] || [[ "$val" == *"placeholder"* ]]
}

# 与 .env.example 模板里的默认值完全一致时也认为是占位符
is_default_placeholder() {
    local val="$1"
    local key="$2"
    case "$key" in
        ADMIN_USERNAME)  [ "$val" = "moyuyo_admin" ] && return 0 ;;
        ADMIN_EMAIL)     [ "$val" = "admin@moyuyo.com" ] && return 0 ;;
        WOOCOMMERCE_URL) [[ "$val" == *"your-woocommerce"* ]] && return 0 ;;
        MYSQL_USER)      [ "$val" = "moyuyo" ] && return 0 ;;
        MOYUYO_CORS_ORIGINS) [[ "$val" == *"example.com"* ]] && return 0 ;;
    esac
    return 1
}

# ---- 主流程 ----
main() {
    local force="${1:-}"

    if [ ! -f "$EXAMPLE_FILE" ]; then
        echo -e "${RED}未找到 .env.example，请确认在 moyuyo-server 根目录执行${NC}"
        exit 1
    fi

    if [ -f "$ENV_FILE" ] && [ "$force" != "--force" ]; then
        echo -e "${YELLOW}.env 已存在，仅补全缺失项。如需重新生成全部密钥请加 --force${NC}"
    else
        cp "$EXAMPLE_FILE" "$ENV_FILE"
        chmod 600 "$ENV_FILE"
        echo -e "${GREEN}已基于 .env.example 创建 .env${NC}"
    fi

    echo -e "${GREEN}开始生成强随机密钥...${NC}"

    # ---- 数据库 ----
    if is_placeholder "$(get_value MYSQL_ROOT_PASSWORD)"; then
        set_or_replace MYSQL_ROOT_PASSWORD "$(gen_password 24)"
        echo "  MYSQL_ROOT_PASSWORD          $(get_value MYSQL_ROOT_PASSWORD | head -c 8)..."
    fi
    if is_placeholder "$(get_value MYSQL_PASSWORD)"; then
        set_or_replace MYSQL_PASSWORD "$(gen_password 24)"
        echo "  MYSQL_PASSWORD                $(get_value MYSQL_PASSWORD | head -c 8)..."
    fi
    if is_placeholder "$(get_value MYSQL_EXPORTER_PASSWORD)"; then
        set_or_replace MYSQL_EXPORTER_PASSWORD "$(gen_password 24)"
        echo "  MYSQL_EXPORTER_PASSWORD      $(get_value MYSQL_EXPORTER_PASSWORD | head -c 8)..."
    fi
    if is_placeholder "$(get_value MYSQL_TRUSTSTORE_PASSWORD)"; then
        set_or_replace MYSQL_TRUSTSTORE_PASSWORD "$(gen_truststore)"
        echo "  MYSQL_TRUSTSTORE_PASSWORD    $(get_value MYSQL_TRUSTSTORE_PASSWORD | head -c 8)..."
    fi

    # ---- Redis ----
    if is_placeholder "$(get_value REDIS_PASSWORD)"; then
        set_or_replace REDIS_PASSWORD "$(gen_password 24)"
        echo "  REDIS_PASSWORD                $(get_value REDIS_PASSWORD | head -c 8)..."
    fi
    if is_placeholder "$(get_value REDIS_EXPORTER_PASSWORD)"; then
        set_or_replace REDIS_EXPORTER_PASSWORD "$(gen_password 16)"
        echo "  REDIS_EXPORTER_PASSWORD      $(get_value REDIS_EXPORTER_PASSWORD | head -c 8)..."
    fi

    # ---- JWT / API 签名 ----
    if is_placeholder "$(get_value JWT_SECRET)"; then
        set_or_replace JWT_SECRET "$(gen_jwt)"
        echo "  JWT_SECRET                    $(get_value JWT_SECRET | head -c 8)..."
    fi
    if is_placeholder "$(get_value API_SIGN_SECRET)"; then
        set_or_replace API_SIGN_SECRET "$(gen_sign)"
        echo "  API_SIGN_SECRET               $(get_value API_SIGN_SECRET | head -c 8)..."
    fi

    # ---- ES ----
    if is_placeholder "$(get_value ELASTICSEARCH_PASSWORD)"; then
        set_or_replace ELASTICSEARCH_PASSWORD "$(gen_password 24)"
        echo "  ELASTICSEARCH_PASSWORD        $(get_value ELASTICSEARCH_PASSWORD | head -c 8)..."
    fi
    if is_placeholder "$(get_value ELASTICSEARCH_TRUSTSTORE_PASSWORD)"; then
        set_or_replace ELASTICSEARCH_TRUSTSTORE_PASSWORD "$(gen_truststore)"
        echo "  ELASTICSEARCH_TRUSTSTORE_PWD  $(get_value ELASTICSEARCH_TRUSTSTORE_PASSWORD | head -c 8)..."
    fi

    # ---- 管理员账号 ----
    local admin_user_val
    admin_user_val=$(get_value ADMIN_USERNAME)
    if is_placeholder "$admin_user_val" || is_default_placeholder "$admin_user_val" ADMIN_USERNAME; then
        set_or_replace ADMIN_USERNAME "$(gen_username)"
        echo "  ADMIN_USERNAME                $(get_value ADMIN_USERNAME)"
    fi
    if is_placeholder "$(get_value ADMIN_PASSWORD)"; then
        set_or_replace ADMIN_PASSWORD "$(gen_admin_pwd)"
        echo "  ADMIN_PASSWORD                $(get_value ADMIN_PASSWORD | head -c 6)..."
    fi

    # ---- CORS：留待用户填域名，仅在空或仍是 example.com 时设占位 ----
    if is_placeholder "$(get_value MOYUYO_CORS_ORIGINS)" || is_default_placeholder "$(get_value MOYUYO_CORS_ORIGINS)" MOYUYO_CORS_ORIGINS; then
        set_or_replace MOYUYO_CORS_ORIGINS "https://your-domain.com,https://admin.your-domain.com"
        echo -e "  ${YELLOW}MOYUYO_CORS_ORIGINS          请替换为真实域名（占位已写入）${NC}"
    fi

    # ---- Stripe / PayPal / WooCommerce 占位符提示 ----
    # 这些是必须由用户在 dashboard 申请的第三方密钥，无法自动生成
    # 仅打印占位符残留提醒，不强制重生成
    local third_party=(
        "STRIPE_SECRET_KEY"
        "STRIPE_WEBHOOK_SECRET"
        "PAYPAL_CLIENT_ID"
        "PAYPAL_CLIENT_SECRET"
        "PAYPAL_WEBHOOK_ID"
        "WOOCOMMERCE_URL"
        "WOOCOMMERCE_CONSUMER_KEY"
        "WOOCOMMERCE_CONSUMER_SECRET"
    )
    local third_party_missing=()
    for key in "${third_party[@]}"; do
        local val
        val=$(get_value "$key")
        if is_placeholder "$val" || is_default_placeholder "$val" "$key"; then
            third_party_missing+=("$key")
        fi
    done
    if [ ${#third_party_missing[@]} -gt 0 ]; then
        echo -e "  ${YELLOW}第三方密钥待人工补全：${NC}"
        for k in "${third_party_missing[@]}"; do
            echo "    - $k"
        done
        echo -e "  ${YELLOW}↑ 这些密钥需登录 Stripe / PayPal / WooCommerce 后台获取，脚本无法自动生成${NC}"
    fi

    chmod 600 "$ENV_FILE"
    echo
    echo -e "${GREEN}====================================================${NC}"
    echo -e "${GREEN}密钥生成完成 → $ENV_FILE${NC}"
    echo -e "${GREEN}权限：$(stat -c '%a' "$ENV_FILE")${NC}"
    echo -e "${YELLOW}下一步：${NC}"
    echo "  1) 编辑 .env 补全 Stripe / PayPal / WooCommerce 第三方密钥"
    echo "  2) 编辑 .env 修改 MOYUYO_CORS_ORIGINS 为真实域名"
    echo "  3) 执行 ./deploy/deploy.sh 开始拉起服务"
    echo -e "${GREEN}====================================================${NC}"
}

main "$@"