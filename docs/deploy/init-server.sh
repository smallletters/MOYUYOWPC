#!/bin/bash

# ============================================================
# MOYUYO 1Panel 服务器初始化脚本
# 适用系统：Ubuntu 22.04 LTS
# 使用方法：bash init-server.sh
# ============================================================

set -e

echo "=========================================="
echo "  MOYUYO 服务器初始化脚本"
echo "=========================================="

# --------------------------
# 1. 更新系统
# --------------------------
echo ""
echo "[1/8] 更新系统..."
apt update -y
apt upgrade -y
apt autoremove -y

# --------------------------
# 2. 安装基础依赖
# --------------------------
echo ""
echo "[2/8] 安装基础依赖..."
apt install -y \
    curl \
    wget \
    git \
    vim \
    net-tools \
    htop \
    unzip \
    zip \
    certbot \
    python3-certbot-nginx

# --------------------------
# 3. 安装 Docker
# --------------------------
echo ""
echo "[3/8] 安装 Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    
    usermod -aG docker root
    echo "Docker 安装完成"
else
    echo "Docker 已安装"
fi

# --------------------------
# 4. 安装 Docker Compose
# --------------------------
echo ""
echo "[4/8] 安装 Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep 'tag_name' | cut -d'"' -f4)
    curl -L "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    echo "Docker Compose 安装完成"
else
    echo "Docker Compose 已安装"
fi

# --------------------------
# 5. 安装 1Panel
# --------------------------
echo ""
echo "[5/8] 安装 1Panel..."
if ! command -v 1panel &> /dev/null; then
    curl -sSL https://resource.fit2cloud.com/1panel/package/quick_start.sh | sh
    echo "1Panel 安装完成"
else
    echo "1Panel 已安装"
fi

# --------------------------
# 6. 配置防火墙
# --------------------------
echo ""
echo "[6/8] 配置防火墙..."
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/tcp
ufw allow 8080/tcp
ufw allow 3306/tcp
ufw allow 6379/tcp
ufw allow 9876/tcp
ufw allow 10911/tcp
ufw --force enable
echo "防火墙配置完成"

# --------------------------
# 7. 创建目录结构
# --------------------------
echo ""
echo "[7/8] 创建目录结构..."
mkdir -p /opt/moyuyo/{backend,logs,config,data,nginx,ssl}
mkdir -p /opt/moyuyo/data/{mysql,redis,rocketmq}
mkdir -p /opt/moyuyo/logs/{backend,mysql,redis,rocketmq}
chown -R www-data:www-data /opt/moyuyo
echo "目录结构创建完成"

# --------------------------
# 8. 设置时区
# --------------------------
echo ""
echo "[8/8] 设置时区..."
timedatectl set-timezone Asia/Shanghai
echo "时区设置完成"

# --------------------------
# 完成
# --------------------------
echo ""
echo "=========================================="
echo "  服务器初始化完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 访问 1Panel 面板：http://<服务器IP>:443"
echo "2. 设置 1Panel 管理员账号密码"
echo "3. 创建数据库和Redis实例"
echo "4. 上传后端部署文件"
echo ""
echo "服务器信息："
echo "主机名: $(hostname)"
echo "IP地址: $(curl -s ifconfig.me)"
echo "时区: $(timedatectl show --property=Timezone --value)"
echo "Docker版本: $(docker --version)"
echo ""
