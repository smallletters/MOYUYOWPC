# MOYUYO ATELIER

> **A Modern Luxury Pet Lifestyle House.** Every Journey Together.
> 魔芋友 / MoyuYo 宠物生活

基于 **uni-app (Vue 3)** + **WordPress/WooCommerce REST API** 的跨端宠物电商 App。
一套代码同时编译到 **Android / iOS / H5 / 微信小程序**。

---

## 技术栈

| 层 | 技术 |
| :- | :- |
| 前端框架 | uni-app (Vue 3) |
| 状态管理 | Pinia |
| UI 组件 | uView Plus 3.x |
| HTTP | uni.request 封装 |
| 后端 | WordPress + WooCommerce（REST API v3） |
| 鉴权 | JWT Authentication for WP REST API |
| 打包 | HBuilderX 云打包 |

---

## 项目结构

```
moyuyo-app/
├── src/
│   ├── api/                 # API 调用层
│   │   ├── product.js       # 商品 API
│   │   ├── order.js         # 订单 API
│   │   ├── user.js          # 用户 API
│   │   ├── coupon.js        # 优惠券 API
│   │   └── index.js
│   ├── store/               # Pinia 状态管理
│   │   ├── user.js          # 用户状态
│   │   ├── cart.js          # 购物车
│   │   ├── product.js       # 商品/分类
│   │   ├── theme.js         # 主题
│   │   └── index.js
│   ├── utils/               # 工具方法
│   │   ├── config.js        # 环境配置
│   │   ├── request.js       # 统一请求封装
│   │   └── storage.js       # 本地存储
│   ├── styles/              # 全局样式
│   │   ├── variables.scss   # 品牌色 + Design Tokens
│   │   └── common.scss
│   ├── pages/               # 页面
│   │   ├── tabbar/          # 5 个底部 Tab
│   │   │   ├── home.vue
│   │   │   ├── category.vue
│   │   │   ├── pet.vue
│   │   │   ├── community.vue
│   │   │   └── user.vue
│   │   ├── goods/           # 商品相关
│   │   │   ├── list.vue
│   │   │   ├── detail.vue
│   │   │   └── search.vue
│   │   ├── cart/            # 购物车
│   │   │   ├── index.vue
│   │   │   └── checkout.vue
│   │   ├── order/           # 订单
│   │   │   ├── list.vue
│   │   │   ├── detail.vue
│   │   │   ├── logistics.vue
│   │   │   └── review.vue
│   │   ├── user/            # 用户
│   │   │   ├── login.vue
│   │   │   ├── register.vue
│   │   │   ├── profile.vue
│   │   │   ├── address.vue
│   │   │   ├── address-edit.vue
│   │   │   ├── devices.vue
│   │   │   └── security.vue
│   │   ├── pet/             # Pet Hub
│   │   │   ├── profile.vue
│   │   │   ├── health.vue
│   │   │   └── achievement.vue
│   │   └── webview/
│   │       └── pay.vue      # 支付 WebView（方案A）
│   ├── App.vue
│   ├── main.js
│   ├── manifest.json
│   └── pages.json
├── .env                     # 环境变量（含 API 凭证，**不入 Git**）
├── .env.example
├── .gitignore
├── package.json
├── vite.config.js
└── index.html
```

---

## 快速开始

### 1. 安装依赖

```bash
npm install
# 或
pnpm install
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 填入实际配置：

```env
# API 基础地址（生产环境推荐 Cloudflare Workers 代理层）
VITE_API_BASE=https://your-wp-site.com/wp-json

# WordPress 站点地址
VITE_WP_BASE=https://your-wp-site.com

# WooCommerce REST API 凭证
VITE_CONSUMER_KEY=your_consumer_key
VITE_CONSUMER_SECRET=your_consumer_secret

# WP OAuth Server（MiniOrange）OAuth 凭证
# 在 WP 后台 → WP OAuth Server → 客户端 → 添加客户端 → 获取
VITE_OAUTH_CLIENT_ID=your_oauth_client_id
VITE_OAUTH_CLIENT_SECRET=your_oauth_client_secret
```

### 3. 启动开发

```bash
# H5 端
npm run dev:h5

# 微信小程序
npm run dev:mp-weixin

# APP 端（需 HBuilderX 真机运行）
npm run dev:app
```

### 4. 打包发布

```bash
# H5 端
npm run build:h5

# 微信小程序
npm run build:mp-weixin

# APP 端（推荐使用 HBuilderX 云打包）
npm run build:app
```

---

## WordPress 后端环境配置

### 必装插件

| 插件 | 用途 |
| :- | :- |
| **WooCommerce** | 核心电商引擎 |
| **WP OAuth Server**（MiniOrange） | OAuth 2.0 用户鉴权（替代 JWT Authentication） |
| **Super Socializer** | 社交登录（配合 WP OAuth Server 使用） |
| **WP Offload Media** | 图片对象存储（可选） |

### WP OAuth Server 配置步骤

1. **安装并激活**：
   - WP 后台 → 插件 → 安装 → 搜索 "WP OAuth Server" → 安装并激活
   - 也可以同时安装 "REST API Authentication" 插件

2. **启用 OAuth**：WP OAuth Server → 设置 → 勾选 **Password Grant** 方法
   - Password Grant 支持邮箱+密码直接换取 access_token（适合 APP 端）

3. **创建 OAuth Client**：WP OAuth Server → 客户端 → 添加客户端
   - 名称：`moyuyo-app`
   - 授权类型：`Password Grant, Refresh Token, Client Credentials`
   - 重定向 URI：`https://your-app.com/oauth-callback`
   - 记下生成的 **Client ID** 和 **Client Secret**

4. **配置 .env**：
   ```env
   VITE_OAUTH_CLIENT_ID=你的ClientID
   VITE_OAUTH_CLIENT_SECRET=你的ClientSecret
   ```

5. **社交登录配合**（可选）：
   - 安装 Super Socializer 用于前端社交登录
   - WP OAuth Server 的 `/wp-json/moserver/authorize` 端点处理社交登录回调
   - 无需额外插件即可实现邮箱密码 + 社交登录的统一 OAuth 流程

### 配置步骤

1. **固定链接**：WP 后台 → 设置 → 固定链接 → 文章名格式（`/%postname%/`）
2. **生成 API 凭证**：WooCommerce → 设置 → 高级 → REST API → 添加密钥
3. **CORS 配置**（`wp-config.php`）：

```php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Authorization, Content-Type');
```

---

## 🔒 API 代理层（生产环境必须实施）

**警告**：直接将 `Consumer Key` / `Consumer Secret` 写入 uni-app 代码并打包到 APK/IPA，
等同于把 WP 后台完整读写权限公开（任何人均可通过反编译提取）。

### 推荐方案：Cloudflare Workers 代理

```
uni-app App（无密钥）
       ↓ HTTPS
┌──────────────────────────────┐
│  Cloudflare Workers 代理层    │
│  - 持有 Consumer Key/Secret  │
│  - 附加 Basic Auth 头        │
│  - 速率限制 / CORS / 日志    │
└──────────┬───────────────────┘
           ↓ HTTPS + Basic Auth
┌──────────────────────────────┐
│  WordPress + WooCommerce      │
│  /wp-json/wc/v3/*            │
└──────────────────────────────┘
```

详细 Workers 代码示例见 [`docs/需求文档.md §5.4`](../docs/需求文档.md)。

部署后将 `VITE_API_BASE` 改为 Workers 域名：
```env
VITE_API_BASE=https://api.moyuyo.com
```

---

## 核心业务流程

### 1. 注册 / 登录
- 邮箱 + 密码注册（`/wc/v3/customers`）
- 登录：OAuth Password Grant（`POST /api/v1/token`）
  - 参数：`grant_type=password&username=<email>&password=<pwd>&client_id=<id>`
  - 返回：`{ access_token, refresh_token, expires_in }`
- Token 存于本地（`moyuyo_token`），后续请求自动附加 `Authorization: Bearer <token>`
- 用户信息通过 `GET /api/v1/userinfo` 获取

### 2. 商品浏览 / 搜索
- 商品列表：`/wc/v3/products`
- 分类：`/wc/v3/products/categories`
- 搜索：通过 `search` 参数过滤

### 3. 购物车（本地优先）
- 游客也可加购，本地存储
- 下单时同步到 WooCommerce 订单

### 4. 下单 / 支付（方案A）
1. APP 调 `POST /wc/v3/orders` 创建 pending 订单
2. WP 返回 `order-pay/{id}` URL
3. APP 打开 WebView 加载 WP 支付页
4. 支付完成 → WebView 通过 `postMessage` 通知 APP
5. APP 同时轮询订单状态（兜底）
6. 跳转到订单详情

---

## 品牌色板

| 用途 | 名称 | 色值 |
| :- | :- | :- |
| 主色 | Sand Gold | `#DBC98A` |
| 背景 | Linen | `#F6F2EE` |
| 文字 | Charcoal | `#2E2B29` |
| 强调 | Antique Brass | `#B38A5A` |
| 成功 | Sage Mist | `#ABB9AD` |
| 情感 | Dusty Rose | `#D9B4B0` |

完整规范见 [`src/styles/variables.scss`](./src/styles/variables.scss)。

---

## 4 大产品线

| 产品线 | 旗舰 IP | 颜色 |
| :- | :- | :- |
| CARE 洗护 | LUNA 策展家 | Sand Gold |
| GEAR 出行 | MILO 探险家 | Sage Mist |
| PLAY 玩具 | ATLAS 守护者 | Antique Brass |
| HOME 家居 | OLIVE 艺术家 | Dusty Rose |

---

## 开发规范

- 注释：中文
- 缩进：2 空格
- 函数命名：camelCase
- 组件命名：PascalCase
- 常量命名：SNAKE_CASE
- 敏感信息：`.env` 管理，不提交 Git

---

## License

Proprietary © 2026 MOYUYO ATELIER
