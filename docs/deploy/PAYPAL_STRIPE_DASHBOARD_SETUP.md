# PayPal & Stripe Dashboard 配置指南

> **适用版本**：MOYUYO 美国市场上线版
> **线上域名**：moyuyoshop.com
> **APP Bundle ID**：com.moyuyoshop.app
> **Apple Pay Merchant ID**：merchant.com.moyuyoshop.app
> **文档版本**：v1.0  /  更新日期：2026-09-01

本文档列出 APP 上线前必须在 4 个第三方控制台完成的配置。**所有 5 项配置缺一不可**，否则会出现"用户付完款后 APP 回不来"或"Apple Pay 按钮不显示"等致命问题。

---

## 总览

| # | 平台 | 配置位置 | 关键值 | 影响 |
|---|---|---|---|---|
| 1 | PayPal Developer | App Settings → Return URLs | `com.moyuyoshop.app://paypalpay` | PayPal 渠道回跳 |
| 2 | Stripe Dashboard | Settings → Payment methods → Apple Pay | `merchant.com.moyuyoshop.app` | iOS Apple Pay 按钮 |
| 3 | Stripe Dashboard | Settings → Payment methods → Apple Pay → Web domains | `moyuyoshop.com` | Apple Pay 域验证 |
| 4 | Apple Developer | Identifiers → Merchant IDs | `merchant.com.moyuyoshop.app` | iOS entitlement |
| 5 | 服务端部署 | 后端 `.env` → `PAYPAL_ALLOWED_ORIGINS` | `https://moyuyoshop.com,https://www.moyuyoshop.com,https://api.moyuyoshop.com` | PayPal returnUrl 白名单 |

---

## 1. PayPal Developer Dashboard — 配置 Return URL

### 目的
让 PayPal 渠道付款完成后能从 PayPal 网页/APP 返回 Moyuyo APP。

### 步骤

1. 打开 https://developer.paypal.com → 右上角登录 → **Apps & Credentials**
2. 切到 **Live** 标签（生产环境），找到已创建的 APP（例如 `MOYUYO-Live`）
3. 点击 APP 名称进入详情
4. 找到 **App Settings** 卡片 → **Return URLs**（部分版本显示为 **Return URL**）
5. 点击 **Add** → 输入：
   ```
   com.moyuyoshop.app://paypalpay
   ```
6. 点击 **Save** 保存

### 验证

- Sandbox 环境也建议加同样 URL，便于联调：
  - 切到 **Sandbox** 标签 → 同样路径加 `com.moyuyoshop.app://paypalpay`

### 常见错误

| 错误现象 | 原因 | 修复 |
|---|---|---|
| PayPal 付完跳到 PayPal 网站首页 | Return URL 没配或拼写错 | 检查大小写、scheme、host 都要精确一致 |
| PayPal 报"Return URL is not registered" | 没在 Return URLs 列表里 | 加 URL 后重试 |
| iOS 跳 scheme 后没反应 | iOS urlschemewitelist 没加 `com.moyuyoshop.app` | 见 manifest.json 配置 |

---

## 2. Stripe Dashboard — 注册 Apple Pay Merchant ID

### 目的
让 Stripe Checkout 在 iOS Safari/Chrome 内能调起 Apple Pay 按钮（PassKit）。

### 前置
- 已在 Apple Developer 后台创建 `merchant.com.moyuyoshop.app`（见第 4 节）
- 已下载 Apple Pay 证书（`.cer` 文件）

### 步骤

1. 打开 https://dashboard.stripe.com → 登录 → 切到 **Live**（生产）
2. 左侧菜单 **Settings** → **Payments** → **Payment methods**
3. 找到 **Apple Pay** 行 → 点击右边的齿轮图标 → **Configure**
4. 在 **Merchant identifier** 输入框填入：
   ```
   merchant.com.moyuyoshop.app
   ```
5. 点击 **Add new domain** → 输入：
   ```
   moyuyoshop.com
   ```
6. 接下来 Stripe 会要求下载验证文件（`.txt`），放到 `https://moyuyoshop.com/.well-known/apple-developer-merchantid-domain-association`
   - 后端静态文件可直接放 `src/main/resources/static/.well-known/apple-developer-merchantid-domain-association`
   - 也可以用 Nginx 直接 serve：
     ```nginx
     location = /.well-known/apple-developer-merchantid-domain-association {
         root /var/www/moyuyoshop;
     }
     ```
7. 点击 **Verify** → 通过后显示绿色对勾
8. 同样在 **Test mode** 标签下重复上述步骤（联调需要）

### 验证

- 在 Stripe Dashboard → Settings → Payment methods → Apple Pay 状态应为 **Enabled**
- iOS 真机 Safari 打开 `https://buy.stripe.com/test_xxx` 测试支付，应能看到 Apple Pay 按钮

---

## 3. Stripe Dashboard — 启用 Google Pay

### 目的
让 Stripe Checkout 在 Android Chrome 内能调起 Google Pay 按钮。

### 步骤

1. https://dashboard.stripe.com → Settings → Payments → Payment methods
2. 找到 **Google Pay** 行 → 点击 **Turn on** 或齿轮 → **Configure**
3. 默认 Stripe 会自动启用，无需额外配置（Merchant ID 用 Stripe 默认）
4. **Production** 标签下也重复一遍
5. （可选）Stripe Dashboard → Settings → Branding 上传 MOYUYO Logo / 品牌色，让支付页更专业

### 验证

- Android Chrome 真机打开 Stripe 测试页 → 应能看到 Google Pay 按钮
- 真机需安装 Google Pay APP（部分 ROM 无 G Pay，会被 Stripe 自动隐藏按钮）

---

## 4. Apple Developer — 创建 Merchant ID

### 目的
iOS APP 要用 Apple Pay 必须先在 Apple 后台注册 Merchant ID 并启用 Apple Pay capability。

### 前置
- Apple Developer 账号（$99/年）
- 已创建 App ID `com.moyuyoshop.app`

### 步骤

1. 打开 https://developer.apple.com/account → **Certificates, Identifiers & Profiles**
2. 左侧菜单 **Identifiers** → 点击右上角 **+**
3. 选择 **Merchant IDs** → **Continue**
4. 输入：
   - Description: `MOYUYO Apple Pay`
   - Identifier: `merchant.com.moyuyoshop.app`
5. **Register** → 列表里能看到刚创建的 Merchant ID
6. 点击进入 → 勾选 **Apple Pay Payment Processing** 复选框 → **Save**
7. （**Cert 步骤**）回到 Merchant ID 详情 → **Create Certificate**：
   - 按提示在 Mac 上打开 Keychain Access → Certificate Assistant → Request a Certificate
   - 上传 CSR 文件 → 下载 `.cer`（Apple Pay Payment Processing Certificate）
   - **这个证书要上传到 Stripe Dashboard**（见第 2 节 Apple Pay 配置流程）

### 关联到 App ID

1. 左侧菜单 **Identifiers** → 找到 App ID `com.moyuyoshop.app` → 点击进入
2. 勾选 **Apple Pay Payment Processing** capability → **Edit**
3. 勾选刚创建的 Merchant ID `merchant.com.moyuyoshop.app` → **Continue** → **Save**

### 更新 manifest.json

项目里 `src/manifest.json` 已经写好：

```json
{
  "ios": {
    "capabilities": {
      "entitlements": {
        "com.apple.developer.in-app-payments": [
          "merchant.com.moyuyoshop.app"
        ]
      }
    }
  }
}
```

打自定义基座或云打包时，HBuilderX 会自动写入到 `.entitlements` 文件。

---

## 5. 服务端 `.env` 配置

### 目的
PayPal 创建订单时校验 `returnUrl` 域名是否在白名单内；不配会被 PayPal 拒。

### 文件

生产环境 `/opt/moyuyo/.env`（或 1Panel 部署对应的环境变量配置）。

### 配置项

```bash
# PayPal 回调域名白名单（逗号分隔）
PAYPAL_ALLOWED_ORIGINS=https://moyuyoshop.com,https://www.moyuyoshop.com,https://api.moyuyoshop.com

# Stripe 配置（生产）
STRIPE_SECRET_KEY=sk_live_xxx
STRIPE_PUBLISHABLE_KEY=pk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx

# PayPal 配置（生产）
PAYPAL_CLIENT_ID=xxx
PAYPAL_CLIENT_SECRET=xxx
PAYPAL_WEBHOOK_ID=xxx
PAYPAL_MODE=live
```

### 生效方式

修改后必须**重启后端服务**：

```bash
# Docker 部署
docker compose restart moyuyo-api

# 1Panel 部署
1panel restart moyuyo-api

# 裸进程部署
systemctl restart moyuyo-api
```

### 验证

```bash
# 下单测试 PayPal 渠道，看后端日志
curl -X POST https://api.moyuyoshop.com/api/v1/payment/create \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"TEST_001","payChannel":"PAYPAL","payMethod":"PAYPAL"}'
```

返回里 `approvalUrl` 应是 PayPal 真实 URL，且带 `return_url` / `cancel_url` 指向 `https://moyuyoshop.com/payment/return.html?...`。

---

## 附：完整支付链路图

```
[Moyuyo APP checkout 页面]
  用户选 Google Pay / Apple Pay / PayPal / Card
          │
          ▼
  /pages/order/pay?channel=STRIPE&method=GOOGLE_PAY|APPLE_PAY|PAYPAL|CARD&id=xxx
          │
          ▼
  POST /api/v1/payment/create
          │
          ├── payChannel=STRIPE ──► Stripe Checkout Session
          │     ├─ returnURL → moyuyoshop.com/payment/return.html?status=success
          │     ├─ cancelURL → moyuyoshop.com/payment/return.html?status=cancel
          │     └─ APP 端再覆盖 → moyuyo://pay/return?status=success
          │
          └── payChannel=PAYPAL ──► PayPal v2 Orders API
                ├─ returnURL → moyuyoshop.com/payment/return.html?status=success
                └─ APP 端再覆盖 → com.moyuyoshop.app://paypalpay?status=success
          │
          ▼
  返回 sessionUrl / approvalUrl
          │
          ▼
  [APP WebView 加载]
  payAppBridge.createPaymentWebView()
  overrideUrlLoading 拦截:
    - alipays/cashme/venmo 等 → 已被精简掉,不再拦截
    - paypal:// / paypalme:// / paypalpay:// → plus.runtime.openURL 唤起 PayPal APP
    - googlepay:// / gpay:// → 唤起 G Pay APP
    - intent://paypal.google... → Android intent 唤起 G Pay APP
    - applepay:// / com-apple-payment-pass:// → iOS PassKit
    - moyuyo://pay/return?status=... → 命中回跳,关闭 WebView + 处理结果
          │
          ▼
  用户在第三方 APP 完成支付
          │
          ▼
  [第三方 APP 回跳]
  PayPal → com.moyuyoshop.app://paypalpay?status=success&orderNo=xxx
  Google Pay → moyuyoshop.com/payment/return.html?status=success
  Apple Pay → moyuyoshop.com/payment/return.html?status=success
          │
          ▼
  [Moyuyo APP 接收]
  App.vue onShow 监听 moyuyo://pay/return scheme
  或 PayPal SDK 直接唤回 APP
          │
          ▼
  pay.vue 关闭 WebView,显示成功,3秒后跳订单详情
          │
          ▼
  [后端异步确认]
  Stripe Webhook → POST /api/v1/webhook/stripe
  PayPal Webhook → POST /api/v1/webhook/paypal
  订单状态 PAID,后续发货流程启动
```

---

## 故障排查 checklist

| 现象 | 检查项 |
|---|---|
| PayPal 付完跳到 PayPal 网站 | Return URL 没加 → 看第 1 节 |
| iOS 跳 PayPal APP 后回不来 | urlschemewitelist 缺 `com.moyuyoshop.app` → 看 manifest.json |
| Apple Pay 按钮不显示 | Stripe Dashboard 缺 Apple Pay Merchant ID / 域验证失败 → 看第 2 节 |
| Google Pay 按钮不显示 | 用户没装 G Pay APP / Android 12+ 包名 visibility 没配 → 看 manifest.json queries |
| PayPal 创建订单 422 | returnUrl 不在白名单 → 看第 5 节 |
| 后端日志报 `certs directory not readable` | Stripe webhook secret 配置错 → 看第 5 节 STRIPE_WEBHOOK_SECRET |
| 真机点 Google Pay 没反应 | Android intent:// scheme 没被拦截 → 看 payAppBridge.js isExternalAppScheme 列表 |

---

## 上线前自测清单

- [ ] 第 1 节 PayPal Return URL 已加（Sandbox + Live）
- [ ] 第 2 节 Stripe Apple Pay Merchant ID + 域验证完成
- [ ] 第 3 节 Stripe Google Pay 已启用
- [ ] 第 4 节 Apple Developer Merchant ID 创建 + 关联 App ID
- [ ] 第 5 节 .env 已配置且重启后端
- [ ] 后端静态文件 `moyuyoshop.com/payment/return.html` 可访问（curl 200）
- [ ] 后端静态文件 `moyuyoshop.com/.well-known/apple-developer-merchantid-domain-association` 可访问
- [ ] iOS 真机测：Apple Pay 流程能跳 Face ID + 付完回 APP
- [ ] iOS 真机测：PayPal 流程能跳 PayPal APP + 付完回 APP
- [ ] Android 真机测：Google Pay 流程能跳 G Pay APP + 付完回 APP
- [ ] Android 真机测：Stripe 内信用卡输入能走通
- [ ] 后端日志查看 `payment webhook processed` 字样

---

## 参考资料

- [Stripe Apple Pay 配置](https://docs.stripe.com/apple-pay)
- [Stripe Google Pay 配置](https://docs.stripe.com/google-pay)
- [PayPal Return URL 配置](https://developer.paypal.com/api/rest/reference/wallet/experience-context/#definition-return_url)
- [Apple Pay Merchant ID 申请](https://developer.apple.com/documentation/passkit_apple_pay/setting_up_apple_pay)
- [uni-app Payment 文档](https://uniapp.dcloud.net.cn/tutorial/app-payment-stripe.html)