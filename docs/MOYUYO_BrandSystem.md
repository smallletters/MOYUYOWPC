# MOYUYO 品牌系统（Brand System）

> 配套设计稿与执行规范。所有 UI 设计、UI 代码、营销物料、IP 衍生品需遵循本规范。
> 版本：V1.0 · 2026-07-03 · 来源：MOYUYO Atelier 品牌手册

---

## 一、品牌定位

### 1.1 一句话定位
**MOYUYO ATELIER** —— A Modern Luxury Pet Lifestyle House.

### 1.2 中文定位
陪伴每一次共同旅程。  
（英文主 Slogan：**Every Journey Together.**）

### 1.3 品牌调性
- **奢华而克制**：高品质、不浮夸；用"工艺感"而非"金碧辉煌"表达奢华。
- **温暖而专业**：是"懂宠物"的伙伴，而不是"卖货的"商家。
- **东方美学融合现代设计**：色调、字体、版式均体现"东方极简"。

### 1.4 5 大品牌价值（用于营销文案 / UI 标签 / 选品标准）

| 价值 | 英文 | UI 图标 | 含义 |
| :---: | :---: | :---: | --- |
| 探索 | EXPLORATION | 山峰 | 鼓励带宠走出家门，看更大的世界 |
| 陪伴 | COMPANION | 心+爪 | 宠物与人的双向情感连接 |
| 安全 | SAFETY | 盾牌 | 成分 / 工艺 / 材质的安心感 |
| 生活方式 | LIFESTYLE | 杯子 | 宠物用品融入日常审美 |
| 品质 | QUALITY | 钻石 | 选品与做工的极致追求 |

---

## 二、品牌色板（Brand Color Palette）

> 所有色值已收录在 `mobile/theme/BrandColors.kt`（Android）与 `MOYUYO/Theme/BrandColors.swift`（iOS），设计师在 Figma 中可绑定同名 token。

| 名称 | 角色 | HEX | RGB | CMYK 近似 | 使用建议 |
| :---: | :--- | :---: | :---: | :---: | --- |
| **Sand Gold** | 主色 Primary | `#DBC98A` | 219, 201, 138 | 15, 15, 50, 0 | CTA、Logo 主色、卡片高亮 |
| **Linen** | 背景 Background | `#F6F2EE` | 246, 242, 238 | 3, 2, 5, 0 | 全局背景、次级卡片底色 |
| **Charcoal** | 文字 Text | `#2E2B29` | 46, 43, 41 | 60, 60, 60, 70 | 主文字、标题 |
| **Antique Brass** | 强调 Accent | `#B38A5A` | 179, 138, 90 | 25, 40, 70, 5 | 链接、徽章、辅助按钮 |
| **Sage Mist** | 状态-成功 Success | `#ABB9AD` | 171, 185, 173 | 30, 10, 25, 0 | "已下单"、"安全"标签 |
| **Dusty Rose** | 状态-情感 Warm | `#D9B4B0` | 217, 180, 176 | 8, 28, 18, 0 | 营销节日、爱心提示 |

> **历史说明**：原手册写作"ABBOAD"，按 sRGB 重新计算为 `#ABB9AD`（171,185,173）；本规范统一使用 `#ABB9AD`，RGB 字段已同步修正。设计师以 Figma 中 token 为准。

### 2.1 颜色对比度（WCAG）

| 前景 | 背景 | 对比度 | 等级 |
| :---: | :---: | :---: | :---: |
| Charcoal `#2E2B29` | Linen `#F6F2EE` | 13.4:1 | AAA |
| Charcoal `#2E2B29` | Sand Gold `#DBC98A` | 7.6:1 | AAA |
| Antique Brass `#B38A5A` | Linen `#F6F2EE` | 3.6:1 | AA（大字体） |
| Sage Mist `#ABB9AD` | Charcoal `#2E2B29` | 6.1:1 | AAA |
| White `#FFFFFF` | Sand Gold `#DBC98A` | 1.9:1 | ✗（不可用于正文） |

> 凡正文与按钮文字必须满足 WCAG AA；如 Antique Brass 用于小字需加粗 ≥ 600。

### 2.2 暗黑模式

| Light 模式 | Dark 模式 | 说明 |
| :--- | :--- | --- |
| Linen `#F6F2EE` 背景 | Charcoal `#2E2B29` 背景 |  |
| Charcoal `#2E2B29` 文字 | Linen `#F6F2EE` 文字 |  |
| Sand Gold `#DBC98A` 强调 | Sand Gold `#DBC98A` 强调（不变） | 保留品牌色一致性 |
| Dusty Rose `#D9B4B0` | Dusty Rose `#D9B4B0`（不变） |  |

---

## 三、字体系统（Typography）

### 3.1 字体家族
| 平台 | 主字体 | 备选字体 | 数字字体 |
| :---: | :--- | :--- | :--- |
| iOS | **Playfair Display**（标题） + **SF Pro Text**（正文） | Noto Serif / PingFang TC | SF Mono |
| Android | **Playfair Display**（标题） + **Inter**（正文） | Noto Serif / Source Han Sans | Roboto Mono |
| Web | Playfair Display + Inter | Noto Sans | Roboto Mono |

> 标题使用 Playfair Display（衬线、奢华感）；正文使用无衬线（可读性优先）。

### 3.2 Type Scale

| 级别 | 用途 | 字号 / iOS pt | 字重 | 行高 |
| :---: | :--- | :---: | :---: | :---: |
| Display | 首屏 Slogan | 32 | Playfair Bold 700 | 1.2 |
| Headline 1 | 一级标题 | 24 | Playfair SemiBold 600 | 1.3 |
| Headline 2 | 二级标题 | 20 | Playfair Medium 500 | 1.3 |
| Title 1 | 卡片标题 | 18 | Inter SemiBold 600 | 1.4 |
| Title 2 | 小节标题 | 16 | Inter SemiBold 600 | 1.4 |
| Body | 正文 | 16 | Inter Regular 400 | 1.5 |
| Body Small | 辅助 | 14 | Inter Regular 400 | 1.5 |
| Label | 按钮 / 徽章 | 14 | Inter Medium 500 | 1.3 |
| Caption | 注释 | 12 | Inter Regular 400 | 1.4 |

---

## 四、Logo 规范

### 4.1 三种 Logo 形式

| 类型 | 用途 | 最小宽度 |
| :--- | :--- | :---: |
| **PRIMARY LOGO** | "MOYUYO" + ™ 标 + Atelier 字样 | 96 px |
| **MONOGRAM** | "M Y" 组合字母 | 32 px |
| **EMBLEM** | 圆形徽章 + 宠物剪影 + MOYUYO 字样 | 48 px |

### 4.2 安全区与最小尺寸
- Logo 周围留白 ≥ Logo 高度的 50%。
- 数字屏幕最小展示尺寸：Primary 96 px / Monogram 24 px / Emblem 40 px。
- 单色版本：纯 Charcoal `#2E2B29` 或纯 Linen `#F6F2EE`；金色版本仅用于品牌高端页面。

### 4.3 错误使用
- 不可拉伸、旋转、改变颜色（在 4.2 规定之外）。
- 不可添加阴影 / 描边 / 渐变。
- 不可放置在低对比度背景上（参考 §2.1）。

---

## 五、四大产品线（Product Collection）

> 与数据库 `mo_collection` 表一一对应；每条产品线下有专属 IP 角色。

| 编号 | 产品线 | 英文 | 目标场景 | 关键品类 |
| :---: | :--- | :--- | :--- | :--- |
| 1 | **出行装备系列** | Gear Collection | 户外 / 旅行 | 牵引绳、背包、推车、饮水器 |
| 2 | **洗护护理系列** | Care Collection | 日常护理 | 沐浴露、护毛素、牙刷、指甲剪 |
| 3 | **玩具娱乐系列** | Play Collection | 室内 / 互动 | 益智玩具、咬绳、逗猫棒 |
| 4 | **家居与时尚系列** | Home & Atelier Collection | 家居 / 穿搭 | 窝垫、餐具、服饰、配件 |

> 选品标准：所有商品必须对应 1 个产品线；可多选但不超过 2 个。

---

## 六、四大 IP 角色（Brand IP Character）

> 4 个 IP 是品牌叙事与社区文化的核心。商品 / 内容 / 营销活动均按 IP 关联。

| IP | 英文名 | 中文名 | 物种 | 性格 | 关联产品线 | 设计要点 |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| 🐕 | **MILO** | 探险家 | 灵缇犬 | 自由 / 好奇 | 出行装备系列 | 米黄 + 棕褐，背双肩包，戴 MOYUYO 项圈 |
| 🐱 | **LUNA** | 策展家 | 英短金渐层 | 优雅 / 精致 | 洗护护理系列 | 暖金色 + 柔白，洗护瓶罐围在身边 |
| 🐕 | **ATLAS** | 守护者 | 杜宾犬 | 勇敢 / 守护 | 玩具娱乐系列 | 黑棕 + 古铜金，戴战术胸背带 |
| 🐱 | **OLIVE** | 艺术家 | 缅因猫 | 文艺 / 慵懒 | 家居与时尚系列 | 灰绿 + 藕粉，画家围裙，背景藤蔓 |

### 6.1 IP 资产格式
- **2D Illustration**：用于商品详情页、宣传图、IP 故事长图。
- **3D Character Model**：用于启动屏、加载占位、AR 试穿。
- **Character Turnaround (3D)**：用于设计师/3D 建模师参考；交付需 4 视角（前/侧/背/3/4）。
- **IP Application**：IP 衍生物料（帆布包、钥匙扣、包装盒、手机壳等）需 1:1 对照设计稿。

### 6.2 IP 故事线（品牌叙事骨架）

> 注意：本故事线**顺序**与 §5 产品线列表（以及 V5 migration `mo_brand_ip.sort`）不同：列表按"出行→洗护→玩具→家居"陈列，故事线按"探险→守护→艺术→精致"叙事；前端在"品牌故事"频道按故事线呈现，在"全部 IP"频道按 sort 字段呈现。

```
MILO  探险  →  ATLAS  守护  →  OLIVE  艺术  →  LUNA  精致
(出行)        (玩耍)        (家居)        (洗护)
```

> App 内"品牌故事"频道按此顺序展示 4 个 IP；社区可由用户 UGC 与 IP 互动。

---

## 七、UI 落地规范

### 7.1 主色映射
- **Primary（CTA、链接、选中态）**：Sand Gold `#DBC98A`
- **Background**：Linen `#F6F2EE`
- **Surface / Card**：Linen `#F6F2EE`（elevated card：纯白 `#FFFFFF`）
- **On-Primary**：Charcoal `#2E2B29`
- **On-Background**：Charcoal `#2E2B29`
- **Secondary / Accent**：Antique Brass `#B38A5A`
- **Tertiary（情感）**：Dusty Rose `#D9B4B0`
- **Success**：Sage Mist `#ABB9AD`
- **Error**：保留 Material/Cupertino 系统色 `#B00020` / `.systemRed`，避免与品牌色混淆

### 7.2 圆角 / 阴影
- 圆角：卡片 12 dp、按钮 24 dp（pill 形）、输入框 8 dp。
- 阴影：极轻 `0 1 3 rgba(46,43,41,0.06)`；避免大投影。

### 7.3 Loading / 空状态
- 使用 IP 角色的 2D 灰度插画作为占位图（不要用纯文字）。
- 4 个角色按页面分配（如"我的订单空态"用 OLIVE，"我的收藏空态"用 MILO）。

---

## 八、品牌资产清单（设计师交付）

| 资产 | 格式 | 数量 | 存放路径 |
| :--- | :--- | :---: | :--- |
| Primary Logo | SVG + PNG | 3 尺寸 | `assets/brand/logo/` |
| Monogram | SVG + PNG | 3 尺寸 | `assets/brand/logo/` |
| Emblem | SVG + PNG | 3 尺寸 | `assets/brand/logo/` |
| 色板 | JSON + .fig | 6 色 | `assets/brand/color.json` |
| IP 2D 插画 | PNG @1x/2x/3x | 4 角色 × 8 表情 | `assets/ip/2d/` |
| IP 3D 模型 | GLB + USDZ | 4 角色 | `assets/ip/3d/` |
| IP Turnaround | PSD / PNG 序列 | 4 角色 × 4 视角 | `assets/ip/turnaround/` |
| 包装 / 衍生品 | AI / PSD | ≥ 8 SKU | `assets/derivation/` |

---

## 九、品牌应用禁忌

1. **禁止混用三套 Logo**（同屏只允许 1 种）。
2. **禁止更改品牌色 HEX**（设计稿与代码必须用 token，不得写死颜色值）。
3. **禁止给 IP 加表情包文字**（保持"奢华克制"调性）。
4. **禁止使用低分辨率 IP 图片**（社区上传内容走 OSS 自动压缩 + 边缘检测）。
5. **禁止打折季用红色**（用 Dusty Rose `#D9B4B0` 替代，保留品牌一致性）。

---

## 十、版本与维护

| 版本 | 日期 | 变更 | 维护人 |
| :---: | :---: | --- | :---: |
| V1.0 | 2026-07-03 | 初版（基于 MOYUYO Atelier 品牌手册） | 品牌组 |
