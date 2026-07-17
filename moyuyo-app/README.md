# MOYUYO ATELIER

> **A Modern Luxury Pet Lifestyle House.** Every Journey Together.
> 魔芋友 / MoyuYo 宠物生活

基于 **uni-app (Vue 3)** + **WordPress/WooCommerce REST API** 的跨端宠物电商 App。
一套代码同时编译到 **Android / iOS / H5 / 微信小程序** 等 10+ 平台。

配套后端服务 [`moyuyo-server`](file:///d:/MOYUYOWPC/moyuyo-server) 基于 **Spring Boot 3.5 + MyBatis-Plus** 构建，
提供完整的 REST API、管理后台、数据库迁移及 Docker 部署支持。

---

## 技术栈

| 层       | 技术                                       |
| :------- | :----------------------------------------- |
| 前端框架 | uni-app (Vue 3 + Vite)                     |
| 状态管理 | Pinia                                      |
| UI 组件  | uView Plus 3.x                             |
| 语言     | JavaScript + TypeScript (混合)             |
| HTTP     | uni.request 封装                           |
| 后端     | WordPress + WooCommerce (REST API v3)      |
| 鉴权     | OAuth 2.0 Password Grant (WP OAuth Server) |
| 原生插件 | UTSDK 桥接 (Android Kotlin / iOS Swift)    |
| 代码规范 | ESLint + Prettier + Husky + lint-staged    |
| 打包     | HBuilderX 云打包 / Vite CLI                |

### 目标平台

| 平台              | 命令                    |
| :---------------- | :---------------------- |
| H5                | `npm run dev:h5`        |
| 微信小程序        | `npm run dev:mp-weixin` |
| Android / iOS App | `npm run dev:app`       |
| 支付宝小程序      | uni -p mp-alipay        |
| 百度小程序        | uni -p mp-baidu         |
| 抖音/头条小程序   | uni -p mp-toutiao       |
| QQ 小程序         | uni -p mp-qq            |
| 快手小程序        | uni -p mp-kuaishou      |
| 飞书小程序        | uni -p mp-lark          |
| 京东小程序        | uni -p mp-jd            |
| 小红书小程序      | uni -p mp-xhs           |
| 鸿蒙 App          | uni -p app-harmony      |
| 鸿蒙快应用        | uni -p quickapp-webview |

---

## 后端服务（moyuyo-server）

后端项目 [`moyuyo-server`](file:///d:/MOYUYOWPC/moyuyo-server) 与前端 `moyuyo-app` 同级目录，为 APP 提供完整 API 支持。

### 技术栈

| 层       | 技术                |
| :------- | :------------------ |
| 框架     | Spring Boot 3.5.16  |
| 语言     | Java 25             |
| ORM      | MyBatis-Plus 3.5.9  |
| 数据库   | MySQL + Flyway 迁移 |
| 缓存     | Redis               |
| 鉴权     | JWT (jjwt)          |
| API 文档 | SpringDoc OpenAPI   |
| 部署     | Docker              |

### 模块结构

| 模块               | 职责                                        |
| :----------------- | :------------------------------------------ |
| **moyuyo-api**     | Spring Boot 启动入口 + 40+ REST Controller  |
| **moyuyo-common**  | 公共层：DTO、JWT 鉴权、全局异常、Redis 配置 |
| **moyuyo-dao**     | 数据访问层：60+ Entity、MyBatis Mapper      |
| **moyuyo-service** | 业务逻辑层：37 个 Service 接口与实现        |
| **moyuyo-admin**   | 管理后台 SPA（Vue 3，构建后嵌入 api 模块）  |

### 数据流

```
moyuyo-app（跨端 APP）
    ↓ HTTPS
moyuyo-server（后端 API）
    ├── moyuyo-api（Controller）
    ├── moyuyo-service（Service）
    ├── moyuyo-dao（DAO）
    └── moyuyo-common（公共组件）
         ↓
    [MySQL + Redis]
```

---

## 项目结构

```
moyuyo-app/
├── src/
│   ├── api/                   # API 调用层（35+ 模块）
│   │   ├── product.js         # 商品 API
│   │   ├── order.js           # 订单 API
│   │   ├── user.js            # 用户 API
│   │   ├── cart.js            # 购物车 API
│   │   ├── coupon.js          # 优惠券 API
│   │   ├── woocommerce.js     # WooCommerce 通用 API
│   │   ├── pet.js             # 宠物 API
│   │   ├── petAlbum.js        # 宠物相册
│   │   ├── petDiary.js        # 成长日记
│   │   ├── petWeight.js       # 体重记录
│   │   ├── petDresser.js      # 宠物装扮
│   │   ├── community.js       # 社区 API
│   │   ├── address.js         # 地址 API
│   │   ├── member.js          # 会员 API
│   │   ├── review.js          # 评价 API
│   │   ├── points.js          # 积分 API
│   │   ├── flashSale.js       # 限时抢购
│   │   ├── groupBuy.js        # 拼团
│   │   ├── bargain.js         # 砍价
│   │   ├── bundleDeal.js      # 套餐优惠
│   │   ├── giftCard.js        # 礼品卡
│   │   ├── invite.js          # 邀请
│   │   ├── mission.js         # 任务
│   │   ├── notification.js    # 通知
│   │   ├── feedback.js        # 反馈
│   │   ├── afterSales.js      # 售后
│   │   ├── recycleBin.js      # 回收站
│   │   ├── invoice.js         # 发票
│   │   ├── liveRoom.js        # 直播间
│   │   ├── lottery.js         # 抽奖
│   │   ├── subscribe.js       # 订阅
│   │   ├── browsingHistory.js # 浏览记录
│   │   └── index.js           # 统一出口
│   ├── store/                 # Pinia 状态管理
│   │   ├── user.js            # 用户状态
│   │   ├── cart.js            # 购物车
│   │   ├── product.js         # 商品/分类
│   │   ├── theme.js           # 主题
│   │   ├── pet.js             # 宠物
│   │   └── index.js           # 统一出口
│   ├── plugins/               # 原生插件桥接层（JS/TS）
│   │   ├── payment.js         # 支付（Apple Pay / Google Pay）
│   │   ├── payment.ts         # 支付 TypeScript 入口
│   │   ├── auth.js            # 社交登录（Apple / Google / Facebook）
│   │   ├── auth.ts            # 登录 TypeScript 入口
│   │   ├── petHub3d.js        # PetHub 3D 场景
│   │   ├── pethub.ts          # PetHub TypeScript 入口
│   │   ├── arTryOn.js         # AR 试穿
│   │   ├── push.js            # 推送
│   │   ├── camera.js          # 相机/相册
│   │   ├── location.js        # 定位
│   │   ├── biometric.js       # 生物识别
│   │   ├── index.js           # 统一导出
│   │   ├── index.ts           # TS 统一导出
│   │   └── types.ts           # 桥接协议类型定义
│   ├── utils/                 # 工具方法
│   │   ├── config.js          # 环境配置
│   │   ├── request.js         # 统一请求封装
│   │   └── storage.js         # 本地存储
│   ├── styles/                # 全局样式
│   │   ├── variables.scss     # 品牌色 + Design Tokens
│   │   └── common.scss
│   ├── pages/                 # 页面（60+）
│   │   ├── tabbar/            # 5 个底部 Tab
│   │   ├── goods/             # 商品（18 个页面）
│   │   ├── cart/              # 购物车（2 个页面）
│   │   ├── order/             # 订单（10 个页面）
│   │   ├── user/              # 用户（40+ 个页面）
│   │   ├── pet/               # Pet Hub（9 个页面）
│   │   ├── community/         # 社区（4 个页面）
│   │   ├── webview/           # WebView（1 个页面）
│   │   ├── common/            # 通用（2 个页面）
│   │   └── debug/             # 调试（1 个页面）
│   ├── App.vue
│   ├── main.js
│   ├── manifest.json
│   ├── pages.json
│   └── uni.scss
├── nativeplugins/             # 原生插件（Android + iOS）
│   ├── android/               # Android UTSDK 插件（9 个模块）
│   │   ├── moyuyo-payment/    # 支付
│   │   ├── moyuyo-auth/       # 社交登录
│   │   ├── moyuyo-pethub/     # PetHub 3D
│   │   ├── moyuyo-ar/         # AR 试穿
│   │   ├── moyuyo-push/       # 推送
│   │   ├── moyuyo-camera/     # 相机
│   │   ├── moyuyo-location/   # 定位
│   │   ├── moyuyo-biometric/  # 生物识别
│   │   └── moyuyo-biometric/  # 生物识别
│   └── ios/                   # iOS UTSDK 插件（9 个模块）
├── .env                       # 环境变量（**不入 Git**）
├── .env.example               # 环境变量模板
├── .eslintrc.js               # ESLint 配置
├── .gitignore
├── tsconfig.json              # TypeScript 配置
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
VITE_OAUTH_CLIENT_ID=your_oauth_client_id
VITE_OAUTH_CLIENT_SECRET=your_oauth_client_secret

# 结算货币（默认 USD）
VITE_CURRENCY=USD

# 主题模式：light / dark / system（默认 system）
VITE_THEME_MODE=system
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

### 5. 代码检查

```bash
# ESLint 检查并修复
npm run lint

# Prettier 格式化
npm run format
```

---

## 原生插件体系

项目通过 uni-app UTSDK 桥接原生能力，支持 iOS (Swift) 和 Android (Kotlin) 双端。

| 插件      | iOS 模块        | Android 模块     | 能力                                   |
| :-------- | :-------------- | :--------------- | :------------------------------------- |
| 支付      | MOYUYOPayment   | moyuyo-payment   | Apple Pay / Google Pay                 |
| 社交登录  | MOYUYOAuth      | moyuyo-auth      | Sign in with Apple / Google / Facebook |
| PetHub 3D | MOYUYOPetHub3D  | moyuyo-pethub    | 3D 宠物模型加载/场景切换/交互          |
| AR 试穿   | MOYUYOAR        | moyuyo-ar        | ARKit / ARCore 商品试穿                |
| 推送      | MOYUYOPush      | moyuyo-push      | APNs / FCM 推送                        |
| 相机      | MOYUYOCamera    | moyuyo-camera    | 拍照 / 相册选择 / 图片压缩             |
| 定位      | MOYUYOLocation  | moyuyo-location  | 定位 / 地图选择                        |
| 生物识别  | MOYUYOBiometric | moyuyo-biometric | Face ID / 指纹                         |

插件桥接协议定义见 [`src/plugins/types.ts`](file:///d:/MOYUYOWPC/moyuyo-app/src/plugins/types.ts)。

---

## WordPress 后端环境配置

### 必装插件

| 插件                              | 用途                                          |
| :-------------------------------- | :-------------------------------------------- |
| **WooCommerce**                   | 核心电商引擎                                  |
| **WP OAuth Server**（MiniOrange） | OAuth 2.0 用户鉴权（替代 JWT Authentication） |
| **Super Socializer**              | 社交登录（配合 WP OAuth Server 使用）         |
| **WP Offload Media**              | 图片对象存储（可选）                          |

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

部署后将 `VITE_API_BASE` 改为 Workers 域名：

```env
VITE_API_BASE=https://api.moyuyo.com
```

---

## 核心业务流程

### 1. 注册 / 登录

- **邮箱+密码注册**：`POST /wc/v3/customers`
- **OAuth 登录**：`POST /api/v1/token`（Password Grant）
  - 参数：`grant_type=password&username=<email>&password=<pwd>&client_id=<id>`
  - 返回：`{ access_token, refresh_token, expires_in }`
- **社交登录**：通过原生插件调用 Apple / Google / Facebook → 获取 OAuth token
- Token 存于本地（`moyuyo_token`），后续请求自动附加 `Authorization: Bearer <token>`

### 2. 商品浏览 / 搜索 / 促销

- 商品列表：`/wc/v3/products`
- 分类：`/wc/v3/products/categories`
- 搜索：通过 `search` 参数过滤
- 促销活动：限时抢购、拼团、砍价、套餐优惠、众筹、公益捐赠

### 3. 购物车（本地优先）

- 游客也可加购，本地存储（Pinia + Storage）
- 下单时同步到 WooCommerce 订单

### 4. 下单 / 支付（方案A）

1. APP 调 `POST /wc/v3/orders` 创建 pending 订单
2. WP 返回 `order-pay/{id}` URL
3. APP 打开 WebView 加载 WP 支付页
4. 支付完成 → WebView 通过 `postMessage` 通知 APP
5. APP 同时轮询订单状态（兜底）
6. 跳转到订单详情

### 5. 原生支付（方案B — 推荐）

1. APP 创建 WooCommerce 订单
2. 通过原生支付插件调用 Apple Pay / Google Pay
3. 支付结果由原生插件回调返回
4. APP 更新订单状态

### 6. AR 试穿

1. 商品详情页进入 AR 试穿
2. 原生 AR 插件启动 ARKit / ARCore
3. 虚拟商品叠加到现实场景
4. 拍照保存 → 分享 / 加购

### 7. Pet Hub 3D

1. 创建宠物档案 → 生成 3D 模型
2. 切换场景（草地/客厅/训练室/摄影棚）
3. 宠物互动（点击/抚摸反馈）
4. 装扮系统：为宠物穿戴虚拟商品
5. 成长记录 + 健康日历 + 体重追踪

---

## 品牌色板

| 用途 | 名称          | 色值      |
| :--- | :------------ | :-------- |
| 主色 | Sand Gold     | `#DBC98A` |
| 背景 | Linen         | `#F6F2EE` |
| 文字 | Charcoal      | `#2E2B29` |
| 强调 | Antique Brass | `#B38A5A` |
| 成功 | Sage Mist     | `#ABB9AD` |
| 情感 | Dusty Rose    | `#D9B4B0` |

完整规范见 [`src/styles/variables.scss`](file:///d:/MOYUYOWPC/moyuyo-app/src/styles/variables.scss)。

---

## 4 大产品线

| 产品线    | 旗舰 IP      | 颜色          |
| :-------- | :----------- | :------------ |
| CARE 洗护 | LUNA 策展家  | Sand Gold     |
| GEAR 出行 | MILO 探险家  | Sage Mist     |
| PLAY 玩具 | ATLAS 守护者 | Antique Brass |
| HOME 家居 | OLIVE 艺术家 | Dusty Rose    |

---

## 开发规范

- 注释：中文
- 缩进：2 空格
- 函数命名：camelCase
- 组件命名：PascalCase
- 常量命名：SNAKE_CASE
- 敏感信息：`.env` 管理，不提交 Git
- 代码检查：提交前自动执行 ESLint + Prettier（Husky + lint-staged）
- 跨端兼容：通过条件编译处理平台差异（`#ifdef` / `#ifndef`）

---

## License

Proprietary © 2026 MOYUYO ATELIER
