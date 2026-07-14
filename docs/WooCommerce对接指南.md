# MOYUYO × WooCommerce 对接指南

> 文档版本：V1.0.0  
> 编写日期：2026-07-02  
> 适用 WooCommerce 版本：≥ 7.0（WordPress ≥ 6.2）  
> 协议：REST API v3 + Webhook

---

## 一、整体对接流程

```
① WooCommerce 准备 → ② 创建 API Key → ③ 安装必备插件 → ④ 配置 Webhook
                                                                  ↓
⑧ 上线监控 ← ⑦ 全链路联调测试 ← ⑥ 启动商品/订单同步任务 ← ⑤ MOYUYO 后端配置
```

---

## 二、WooCommerce 准备工作

### 2.1 环境要求

| 项 | 要求 |
| :--- | :--- |
| WooCommerce | ≥ 7.0 |
| WordPress | ≥ 6.2 |
| PHP | ≥ 7.4（推荐 8.1+） |
| WordPress 永久链接 | 必须设置为 `Post name`（非默认 `?p=123`） |
| HTTPS | 必须启用（REST API 强制要求） |
| WP Cron | 需可用（部分 Webhook 依赖） |
| 时区 | 设置为 `Asia/Shanghai` |

**永久链接设置路径**：`WordPress 后台 → Settings → Permalinks → Post name → Save`

### 2.2 安装必备插件

| 插件 | 作用 | 是否必装 |
| :--- | :--- | :---: |
| **WooCommerce REST API**（自带） | 提供 REST API 能力 | ✅ |
| **WooCommerce Custom Orders Table**（HPOS） | 高性能订单表 | ✅ |
| **WP Webhooks** | 增强 Webhook 能力（可选） | ⚠️ 推荐 |
| **Chinese SEO** | 中文 URL 优化 | ⚠️ |
| **WP Mail SMTP** | 邮件通知稳定化 | ⚠️ |

---

## 三、创建 REST API 密钥

### 步骤 1：进入 API 设置

`WordPress 后台 → WooCommerce → Settings → Advanced → REST API`

### 步骤 2：添加 Key

| 字段 | 填写 | 说明 |
| :--- | :--- | :--- |
| Description | `MOYUYO App Backend` | 用途描述 |
| User | 选择一个专用管理员账号 | 建议创建 `moyuyo_api` 用户 |
| Permissions | **Read/Write** | MOYUYO 需要创建订单 |

### 步骤 3：保存并获取密钥

点击 **Generate API Key**，将看到：

```
Consumer Key:    ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Consumer Secret: cs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> ⚠️ **Consumer Secret 仅显示一次**，请立即复制并妥善保管（建议存入 1Password / KeePass）。

### 步骤 4：放入 `.env` 文件

```bash
# MOYUYO 后端 .env 文件（绝对不要提交到 Git）
WOO_BASE_URL=https://shop.moyuyo.com
WOO_CONSUMER_KEY=ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
WOO_CONSUMER_SECRET=cs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
WOO_WEBHOOK_SECRET=自定义一个 32 位随机串
```

确保 `.env` 已在 `.gitignore` 中：

```gitignore
# .gitignore
.env
.env.*
```

---

## 四、配置 Webhook

### 4.1 进入 Webhook 设置

`WordPress 后台 → WooCommerce → Settings → Advanced → Webhooks → Add webhook`

### 4.2 创建"订单状态变更"Webhook

| 字段 | 值 |
| :--- | :--- |
| Name | `MOYUYO-Order-Status-Update` |
| Status | **Active** |
| Topic | `Order updated` |
| API Version | **WP REST API Integration v3** |
| Delivery URL | `https://api.moyuyo.com/api/v1/webhook/woocommerce/order` |
| Secret | 与 `.env` 中的 `WOO_WEBHOOK_SECRET` 一致 |

**Topic 备选**（按需创建多个 Webhook）：
- `Order created` — 订单创建（一般不需要，MOYUYO 自己创建）
- `Order updated` — 订单更新（推荐，能捕获 processing/completed/refunded 等所有状态变化）
- `Order deleted` — 订单删除

> **最佳实践**：使用一个 `Order updated` Webhook 即可覆盖发货、完成、退款等所有状态变更，在 MOYUYO 后端根据 `status` 字段分发处理。

### 4.3 创建"商品更新"Webhook（可选）

| 字段 | 值 |
| :--- | :--- |
| Name | `MOYUYO-Product-Updated` |
| Status | Active |
| Topic | `Product updated` |
| API Version | WP REST API Integration v3 |
| Delivery URL | `https://api.moyuyo.com/api/v1/webhook/woocommerce/product` |
| Secret | 同上 |

> 商品同步已由定时任务每 10 分钟拉取，Webhook 仅作为实时通知（可选）。

### 4.4 测试 Webhook

1. 在 Webhook 列表点击对应行的 **Test Webhook** 按钮。
2. 查看 MOYUYO 后端日志是否收到请求：
   ```bash
   tail -f /var/log/moyuyo/webhook.log
   ```
3. 若失败，进入 `WooCommerce → Status → Logs → webhook-delivery` 查看响应。

---

## 五、字段映射表

### 5.1 MOYUYO 订单 → WooCommerce 订单

| MOYUYO 字段 | WooCommerce 字段 | 转换说明 |
| :--- | :--- | :--- |
| orderNo | meta_data `_moyuyo_order_no` | 用于幂等 |
| status = PENDING_PAY | status = `pending` | 实际不会出现（App 内支付） |
| status = PENDING_SHIP | status = `processing` | App 已支付待发货 |
| status = PENDING_RECEIVE | status = `shipped` + meta `_shipped_at` | 商家已发货，待用户确认 |
| status = COMPLETED | status = `completed` | 订单完成 |
| status = CANCELLED | status = `cancelled` | 用户取消 |
| status = REFUNDING | status = `refunded` | 退款中 → 已退款 |
| payChannel | payment_method | WECHAT→`wechat_pay`、ALIPAY→`alipay`、PAYPAL→`paypal`、APPLE_PAY→`apple_pay`、STRIPE→`stripe` |
| payTransactionId | transaction_id | 支付流水 |
| paidAt | date_paid | ISO8601 |
| payAmount | total | 实际支付 |
| goodsAmount | subtotal | 商品小计 |
| freight | shipping_total | 运费 |
| userId | customer_id | 0 = 游客订单 |
| items[].wooProductId | line_items[].product_id | |
| items[].wooVariationId | line_items[].variation_id | |
| items[].quantity | line_items[].quantity | |
| items[].price | line_items[].subtotal / total | |
| receiver | shipping.first_name + last_name | 拼接 |
| phone | shipping.phone / billing.phone | |
| address.detail | shipping.address_1 | 拼接完整地址 |

### 5.2 WooCommerce 订单 → MOYUYO 订单（Webhook 回流）

| WooCommerce 字段 | MOYUYO 字段 | 说明 |
| :--- | :--- | :--- |
| id | wooOrderId | 记录对应关系 |
| status = `processing` | orderStatus = PENDING_SHIP | 商家已发货（实际是"已支付"+"已标记发货"） |
| status = `completed` | orderStatus = COMPLETED | 已完成 |
| status = `refunded` | orderStatus = REFUNDED | 已退款 |
| meta_data[].`_moyuyo_order_no` | 查找订单 | 业务订单号 |
| meta_data[].`_tracking_number` | trackingNumber | 物流单号 |
| meta_data[].`_shipping_carrier` | shippingCarrier | 承运商 |

> 实际生产中，WooCommerce 的"已发货"通常通过 meta_data 中的物流信息传递，可使用 `aftership-woocommerce-tracking` 插件自动写入。

---

## 六、推荐安装的辅助插件

| 插件 | 用途 | 推荐度 |
| :--- | :--- | :---: |
| **AfterShip Tracking** | 物流单号自动同步、轨迹追踪 | ⭐⭐⭐ |
| **WooCommerce Shipment Tracking** | 写入 `_tracking_number` meta | ⭐⭐⭐ |
| **Sequential Order Numbers** | 订单号自定义格式（如 `MO{date}{seq}`） | ⭐⭐ |
| **WP All Import** | 商品批量导入 | ⭐⭐ |
| **Advanced Custom Fields** | 自定义商品字段 | ⭐ |
| **WooCommerce Subscriptions** | 订阅服务 | ⭐ |

### 物流追踪插件配置

`Plugins → Add New → 搜索 "AfterShip Tracking" → Install → Activate`

在 WooCommerce 订单详情页"发货"时，商家填写：

```
Tracking Provider: 顺丰速运
Tracking Number:    SF1234567890
```

插件会自动写入 `_aftership_tracking_number` meta，MOYUYO Webhook 接收端可读取该字段并回填到 `mo_order.tracking_number`。

---

## 七、欧美支付配套（WooCommerce 端）

> 由于本项目面向欧美市场，WooCommerce 必须安装以下支付插件。

### 7.1 必装支付插件

| 优先级 | 插件 | 作用 | 备注 |
| :---: | :--- | :--- | :--- |
| ⭐⭐⭐ | **WooCommerce Payments**（Stripe 官方） | 信用卡 / 借记卡（Visa / MasterCard / Amex / Discover）、Apple Pay、Google Pay、iDEAL、SEPA | Stripe 一站式，最稳 |
| ⭐⭐⭐ | **PayPal Payments**（官方） | PayPal、PayPal Pay Later、Venmo | 欧美用户信任度极高 |
| ⭐⭐ | **Klarna Payments** | 分期付款（欧洲主力） | 暂仅 Web 端支持 |
| ⭐⭐ | **Afterpay** | 分期付款（北美主力） | 暂仅 Web 端支持 |
| ⭐ | **Stripe Gateway** | Stripe 信用卡（如果不用 WooCommerce Payments） | 备选 |

### 7.2 安装 WooCommerce Payments（Stripe）

1. `Plugins → Add New → 搜索 "WooCommerce Payments"`
2. 点击 **Install Now** → **Activate**
3. 进入 `Payments → Settings`，点击 **Connect to Stripe**
4. 跳转到 Stripe 授权页面，使用商家 Stripe 账号登录
5. 授权后回到 WooCommerce，连接成功
6. 启用支付方式：
   - ✅ Credit card / debit card
   - ✅ Apple Pay
   - ✅ Google Pay
   - ✅ iDEAL（荷兰）
   - ✅ SEPA Direct Debit（欧元区）
   - ✅ Klarna（如已开通）
7. 开启 **Test mode** 进行沙箱测试

### 7.3 安装 PayPal Payments

1. `Plugins → Add New → 搜索 "PayPal Payments"`
2. 安装并激活
3. 进入 `Payments → Settings → PayPal`
4. 点击 **Connect to PayPal**
5. 跳转到 PayPal 授权，使用商家 PayPal Business 账号登录
6. 授权后回到 WooCommerce，连接成功
7. 启用支付方式：
   - ✅ PayPal Checkout
   - ✅ PayPal Pay Later（如可用）
   - ✅ Venmo（仅美国）
   - ✅ Pay in 4（澳大利亚 / 英国 / 美国）
8. 开启 **Sandbox mode** 进行沙箱测试

### 7.4 支付流程

```
用户在 MOYUYO App 选 PayPal / Apple Pay / 信用卡
   ↓
App 调用 MOYUYO 后端创建支付 Intent
   ↓
（方案 A：WooCommerce Checkout）
  后端调用 WooCommerce Payment Gateway → 返回支付 URL / 参数
  App 跳转到 WooCommerce Checkout 页面（或 SDK 唤起）

（方案 B：Stripe 直连，推荐）
  后端调用 Stripe API 直接创建 PaymentIntent
  App 通过 Stripe SDK（iOS PaymentSheet / Android）完成支付
  支付成功 → Webhook 通知 MOYUYO → 同步到 WooCommerce 订单
```

> **推荐方案 B**：避免跳转第三方页面，体验更顺滑，且订单同步逻辑更可控。

### 7.5 货币配置

WooCommerce 默认货币：进入 `WooCommerce → Settings → General → Currency`：

| 货币 | 适用市场 |
| :--- | :--- |
| USD ($) | 美国 |
| EUR (€) | 欧元区 |
| GBP (£) | 英国 |
| CAD (C$) | 加拿大 |
| AUD (A$) | 澳大利亚 |
| JPY (¥) | 日本（后期） |

> 若需多货币切换：安装 **WOOCS - Currency Switcher** 或 **Aelia Currency Switcher**。

### 7.6 税务配置

#### 7.6.1 欧盟 VAT

1. 安装 **TaxJar** 或 **WooCommerce Tax**
2. 进入 `WooCommerce → Settings → Tax → Standard rates`
3. 启用 **Automated taxes**
4. 录入商家 VAT 号
5. 跨境销售 ≤ €150 适用 **IOSS**（Import One-Stop Shop）

#### 7.6.2 美国 Sales Tax

1. 安装 **TaxJar**
2. 录入各州税率（Nexus states）
3. 自动计算订单税额

#### 7.6.3 英国 VAT

- 独立于欧盟 VAT，按英国税率（20% / 5% / 0%）计算
- 需在 WooCommerce Tax 中单独配置

### 7.7 退款 / 争议处理

- **Stripe Dashboard**：处理退款、争议、退款原因记录
- **PayPal Resolution Center**：PayPal 争议处理
- **Webhook 监听**：`charge.refunded`、`dispute.created`
- MOYUYO 后端收到 Webhook → 更新 `mo_refund` / `mo_order.status = REFUNDED`

### 7.8 财务对账

| 维度 | 工具 |
| :--- | :--- |
| 支付流水 | Stripe Dashboard / PayPal Reports |
| 订单对账 | MOYUYO 后台对账脚本（每日） |
| 税务报告 | Stripe Tax / TaxJar |
| 银行对账 | Stripe Payout / PayPal Withdrawal |

---

## 八、亚马逊 MCF 履约集成

### 8.1 集成架构（方案 A：WooCommerce 中转）

```
MOYUYO App → MOYUYO 后端 → WooCommerce REST API → WooCommerce 后台
                                            ↓
                              Amazon Fulfillment 插件
                                            ↓
                              亚马逊 MCF API（SP-API）
                                            ↓
                              亚马逊 FBA 仓库 → 配送到终端用户
                                            ↓
                              运单号 + 物流状态回传（插件自动）
                                            ↓
                              WooCommerce Webhook → MOYUYO 后端 → App 端
```

> 核心思路：WooCommerce 作为订单中转枢纽，通过 Amazon Fulfillment 插件自动将订单转发至亚马逊 MCF，MOYUYO 后端无需直接对接亚马逊 SP-API。

### 8.2 插件选型与安装

**推荐插件：WooCommerce Amazon Fulfillment**

| 功能 | 说明 |
| :--- | :--- |
| 自动发单 | `processing` 状态订单自动发送到 MCF |
| 库存同步 | FBA 库存自动同步到 WooCommerce 商品库存 |
| 运单回传 | 自动回传运单号与物流状态 |
| 多包裹发货 | 支持部分发货、多包裹独立运单号 |
| 多国家 | 支持 US / EU / UK 多区域 FBA 仓库 |
| 配送速度 | Standard / Expedited / Priority 三档 |

**安装步骤：**
1. `Plugins → Add New → 搜索 "WooCommerce Amazon Fulfillment"`
2. Install → Activate
3. 进入 `WooCommerce → Settings → Amazon Fulfillment`
4. 填入亚马逊凭证（Seller ID、MWS Auth Token / SP-API Refresh Token）
5. 选择 **MCF 模式**（Multi-Channel Fulfillment，非 FBA 前台订单）
6. 配置 SKU 映射 + 配送速度映射
7. 开启自动发货开关

### 8.3 SKU 映射与库存同步

**SKU 三方映射：**
```
MOYUYO 内部 SKU ↔ WooCommerce SKU ↔ 亚马逊 FNSKU
```

- 映射关系存储在 WooCommerce 商品 meta 字段 `_amazon_fulfillment_sku`
- 首次配置批量导入 CSV 映射表，后续新增商品自动映射

**库存同步链路：**
```
亚马逊 FBA（权威源）
    ↓ 每小时同步
WooCommerce 商品库存
    ↓ 每 10 分钟拉取
MOYUYO App 商品库
```

**超卖防护三层校验：**
1. MOYUYO 后端下单时 Redis 预扣库存
2. WooCommerce 库存二次校验
3. 亚马逊 MCF 创建时最终校验
任一层库存不足则取消订单并自动退款。

### 8.4 配送速度映射

| 亚马逊 MCF 速度 | 承诺时效 | WooCommerce 运费方式 | App 端展示 |
| :--- | :--- | :--- | :--- |
| Standard | 3-5 工作日 | Standard Shipping | 标准配送（免费） |
| Expedited | 2 工作日 | Express Shipping | 加急配送（$9.99） |
| Priority | 次日达 | Priority Shipping | 次日达（$19.99） |

- 免邮门槛：订单 ≥ $49 自动升级 Expedited
- 偏远地区（HI / AK / PO Box）仅支持 Standard

### 8.5 订单履约全流程

1. 用户 App 下单支付 → MOYUYO 创建订单
2. MOYUYO 同步至 WooCommerce（`status=processing`, `set_paid=true`）
3. Amazon Fulfillment 插件检测到 processing 订单
4. 插件调用 MCF API `create-fulfillment-order`
5. 亚马逊 FBA 仓库挑拣、包装、发货
6. 回传运单号 + 物流状态到插件
7. 插件更新 WooCommerce 订单为 `completed` + 写入运单号
8. WooCommerce Webhook `order.completed` 回流至 MOYUYO
9. MOYUYO 更新 `mo_order` 状态 + 运单号
10. App 端展示物流追踪（对接 AfterShip / 17Track）

### 8.6 异常处理

| 异常场景 | 处理策略 |
| :--- | :--- |
| MCF 创建失败（SKU 不存在） | 插件标记 `failed`，Webhook 回流触发客服工单 |
| FBA 库存不足 | MCF 拒绝履约，插件回写库存为 0，App 端商品自动下架 |
| MCF 部分发货 | 多包裹独立运单号，全部回传至 WooCommerce |
| MCF 发货超时（48h） | 插件告警运营，MOYUYO Push 通知用户延迟 |
| 同步中断 | 自动重试 3 次，失败标记 `failed`，人工介入 |
| 运单回传延迟 | MOYUYO 每 30 分钟主动查询 WooCommerce 订单状态补齐 |
| MCF 退货 | 退货至 FBA 仓库，插件同步退货状态，触发退款 |

### 8.7 费用与对账

- **MCF 费用构成**：按件数 + 重量 + 配送速度计费，从卖家账户自动扣除
- **对账机制**：
  - 每日拉取亚马逊 MCF Fulfillment Report
  - 与 `mo_order.mcf_fulfillment_id` 逐笔对账
  - 费用差异 > $1 自动工单告警
- **成本看板**：后台展示 MCF 履约费用占比、单均履约成本、速度分布

### 8.8 多仓多区域配置

- **美国**：US-East / US-West FBA 仓库
- **欧洲**：DE / FR / IT / ES FBA 仓库（EU Pan-European）
- **英国**：UK FBA 仓库（Brexit 独立清关）
- **路由规则**：按收货地址国家自动路由最近 FBA 仓库
- **跨区域限制**：液体洗护类不支持跨区配送，在插件 SKU 配置中标记

---

## 九、测试与验证

### 9.1 验证 API 连通性

```bash
# 在 MOYUYO 服务器上执行
curl -u "ck_xxxx:cs_xxxx" \
  "https://shop.moyuyo.com/wp-json/wc/v3/system_status"
```

**预期输出**（简化）：
```json
{
  "environment": {
    "site_url": "https://shop.moyuyo.com",
    "wp_version": "6.4",
    "wc_version": "8.5"
  }
}
```

### 9.2 验证创建订单

```bash
curl -X POST \
  -u "ck_xxxx:cs_xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "processing",
    "set_paid": true,
    "source": "moyuyo_app",
    "currency": "USD",
    "billing": {
      "first_name": "测试",
      "last_name": "用户",
      "phone": "13800138000",
      "address_1": "科技园路 1 号",
      "city": "深圳市",
      "state": "广东省",
      "country": "CN"
    },
    "line_items": [
      { "product_id": 1001, "quantity": 1 }
    ],
    "meta_data": [
      { "key": "_moyuyo_order_no", "value": "MO_TEST_001" }
    ]
  }' \
  "https://shop.moyuyo.com/wp-json/wc/v3/orders"
```

**预期返回** `201 Created`，包含 `id` 字段。

### 9.3 验证 Webhook 签名

WooCommerce 的 Webhook 签名 = Base64(HMAC-SHA256(rawBody, webhookSecret))

```python
import hmac, hashlib, base64

def verify(raw_body: bytes, signature: str, secret: str) -> bool:
    digest = hmac.new(
        secret.encode(), raw_body, hashlib.sha256
    ).digest()
    expected = base64.b64encode(digest).decode()
    return hmac.compare_digest(expected, signature)
```

Java 版见需求文档 12.4 章节。

### 9.4 端到端联调清单

| # | 验证项 | 期望结果 |
| :---: | :--- | :--- |
| 1 | App 下单 → MOYUYO 创建订单 | DB 出现订单，状态 PENDING_PAY |
| 2 | App 选 Apple Pay / Google Pay / 信用卡 / PayPal 完成支付 | 订单变为 PENDING_SHIP，触发 Woo 推送 |
| 3 | MOYUYO 调 Woo API | Woo 后台订单列表出现该订单 |
| 4 | 商家在 Woo 后台发货 | 写入 tracking 信息 |
| 5 | Webhook 推送至 MOYUYO | MOYUYO 订单记录物流单号 |
| 6 | 用户在 App 查订单 | 看到"已发货 + 物流单号" |
| 7 | 用户确认收货 | 订单变 COMPLETED |
| 8 | Woo 中退款 | Webhook 通知，订单变 REFUNDED |
| 9 | 商品库存为 0 | App 端 30 分钟内下架 |
| 10 | 同步失败 5 次 | 通知运营 + 邮件告警 |
| 11 | Stripe 沙箱测试（卡号 4242 4242 4242 4242） | 支付成功 |
| 12 | Stripe Webhook → MOYUYO 收到 `payment_intent.succeeded` | 订单状态更新 |
| 13 | PayPal 沙箱测试 | 支付成功 |
| 14 | 多币种（USD / EUR / GBP） | 订单金额、税额正确 |
| 15 | EU VAT 计算（VAT 23%） | 订单包含 VAT 金额 |

---

## 十、常见问题 FAQ

### Q1：401 Unauthorized
- 检查 Consumer Key/Secret 是否正确。
- 检查 URL 是否使用 HTTPS。
- 检查 `.htaccess` 是否有 IP 限制。

### Q2：404 Not Found
- 永久链接未设置为 Post name。
- WordPress 伪静态未生效（NGINX 需配置 rewrite 规则）。

### Q3：Webhook 一直不触发
- 检查 WP Cron 是否正常：`wp cron event list`。
- 检查 Webhook 状态是否为 Active。
- 检查 Delivery URL 是否能从公网访问。
- 在 WooCommerce → Status → Logs → webhook-delivery 查看失败原因。

### Q4：订单同步出现时区偏差
- WordPress 时区统一设置为 `UTC` 或目标市场时区（如 `America/New_York`）
- MOYUYO 服务端时区也保持一致。

### Q5：商品图片无法显示
- WooCommerce 媒体库权限需放开。
- 图片域名需加入 MOYUYO App 的网络白名单。

### Q6：库存超卖
- 启用 WooCommerce"启用库存管理"。
- 在商品编辑 → Inventory 勾选 "Manage stock"。
- MOYUYO 下单时增加库存二次校验（在 Redis 中预扣）。

### Q7：签名校验失败
- 确认 `rawBody` 是**原始字节**，不要提前 `JSON.parse`。
- 确认 Secret 与 WooCommerce 后台一致。
- 注意中文字符编码使用 UTF-8。

---

## 十一、安全建议

1. **专用 API 账号**：不要使用 `admin` 账号，给 MOYUYO 单独创建一个 `moyuyo_api` 管理员账号。
2. **IP 白名单**：通过 `.htaccess` 或 NGINX 限制 WooCommerce 后台访问 IP。
3. **Web 应用防火墙**：部署 WAF（如 Cloudflare、Wordfence）。
4. **API Key 定期轮换**：建议每 90 天轮换一次。
5. **HTTPS 强制**：WordPress 后台强制 SSL（`wp-config.php` 加 `FORCE_SSL_ADMIN`）。
6. **日志审计**：开启 WooCommerce 日志记录功能，定期审查 `wp-content/debug.log`。

---

## 十二、上线 Checklist

- [ ] WooCommerce 永久链接已设为 Post name
- [ ] HTTPS 已启用且证书有效
- [ ] Consumer Key/Secret 已生成并入库 `.env`
- [ ] Webhook 已创建且测试通过
- [ ] 物流追踪插件已安装并配置
- [ ] API 连通性测试通过
- [ ] Webhook 签名校验代码已部署
- [ ] 商品定时同步任务已启动
- [ ] 订单重试补偿任务已启动
- [ ] 监控告警已配置（同步失败率、API 响应时间）
- [ ] 运营人员培训完成
