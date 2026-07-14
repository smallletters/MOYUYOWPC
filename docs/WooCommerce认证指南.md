# WooCommerce 认证指南

**版本**：V1.0 | **适用架构**：uni-app + Cloudflare Workers + WordPress/WooCommerce

---

## 一、认证架构概览

本系统涉及两套独立的认证机制：

| 层级 | 认证方式 | 密钥存放位置 | 用途 |
| :--- | :--- | :--- | :--- |
| **客户端 → 代理层** | 无认证（API 路由转发） | — | App 请求无需携带密钥 |
| **代理层 → WP** | Basic Auth（Consumer Key + Secret） | Cloudflare Workers 环境变量 | WooCommerce REST API 读写权限 |
| **客户端 → WP（JWT）** | Bearer Token（JWT） | JWT Authentication 插件 | 用户登录/注册/个人信息 |

> **安全原则**：Consumer Key + Consumer Secret **绝不出现在客户端代码中**，仅存在于 Workers 环境变量。

---

## 二、WooCommerce REST API 密钥配置

### 2.1 生成 API 密钥

1. 登录 WordPress 后台 → WooCommerce → 设置 → 高级 → REST API
2. 点击「创建密钥」
3. 填写说明（如 "MOYUYO App API"）
4. 选择用户（建议使用管理员账号）
5. 权限级别选择：**Read/Write**
6. 点击「生成密钥」

**生成后获得**：
```
Consumer Key    : ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Consumer Secret : cs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 2.2 部署到 Cloudflare Workers

```bash
# 通过 wrangler CLI 设置密钥（不写入代码）
npx wrangler secret put CONSUMER_KEY
# 输入: ck_xxxxxxxx

npx wrangler secret put CONSUMER_SECRET
# 输入: cs_xxxxxxxx

npx wrangler secret put WP_URL
# 输入: https://your-wp-site.com
```

### 2.3 Workers 中读取密钥

```js
// 通过环境变量读取，不写入代码
const CONSUMER_KEY = env.CONSUMER_KEY;
const CONSUMER_SECRET = env.CONSUMER_SECRET;
const WP_URL = env.WP_URL;
```

---

## 三、JWT 用户鉴权

### 3.1 安装配置

1. 安装插件：**JWT Authentication for WP REST API**
2. 在 `wp-config.php` 中添加：

```php
// JWT 密钥（使用随机生成的长字符串）
define('JWT_AUTH_SECRET_KEY', 'your-long-random-secret-key-here');

// 启用 CORS
define('JWT_AUTH_CORS_ENABLE', true);
```

3. 修改 `.htaccess`（Apache）或 Nginx 配置：

```apache
# .htaccess
RewriteEngine On
RewriteCond %{HTTP:Authorization} ^(.*)
RewriteRule ^(.*) - [E=HTTP_AUTHORIZATION:%1]
```

```nginx
# nginx.conf
location ~ /wp-json/ {
    rewrite ^/wp-json/(.*)$ /index.php?rest_route=/$1 last;
}
```

### 3.2 生成 JWT 密钥

```bash
# 使用 openssl 生成随机密钥
openssl rand -base64 64
```

将输出粘贴到 `wp-config.php` 的 `JWT_AUTH_SECRET_KEY`。

### 3.3 uni-app 端登录流程

```js
// 1. 用户输入邮箱 + 密码
const login = async (username, password) => {
  // JWT 登录直接请求 WP 端点（不经代理，代理层不处理 JWT）
  const res = await uni.request({
    url: 'https://moyuyo.com/wp-json/jwt-auth/v1/token',
    method: 'POST',
    data: { username, password },
  });
  
  // 存储 Token
  const { token } = res.data;
  uni.setStorageSync('jwt_token', token);
  return res.data;
};

// 2. 后续请求携带 Token
const apiWithAuth = (url, options = {}) => {
  const token = uni.getStorageSync('jwt_token');
  return uni.request({
    url,
    header: {
      'Authorization': `Bearer ${token}`,
      ...options.header,
    },
    ...options,
  });
};
```

### 3.4 Token 刷新策略

- Token 有效期：**24 小时**（在 `wp-config.php` 中配置）
- 刷新策略：APP 启动时检查 Token 是否过期（解析 JWT exp 字段）
- 过期处理：引导用户重新登录
- 注意：JWT 插件默认不支持 Refresh Token，如需无感刷新需自行扩展或使用第三方方案

---

## 四、权限模型

| 操作 | 认证方式 | 权限要求 |
| :--- | :--- | :--- |
| 浏览商品 | 代理层 Basic Auth（无需 JWT） | — |
| 搜索商品 | 代理层 Basic Auth（无需 JWT） | — |
| 创建订单 | 代理层 Basic Auth（无需 JWT） | — |
| 查询自己订单 | JWT Bearer Token | 登录用户 |
| 查看用户信息 | JWT Bearer Token | 登录用户 |
| 管理后台 | WordPress 登录（Cookie） | 管理员 |

---

## 五、安全最佳实践

1. **API 密钥定期轮换**：建议每 90 天重新生成一份 Consumer Key/Secret 对
2. **IP 白名单**：在 Wordfence 中限制 `/wp-json/wc/v3/*` 仅允许 Cloudflare Workers IP 段访问
3. **HTTPS 强制**：全链路 HTTPS，禁止 HTTP 明文传输
4. **WordPress 安全**：
   - 隐藏 `wp-admin` 登录路径（使用 WPS Hide Login 插件）
   - 禁用 XML-RPC
   - 限制登录尝试次数
5. **Workers 安全**：
   - 速率限制：每 IP ≤ 60 请求/分钟
   - 不记录敏感数据到日志
   - 定期审计 Workers 访问日志

---

## 六、常见问题

**Q: 为什么 JWT 不经过代理层？**
A: JWT 包含用户密码，代理层处理 JWT 会增加密钥管理复杂度。JWT 端点直接请求 WP 即可。

**Q: 代理层挂了怎么办？**
A: Cloudflare Workers 具有高可用性（全球边缘节点），单节点故障不影响其他节点。但建议准备降级方案（直连 WP + 临时密钥）。

**Q: 怎么防止 API 被滥用？**
A: Cloudflare Workers 层做速率限制（60次/min/IP）+ Wordfence WAF 层做恶意请求拦截，双重防护。
