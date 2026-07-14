# MOYUYO 宠物用品商城 App

> **A Modern Luxury Pet Lifestyle House.** 陪伴每一次共同旅程（Every Journey Together.）
> 品牌：MOYUYO ATELIER · 配套后端：Spring Boot 3 + Java 17 · 客户端：Android (Kotlin + Compose) / iOS (Swift + SwiftUI) · 履约：WooCommerce。

---

## 🚀 快速开始

**环境要求**
- JDK 17+（Spring Boot 3）
- Node.js 18+（前端工具链）
- MySQL 8.0+（数据库）
- Redis 7.x（缓存 + Token 黑名单）
- Maven 3.8+（后端构建）
- Android Studio Hedgehog+（Android 端）
- Xcode 15+（iOS 端）

**后端启动**
```bash
# 克隆仓库
git clone https://github.com/moyuyo/moyuyo-app.git
cd moyuyo-app

# 配置数据库（Flyway 自动迁移 V1~V6）
# 修改 application.yml 中的数据库连接信息

# 启动后端服务
mvn spring-boot:run
```
服务启动后访问：http://localhost:8080/actuator/health

**数据库迁移**
- 使用 Flyway 自动执行 db-migration/ 目录下的 V1~V6 迁移脚本
- 首次启动自动建表，无需手动执行 SQL

**移动端启动**
- Android：用 Android Studio 打开 android/ 目录，Sync Gradle 后 Run
- iOS：用 Xcode 打开 ios/MOYUYO.xcodeproj，配置签名后 Run

---

## 📁 目录结构
```
MOYUYO/
├── db-migration/              # 数据库迁移脚本（Flyway）
│   ├── V1__init_user_and_oauth.sql
│   ├── V2__init_business_tables.sql
│   ├── V3__seed_data.sql
│   ├── V4__indexes_and_partition.sql
│   ├── V5__init_brand_and_ip.sql
│   └── V6__init_pet_hub.sql
├── backend/                   # Spring Boot 后端（如有）
├── android/                   # Android 端（Kotlin + Compose）
├── ios/                       # iOS 端（Swift + SwiftUI）
├── 需求文档.md                  # 核心产品需求文档
├── 接口文档.md                  # REST API 接口定义
├── 数据库设计.md                # 数据库表结构设计
├── 移动端接入指南.md             # 移动端开发接入文档
├── 后端SSO模块.md               # 后端 SSO 认证模块设计
├── WooCommerce对接指南.md       # WooCommerce 集成对接文档
├── MOYUYO_BrandSystem.md       # 品牌系统设计规范
└── README.md                   # 本文件
```

---

## 一、文档目录

| 序号 | 文档 | 用途 | 阅读顺序 |
| :---: | --- | :--- | :---: |
| 0 | [MOYUYO_BrandSystem.md](./MOYUYO_BrandSystem.md) | **品牌系统：定位 / 色板 / 字体 / Logo / 4 大产品线 / 4 大 IP** | ★ 必读 |
| 1 | [需求文档.md](./需求文档.md) | 产品需求、功能清单、业务规则、合规要求 | ① |
| 2 | [接口文档.md](./接口文档.md) | 92 个 REST 接口的请求/响应、错误码 | ② |
| 3 | [数据库设计.md](./数据库设计.md) | ER 图、表结构、索引、GDPR 匿名化、品牌 IP 表 | ③ |
| 4 | [后端SSO模块.md](./后端SSO模块.md) | 登录、OAuth、2FA、GDPR 的 Java 实现 | ④ |
| 5 | [WooCommerce对接指南.md](./WooCommerce对接指南.md) | 订单同步、Webhook、Stripe / PayPal 接入 | ⑤ |
| 6 | [移动端接入指南.md](./移动端接入指南.md) | Android / iOS 工程结构、关键页面、SDK 集成、品牌 Theme | ⑥ |
| 7 | [db-migration/](./db-migration/README.md) | Flyway 迁移脚本（V1～V6） | 与 ③④ 并行 |

---

## 二、品牌速览

| 维度 | 内容 |
| --- | --- |
| 品牌名 | **MOYUYO ATELIER** |
| Slogan | Every Journey Together. / 陪伴每一次共同旅程 |
| 调性 | Modern Luxury（现代奢华）· 温暖 · 克制 · 东方美学 |
| 5 大价值 | EXPLORATION · COMPANION · SAFETY · LIFESTYLE · QUALITY |
| 4 大产品线 | Gear / Care / Play / Home & Atelier |
| 4 大 IP | 🐕 MILO（探险家）· 🐱 LUNA（策展家）· 🐕 ATLAS（守护者）· 🐱 OLIVE（艺术家） |
| 主色 | Sand Gold `#DBC98A` |
| 背景 | Linen `#F6F2EE` |
| 文字 | Charcoal `#2E2B29` |
| 强调 | Antique Brass `#B38A5A` |
| 成功 | Sage Mist `#ABB9AD` |
| 情感 | Dusty Rose `#D9B4B0` |
| 标题字体 | Playfair Display（衬线） |
| 正文字体 | Inter（Android）/ SF Pro Text（iOS） |

> 完整规范见 [MOYUYO_BrandSystem.md](./MOYUYO_BrandSystem.md)。

---

## 三、阅读路径建议

### 3.1 产品 / 项目经理
```
MOYUYO_BrandSystem → 需求文档 → 接口文档（功能视角）→ WooCommerce 对接指南
```

### 3.2 UI / 设计师
```
MOYUYO_BrandSystem（必读）→ 需求文档 §1.3 / §7.1 → 移动端接入指南 §5.1（Token）
```

### 3.3 后端工程师
```
MOYUYO_BrandSystem §5/§6 → 需求文档 → 接口文档 §4.0 / §9 → 数据库设计 §2.1.7～§2.1.9 →
后端 SSO 模块 → db-migration（V5 含 IP 表）
```

### 3.4 Android 工程师
```
MOYUYO_BrandSystem §2/§3 → 移动端接入指南 §5.1.1（Compose Theme）→
需求文档 §3 → 接口文档 §4.0 / §9 → 移动端接入指南 第三章
```

### 3.5 iOS 工程师
```
MOYUYO_BrandSystem §2/§3 → 移动端接入指南 §5.1.2（SwiftUI Theme）→
需求文档 §3 → 接口文档 §4.0 / §9 → 移动端接入指南 第四章
```

### 3.6 运维 / DBA
```
MOYUYO_BrandSystem（IP 资产）→ 数据库设计 §2.1.7～§2.1.9 → db-migration V5
WooCommerce 对接指南 §6 → 移动端接入指南 §7
```

---

## 四、文档间交叉引用

| 主题 | 主文档 | 关联 |
| --- | --- | --- |
| 品牌定位 / Slogan | 品牌系统 §1 / 需求文档 §1.2 | 移动端 §5.1 |
| 品牌色板 | 品牌系统 §2 | 需求文档 §7.1、移动端 §5.1、数据库设计 §2.1.8（color_hint） |
| 4 大产品线 | 品牌系统 §5 | 数据库设计 §2.1.7、接口文档 §4.0.1 |
| 4 大 IP | 品牌系统 §6 | 数据库设计 §2.1.8、接口文档 §4.0.2～§4.0.4、需求文档 §1.3.1 |
| 字体系统 | 品牌系统 §3 | 移动端 §5.1.1（Type.kt） / §5.1.2（MoyuyoFont.swift） |
| 用户登录 | 需求文档 §3.1.1 | 接口文档 §2.1～§2.5、后端 SSO §1～§7、移动端 §3.9 / §4.9 |
| 2FA | 需求文档 §3.1.4 | 接口文档 §2.6、后端 SSO §5、移动端 §3.9.6 / §4.9.7 |
| GDPR 注销 | 需求文档 §4.6.1 | 接口文档 §2.17、后端 SSO §9、数据库设计 §6 |
| 同意管理 | 需求文档 §4.6.3 | 接口文档 §2.15、后端 SSO §9.5、数据库设计 §5、移动端 §3.9.8 / §4.9.9 |
| 支付方式 | 需求文档 §3.1.6 | WooCommerce 对接指南 §6、接口文档 §7、后端 SSO §10 |
| 订单状态机 | 需求文档 §3.4 | WooCommerce 对接指南 §5 字段映射、接口文档 §6 |
| 用户表字段 | 数据库设计 §2.1 | 需求文档 §3.1.1、接口文档 §2.1、后端 SSO §1、db-migration V1 |
| WooCommerce Webhook | WooCommerce 对接指南 §4 | 需求文档 §3.4、接口文档 §12.3 |

---

## 五、版本与变更记录

| 日期 | 版本 | 变更 | 涉及文档 |
| :---: | :---: | --- | --- |
| 2026-07-01 | V1.0.0 | 初始版本（含中国/欧美两套注册登录方式） | 全部 |
| 2026-07-02 | V1.1.0 | 调整为欧美市场主版本：Apple/Google/Facebook/Email + 2FA + GDPR | 需求文档 §3.1.1 / §4.6 / §8，接口文档 §2，数据库设计 §3 / §5 / §6，后端 SSO，移动端 §3.9 / §4.9 / CheckList |
| 2026-07-02 | V1.1.1 | 后端补全 EmailAuth / TwoFactor / MagicLink / Gdpr Controller | 后端 SSO §14～§16 |
| 2026-07-02 | V1.1.2 | 移动端补全 2FA 启用 / 账号注销 / 同意管理 三页双端实现 | 移动端 §3.9.6～§3.9.8 / §4.9.7～§4.9.9 |
| 2026-07-03 | V1.1.3 | 文档索引导航 README.md | 本文件 |
| 2026-07-03 | V1.2.0 | **品牌系统接入**：新增 MOYUYO_BrandSystem.md + db-migration V5（4 大产品线 / 4 大 IP 表） + 接口 §4.0（5 个 IP 接口） + 移动端 §5.1 重写 | 品牌系统 / 需求 / 接口 / 数据库 / 移动端 / README |

---

## 六、欧美市场合规要点速查

> 完整内容见 [需求文档 §4.6](./需求文档.md)。

| 法规 | 关键要求 | 实现位置 |
| --- | --- | --- |
| GDPR（欧盟） | 数据访问、删除、撤回同意 | 接口文档 §2.15 / §2.16 / §2.17，后端 SSO §9 |
| CCPA（加州） | "Do Not Sell" 入口、≤12 月龄数据清除 | 数据库设计 §6.3 |
| COPPA（美国儿童） | < 13 岁禁止注册、需家长同意 | 需求文档 §3.1.1 |
| App Store 4.8 | 集成 Sign in with Apple | 移动端 §3.9.1 / §4.9.1 |
| Apple Privacy Manifest | 必需数据 + 追踪声明 | 移动端 §7 |
| Google Play 数据安全 | 数据收集与分享声明 | 移动端 §7 |
| PCI DSS | 支付卡号不落本地 | WooCommerce 对接指南 §6 |

---

## 七、关键术语对照表

| 中文 | 英文 | 说明 |
| --- | --- | --- |
| 用户 | User | 唯一标识 userId（雪花 ID） |
| 商品 | Product | SKU 维度管理 |
| 产品线 | Collection | Gear / Care / Play / Home & Atelier |
| IP 角色 | Brand IP | MILO / LUNA / ATLAS / OLIVE |
| 订单 | Order | 与 WooCommerce Order 双向同步 |
| 备货 | Processing | WooCommerce 状态机 |
| 取消 | Cancelled | 用户主动 / 商家拒收 |
| 退款中 | Refunding | WooCommerce Refunded 触发 |
| 注销待恢复 | Pending Deletion | 30 天冷静期 |
| 已注销 | Erased | 数据已物理删除 / 匿名化 |
| 临时令牌 | tempToken | 仅用于 2FA 流程，5 分钟有效 |
| 信任设备 | Trusted Device | 30 天内跳过 2FA |

---

## 八、联系与协作

| 角色 | 负责模块 |
| --- | --- |
| 品牌组 | MOYUYO_BrandSystem（色板 / 字体 / Logo / IP 资产） |
| 产品 | 需求文档 §1～§9 |
| UI / 设计 | 品牌系统 §2/§3/§6/§7、需求文档 §7 |
| 后端 | 接口文档、数据库设计、后端 SSO、WooCommerce 对接 |
| Android | 移动端接入指南 第三章、§5.1.1 Theme |
| iOS | 移动端接入指南 第四章、§5.1.2 Theme |
| DBA / 运维 | 数据库设计、db-migration（V1～V6）、部署脚本 |
| QA | 上线 Checklist（移动端 §7 + WooCommerce §7.4） |

---

**版权与许可**：本文档仅供 MOYUYO 项目组内部使用，未经允许不得外传。
