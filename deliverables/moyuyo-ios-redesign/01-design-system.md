# MOYUYO ATELIER · 移动端 5 屏 iOS 重设计 · 设计系统与组件规范

> **作者**：设计系统专家 彩格调（Cai） · **版本** v1.0（定稿） · **日期** 2026-11-19
> **上游输入**：`00-requirement-summary.md`（唯一输入契约） + `moyuyo-app/src/styles/variables.scss`（生产事实源）
> **下游**：Phase 3 原型构建 → Phase 4 质量审查
> **本轮范围**：5 屏浅色模式。深色模式**本轮不做**，但 token 表已按 D5 预留完整槽位（见 §3.8）。
> **本文不含 HTML/CSS 代码**，只给**可照抄的规格**；唯二例外是 §3.1 的 CSS 变量块（token 本体，需直接粘进 `variables.scss`）。

---

## 目录

- [§0 定稿摘要（先看这 10 条）](#0-定稿摘要先看这-10-条)
- [§1 设计系统选型说明](#1-设计系统选型说明)
- [§2 品牌提取协议 · brand-spec](#2-品牌提取协议--brand-spec)
- [§3 完整设计令牌表](#3-完整设计令牌表)
  - [3.1 颜色](#31-颜色浅色全套含审核定稿)
  - [3.2 字号阶梯](#32-字号阶梯)
  - [3.3 几何量](#33-几何量)
  - [3.4 动效](#34-动效)
  - [3.5 安全区](#35-安全区)
  - [3.6 令牌合并与废弃表](#36-令牌合并与废弃表现状--唯一来源)
  - [3.7 深色模式槽位预留](#37-深色模式槽位预留本轮不填值)
  - [3.8 CSS 变量落地块](#38-css-变量落地块可直接覆盖-variablesscss)
- [§4 组件规范](#4-组件规范)
- [§5 图标策略](#5-图标策略)
- [§6 落地映射表](#6-落地映射表)
- [§7 素材清单与裁切规范](#7-素材清单与裁切规范)
- [§8 定稿裁决记录（与需求摘要的冲突）](#8-定稿裁决记录与需求摘要的冲突)

---

## §0 定稿摘要（先看这 10 条）

| # | 结论 | 依据 |
|---|------|------|
| 1 | **金色只做"面"与"线"**：`--color-primary` 仅用于填充/描边/图标底/评分星/选中指示条/唯一实心 CTA；**任何金色文字一律 `--ink-gold` #6E5C30**（对白 6.49:1，对 Linen 5.83:1） | D1 决策 + WCAG 实测 |
| 2 | **金色实心块内只能放炭色** `--on-primary` #2E2B29（8.52:1）。**禁止** `--ink-gold` 落在金底上（仅 3.93:1，不达标） | 实测修正 |
| 3 | **审核发现 6 处需求摘要数值/口径错误**，本文已逐条定稿并留痕（见 §8） | 实测 |
| 4 | **字号 11 档保留**，但 Caption 2 = 11pt **降级为"纯数字/徽标专用"**，承载信息的最小字号 = **Footnote 13pt**；`rpx` 换算基准按 393pt 主机修正为 1pt = 1.908rpx | D2 审核 |
| 5 | **TabBar 标签 11pt**（非需求摘要的 10pt），以满足 G4「全站最小字号 ≥ 11pt」 | 冲突裁决 |
| 6 | **阴影只有 2 档**：`--shadow-card` / `--shadow-float`。D3 的会员卡暖色阴影取消 | D3 + 硬约束 |
| 7 | **Pet Hub 浮层用"深色玻璃"** `rgba(46,43,41,.32)`，**不用**需求摘要的白色玻璃 —— 白色玻璃在照片上白字仅 2.39:1，实测不达标 | 实测修正 |
| 8 | **金刚区图标底改 `--color-fill-dim` #DCD6CC**，对白卡 1.45:1，修掉现状 1.09:1 | X7 量化 |
| 9 | **iOS 蓝彻底清除**：`#007AFF→#004FAF` 蓝渐变、`rgba(0,122,255,.3/.35)` 蓝阴影、`#FF4D4F` 全部替换 | 硬约束 + 附录 X6 |
| 10 | **`assets/photo/` 下 26 张图带「AI生成」水印**，不可用于产品级原型；`assets/` 根目录 12 张干净 | 素材实测 |

---

## §1 设计系统选型说明

### 1.1 匹配依据

需求是**「iOS HIG 的几何与交互骨架 + MOYUYO 的暖调色板」**——即**结构借 iOS、皮肤借品牌**。因此选型逻辑是：**先定骨架系统（决定几何与交互范式），再定皮肤系统（决定色相与质感）**，最后用一个电商系统校准商品卡范式。

| 方案 | 设计系统 | 匹配度 | 特征 | 采纳 / 不采纳（组件级） |
|------|---------|--------|------|---------------------|
| **A（骨架）** | **Apple**（其他/通用类 · iOS） | ★★★★★ | iOS HIG 几何、Inset Grouped List、Segmented Control、Large Title、TabBar 49+34、Sheet/Action Sheet、毛玻璃材质 | **采纳**：导航栏三形态、分组列表（16pt 页边距 / 24pt 组距 / 0.5pt hairline / 组末行无线 / 14pt 圆角）、Segmented 33pt、列表行 44/52/76、44pt 热区、搜索胶囊 36pt/r18、Safe Area 规则。<br>**不采纳**：① 系统蓝 `#007AFF` 全族；② SF Symbols 独占（生产已有 lucide.ttf 148 类）；③ iOS 冷灰 `#F2F2F7/#D1D1D6/#C7C7CC`（破坏暖调，全部暖化）；④ iOS 灰阶 `--separator` 冷灰；⑤ 26 液态玻璃（原型为静态 HTML，过重） |
| **B（皮肤）** | **Warm Editorial**（通用起始 · 视觉方向 #1 Editorial Monocle） | ★★★★☆ | 暖调哑光、衬线展示字、大留白、细线分隔、小型大写标签、1-2 点缀色 | **采纳**：暖调中性色板方向、"哑光画册"质感、留白节奏、细线分隔、克制的 1 个点缀色（金）。<br>**不采纳**：① 衬线中文标题（Playfair Display 无中文字形 → fallback 宋体 = 廉价感，X9；A5 已定"不用 Playfair"）；② 非对称杂志网格（移动端 5 屏是功能页不是画册页）；③ 暖调珊瑚/薄荷配色（会引入第二套品牌色） |
| **C（电商校准）** | **Airbnb**（电商消费类） | ★★★☆☆ | 暖沙中性色、圆角卡片、图文比例克制、图片主导 | **采纳**：商品卡「图 1:1 + 名称 2 行 + 价格 + 划线原价 + 评分行」信息序、暖中性而非冷灰的图片环境、卡片圆角偏大（14pt）。<br>**不采纳**：① 其蓝绿强调色；② 多图密集网格与营销红点密度；③ Material 化的大圆角浮起卡片 |
| **D（分组列表细节）** | **Notion**（生产力类） | ★★★☆☆ | 纸感暖白、hairline 分隔、无卡片投影、紧凑行距 | **采纳**：分组列表"无描边、靠 surface 分层 + hairline"的构成方式、纸感中性白。<br>**不采纳**：侧栏 / 斜杠命令交互（本项目是移动端 5 Tab） |
| **E（兜底）** | Default (Neutral Modern) | ★★☆☆☆ | 冷中性、极简、强栅格 | 仅作对照基线，**不采用** —— 无品牌个性，与"哑光画册"调性冲突 |

**明确排除**：Spotify / PlayStation / Meta（深色高饱和，与暖调浅色相反）；Tesla / BMW（冷硬汽车感与仪表盘语言）；Stripe / Mastercard（金融渐变 + 蓝紫）；Linear（开发者暗色高密度）。

### 1.2 5 大视觉方向定位

本项目跨两派：**Soft Warm（暖色圆润、C 端亲和）** 提供色温与形状语言，**Modern Minimal（严格栅格、留白、无装饰堆叠）** 提供结构纪律；**Editorial Monocle** 只借用"细线 + 留白 + 编辑级排版"的分寸，不借其衬线与杂志网格。

**执行纪律（可直接当 QA 检查项）**：
- 层级靠**留白 + 分组**建立，**不靠描边堆叠** —— 全站卡片 `border: none`，只保留 0.5pt hairline 分隔线；
- 一屏一个视觉焦点 —— 每屏指定唯一"大图块"（Home=Banner、Pet Hub=场景图、Community=帖子图、Messages=会话头像、User=会员卡）；
- UI 退后、内容上前 —— 图片/帖子图是唯一有饱和度的元素；
- 移动端 **0 处 `:hover`**；
- **金色不得铺满**，单屏金色填充面积 ≤ 一张卡。

---

## §2 品牌提取协议 · brand-spec

### 2.1 五步执行记录

| 步骤 | 执行内容 | 结果 |
|------|---------|------|
| **1 Locate** | 定位品牌资产 | ① `src/styles/variables.scss`（主色板事实源）② `src/styles/common.scss`（Apple 映射层 + 背景/文字色阶）③ `src/pages.json`（tabBar 色）④ 5 个 tab 页 `<style>` 硬编码色（实测 47 处）⑤ `assets/` 素材 38 张 |
| **2 Download** | 读取资产 | 完整读取 variables.scss / common.scss / pages.json / 5 个 tab 页样式块 + lucide.css（148 类）+ 12 张根目录素材与 26 张 photo/ 素材 |
| **3 Grep Hex** | 提取色值 | 见 §2.2，全部 hex 来自源码变量而非目测 |
| **4 Codify** | 编码 | 见 §2.2 brand-spec 表（CSS 变量 + 用途 + 对比度） |
| **5 Vocalise** | 语言化 | 见 §2.3 |

### 2.2 brand-spec（MOYUYO 品牌规范）

```css
/* ===== 品牌主色 ===== */
--color-primary:        #DBC98A;  /* Sand Gold    品牌主色 · 填充/描边专用 */
--color-primary-light:  #E8DDB5;  /* Sand Gold +1 */
--color-primary-dark:   #B8A66B;  /* Sand Gold -1 · 填充专用（对白仅 2.41:1，不可作文字） */
--ink-gold:             #6E5C30;  /* Sand Gold 文字墨色 · 品牌文字唯一来源 */
--on-primary:           #2E2B29;  /* 金色实心块上的文字色 */
--color-accent:         #B38A5A;  /* Antique Brass 强调色 · 装饰/描边 */
--ink-accent:           #7A5836;  /* Antique Brass 文字墨色 */

/* ===== 中性 ===== */
--color-background:     #F6F2EE;  /* Linen 页面底 */
--color-surface:        #FFFFFF;  /* 卡片表面 */
--color-text:           #2E2B29;  /* Charcoal 正文 · 14.06:1 / 12.62:1 */
--color-text-secondary: #6E6962;  /* 次要文字 · 5.44:1 / 4.88:1 */
--color-text-tertiary:  #9A948C;  /* 装饰专用 · 3.00:1 / 2.70:1 · 禁承载信息 */
--color-divider:        #EAE5DD;  /* 遗留别名 → --separator */

/* ===== 中性填充（全部暖化）===== */
--color-fill:           #F1EDE7;  /* 弱填充 · 搜索底/骨架/输入底 */
--color-fill-hi:        #F8F5F1;  /* 更弱填充 */
--color-fill-raised:    #FFFFFF;  /* 抬升表面 · 选中胶囊 */
--color-fill-dim:       #DCD6CC;  /* 强填充 · 图标底/中灰档 */
--color-fill-dim-strong:#D2CCC2;  /* 最强中灰 · 禁用底/空心图标 */
--color-overlay-subtle: rgba(46,43,41,.04);

/* ===== 强调 / 语义 ===== */
--color-success: #ABB9AD;  --ink-success: #4E6152;  /* Sage Mist */
--color-warm:    #D9B4B0;  --ink-warm:    #8A4F49;  /* Dusty Rose */
--color-info:    #8FA8B6;  --ink-info:    #4A6A7D;  /* 雾蓝灰 */
--color-warning: #E6B97A;  --ink-warning: #8A5A1E;  /* 琥珀 */
--color-danger:  #C96E5F;  /* 软危险 · 状态面 */
--color-danger-solid: #B4574A;  /* 实心危险 · 承载白字 4.77:1 */
--color-danger-ink:   #8E3F35;  /* 危险文字墨色 · 7.21:1 / 6.47:1 */

/* ===== 4 大产品线 ===== */
--color-care: #DBC98A;  --ink-care: #6E5C30;   /* Care  洗护 */
--color-gear: #ABB9AD;  --ink-gear: #4E6152;   /* Gear  出行 */
--color-play: #B38A5A;  --ink-play: #7A5836;   /* Play  玩具 */
--color-home: #D9B4B0;  --ink-home: #8A4F49;   /* Home  家居 */
```

**品牌色相家族**（6 色，全部来自源码，非新增）：

| 家族 | HEX | 角色 | 文字墨色 |
|------|-----|------|---------|
| Sand Gold | `#DBC98A` | 品牌主色 · 面/线 | `#6E5C30` |
| Antique Brass | `#B38A5A` | 强调 · 会员卡渐变端 | `#7A5836` |
| Linen | `#F6F2EE` | 页面底 · 画册纸感 | — |
| Charcoal | `#2E2B29` | 正文 · 唯一实心块上的文字 | — |
| Sage Mist | `#ABB9AD` | Care 辅色 · 成功态 | `#4E6152` |
| Dusty Rose | `#D9B4B0` | Home 辅色 · 情感态 | `#8A4F49` |

### 2.3 品牌语言化（Vocalise）

> MOYUYO 的视觉语言是**温暖、克制、纸感**的。它通过**大面积暖白留白 + 单一沙金点缀**传达"高端宠物生活方式"的从容，通过**分组与细线**建立秩序而非靠描边堆叠，通过**哑光质感**（暖中性替代冷灰、极轻阴影替代浮起卡片）建立"哑光画册"的高级感。
> 最具辨识度的特征是**沙金作为"面"（图标底、指示条、唯一 CTA）而非"字"** —— 金色永远出现在面积小于一屏 5% 的地方。
> 生成时应避免：iOS 系统蓝、蓝紫渐变、Material 大圆角浮起卡片、emoji 图标、满屏金色铺底、无意义的编造统计。始终保持**暖 + 静 + 一屏一焦点**。

---

## §3 完整设计令牌表

### 3.1 颜色（浅色全套，含审核定稿）

> **对比度口径**：WCAG 2.x sRGB 相对亮度，`(L1+.05)/(L2+.05)`。**"对白"** = `#FFFFFF`；**"对 Linen"** = `#F6F2EE`；**"对填充"** = `#F1EDE7`（搜索底/分段轨道底）。
> **阈值**：正文/承载信息文字 ≥ 4.5:1；图标与图形（承载信息）≥ 3:1；装饰性元素不设阈值。

#### 3.1.1 品牌与中性（核心 14 项）

| Token | HEX | 用途 | 对白 | 对 Linen | 规则 |
|-------|-----|------|-----|----------|------|
| `--color-primary` | `#DBC98A` | CTA 底 / 图标底 / VIP 卡 / 评分星 / 选中指示条 / 描边 | 1.65 | 1.48 | **仅填充与描边**。禁作文字色、禁作大面积底 |
| `--color-primary-light` | `#E8DDB5` | 主色 +1 档 · 渐变起点 / 浅填充 | — | — | 仅填充 |
| `--color-primary-dark` | `#B8A66B` | 主色 −1 档 · 禁用/压暗填充 | 2.41 | — | 仅填充，**禁文字** |
| `--ink-gold` | `#6E5C30` | **所有金色文字唯一来源**：选中 tab、价格、会员等级、chip 选中文字、品牌图标 | **6.49** | **5.83** | ✅ AA。小号金色文字**只能**用它 |
| `--on-primary` | `#2E2B29` | 金色实心块内文字 | **8.52**（对金底） | — | ✅ 金底上**只能**炭色。**禁白字**（1.65） |
| `--ink-accent` | `#7A5836` | Antique Brass 文字墨色 | **6.41** | **5.75** | ✅ AA |
| `--color-accent` | `#B38A5A` | Antique Brass 装饰 / 描边 | 3.13 | 2.81 | 仅装饰 |
| `--color-background` | `#F6F2EE` | 页面底（Linen） | 1.10 | — | 页面唯一底色 |
| `--color-surface` | `#FFFFFF` | 卡片 / 分组卡 / 导航栏 | — | 1.10 | 卡片唯一表面色 |
| `--color-text` | `#2E2B29` | 正文 / 标题 | **14.06** | **12.62** | ✅ AAA |
| `--color-text-secondary` | `#6E6962` | 次要说明 / **所有承载信息的微标签**（时间戳、计数、未读数、状态词、占位符） | **5.44** | **4.88** | ✅ AA |
| `--color-text-tertiary` | `#9A948C` | **装饰专用**：禁用态、非信息分隔符、占位图形、chevron | 3.00 | 2.70 | ⚠ **禁承载任何信息**（3.00 < 4.5）。图形用途 ≥3 达标 |
| `--color-text-label` | `var(--color-text-secondary)` | **语义别名**（时间戳/计数/状态词专用名） | 同上 | 同上 | 见 §8 裁决 #2：与 secondary 同值，**同源** |

#### 3.1.2 中性填充（暖化，全部替换 iOS 冷灰）

| Token | HEX | 用途 | 对白 | 对填充/轨道 | 规则 |
|-------|-----|------|-----|------------|------|
| `--color-fill` | `#F1EDE7` | 搜索框底 / 骨架屏底 / 输入框底 / 分段轨道底 | 1.17 | — | 替代 iOS `#F2F2F7`（冷） |
| `--color-fill-hi` | `#F8F5F1` | 更弱填充 | 1.09 | — | 替代 `#F7F7FA`（冷） |
| `--color-fill-raised` | `#FFFFFF` | 抬升面 · 分段选中胶囊 | — | 1.17 | 替代 iOS 纯白抬起 |
| `--color-fill-dim` | `#DCD6CC` | **图标底（金刚区/快捷入口）** / 中灰档 / 头像兜底 | **1.45** | — | 替代 iOS `#D1D1D6`（冷） |
| `--color-fill-dim-strong` | `#D2CCC2` | 最强中灰 · 禁用底 / 空心图标 | **1.60** | — | 替代 iOS `#C7C7CC`（冷） |
| `--color-overlay-subtle` | `rgba(46,43,41,.04)` | 幽灵面（返回按钮底等） | — | — | 暖化（原 `rgba(0,0,0,.04)`） |
| `--secondary` | `var(--color-fill)` | 旧别名 · chip 底 / 次级按钮底 | 1.17 | — | **别名，合并至 `--color-fill`**，禁止单独改值 |

#### 3.1.3 语义色 + 墨色对（ink 色族）

| Token | HEX | 用途 | 弱底（15% over 白） | 墨色 Token | 墨色 HEX | 对白 | 对弱底 |
|-------|-----|------|------|------|------|-----|-------|
| `--color-success` | `#ABB9AD` | 成功面 / 图标底 | `#F2F4F3` | `--ink-success` | `#4E6152` | **6.65** | 6.02 |
| `--color-warm` | `#D9B4B0` | 情感面 / 图标底 | `#F9F4F3` | `--ink-warm` | `#8A4F49` | **6.37** | 5.85 |
| `--color-info` | `#8FA8B6` | 信息面 / 图标底 | `#EEF2F4` | `--ink-info` | `#4A6A7D` | **5.76** | 5.11 |
| `--color-warning` | `#E6B97A` | 警告面 / 图标底 | `#FBF4EB` | `--ink-warning` | `#8A5A1E` | **5.90** | 5.40 |
| `--color-danger` | `#C96E5F` | 软危险面 | — | `--color-danger-ink` | `#8E3F35` | **7.21** | 6.17（对 12% 弱底 `#F6EBE9`） |
| `--color-danger-solid` | `#B4574A` | **实心危险**：角标底 / 滑删按钮底（承载白字） | — | — | — | 4.77 | 白字 **4.77** ✅ |

> **规则**：语义色**只做面**（浅底、图标底、状态条）；语义色的**文字一律走对应 ink**。
> `--color-danger-solid` 是唯一的"实心危险"，专门用于承载白字（白字 4.77:1 ✅）；**危险文字**（删除动作、行内 destructive 文案）必须用 `--color-danger-ink` #8E3F35（对白 7.21 / 对 Linen 6.47 ✅）。
> ⚠ `--color-danger-solid` 对 Linen 仅 4.28:1 → **不可在 Linen 底上作文字**。

#### 3.1.4 4 大产品线（弱底 + 墨色文字）

| Token | HEX | 弱底 22% over 白 | 墨色 Token | 墨色 HEX | 墨字对弱底 | 墨字对白 |
|-------|-----|------|------|------|-----|-------|
| `--color-care` | `#DBC98A` | `#F7F3E5` | `--ink-care` | `#6E5C30` | **5.84** | **6.49** |
| `--color-gear` | `#ABB9AD` | `#EEF1EF` | `--ink-gear` | `#4E6152` | **5.85** | **6.65** |
| `--color-play` | `#B38A5A` | `#F0E8DE` | `--ink-play` | `#7A5836` | **5.28** | **6.41** |
| `--color-home` | `#D9B4B0` | `#F7F0EF` | `--ink-home` | `#8A4F49` | **5.66** | **6.37** |

> 产品线色**只做标签弱底 + 墨色文字**（每个 chip 底 = 该线色 22% alpha + 该线墨色文字 + 0.5pt 该线墨色描边）。**禁**产品线色直接作大字。

#### 3.1.5 iOS 专用新增 token（本轮补齐）

| Token | 值 | 用途 | 对比度依据 |
|-------|-----|------|-----------|
| `--nav-veil` | `rgba(255,255,255,.88)` | 导航栏 / TabBar 毛玻璃底（配 `blur(20px) saturate(180%)`） | 炭字 13.85 / ink-gold 6.39 ✅ |
| `--nav-veil-solid` | `rgba(246,242,238,.96)` | 不支持 backdrop-filter 时的降级底 | — |
| `--surface-raised` | `var(--color-fill)` | **分段控件轨道底**（语义别名，同源） | 标签 `#2E2B29` 12.05 ✅ |
| `--separator` | `rgba(46,43,41,.20)` | 0.5pt hairline 分隔线（卡内） | 对白等效 #D5D5D4 · 1.47 |
| `--separator-strong` | `rgba(46,43,41,.28)` | 组间/导航栏底部强分隔 | 对白等效 #C4C4C3 · 1.75 |
| `--press` | `#ECE6DD` | 按压态底（iOS 是"变灰"不是变色） | 对白 1.24 |
| `--shadow-card` | `0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04)` | 贴面卡片（商品图 / 悬浮卡） | 2 层，1 档 |
| `--shadow-float` | `0 8px 24px rgba(46,43,41,.10)` | 浮层（FAB / Sheet / 弹层） | 1 档 |
| `--glass-dark` | `rgba(46,43,41,.32)` + `blur(20px) saturate(180%)` | **照片上的毛玻璃浮层**（Pet Hub Hero） | 白字 ≥4.81 ✅ |
| `--glass-border` | `rgba(255,255,255,.18)` | 玻璃浮层内描边（0.5pt） | — |
| `--scrim-hero-top` | `linear-gradient(180deg, rgba(46,43,41,.50) 0%, rgba(46,43,41,.12) 18%, rgba(46,43,41,0) 34%)` | Hero 顶部压暗 | — |
| `--scrim-hero-bottom` | `linear-gradient(0deg, rgba(46,43,41,.45) 0%, rgba(46,43,41,0) 28%)` | Hero 底部压暗（压住护理卡） | — |
| `--scrim-banner` | `linear-gradient(to top, rgba(46,43,41,.80) 0%, rgba(46,43,41,.42) 42%, rgba(46,43,41,0) 78%)` | Banner 底部压字（三段式） | 白字最差 **4.88 ✅**（原两段式 .58 实测仅 2.77 ✗，见 §10 审查 P0-1） |
| `--brand-gradient-vip` | `linear-gradient(135deg, #E8DDB5 0%, #DBC98A 46%, #C09A6A 100%)` | 会员卡底 | 炭字 10.34 / 8.52 / **5.40** ✅ |
| 会员卡暖色阴影 | — | **取消**（D3 暖色阴影违反"仅 2 档"） | 见 §8 裁决 #5 |
| `--color-primary-bg` | `rgba(219,201,138,.22)` | chip 选中弱底 / 金色弱面 | ink-gold 5.84 ✅ |
| `--focus-ring` | `0 0 0 2px rgba(110,92,48,.28)` | 键盘/辅助焦点环 | ink-gold 6.49 ✅ |

#### 3.1.6 语义色弱底（Alpha 面，非纯色）

| 用途 | 值 | 墨色搭配 | 墨字对比 |
|------|-----|---------|---------|
| 成功弱底 | `rgba(171,185,173,.15)` → `#F2F4F3` | `--ink-success` | 6.02 ✅ |
| 情感弱底 | `rgba(217,180,176,.15)` → `#F9F4F3` | `--ink-warm` | 5.85 ✅ |
| 信息弱底 | `rgba(143,168,182,.15)` → `#EEF2F4` | `--ink-info` | 5.11 ✅ |
| 警告弱底 | `rgba(230,185,122,.15)` → `#FBF4EB` | `--ink-warning` | 5.40 ✅ |
| 危险弱底 | `rgba(180,87,74,.12)` → `#F6EBE9` | `--color-danger-ink` | 6.17 ✅ |
| 金色弱底 | `rgba(219,201,138,.22)` → `#F7F3E5` | `--ink-gold` | 5.84 ✅ |

> 弱底为 **alpha 合成面**，其实际色值随宿主底色变化（over Linen 时各降 0.4–0.5，仍全部 ≥4.6 ✅）。表格值按 over 白 计算。

### 3.2 字号阶梯

**11 档定稿**。`pt` 是唯一真值；`rpx` 为派生值，按 393pt 主机 **1pt = 1.908rpx**（需求摘要用的 1.923 基于 390pt，已修正，差异 ≤1rpx）。

| 语义 | pt | rpx | 字重 | 行高 | 用途 | 修正说明 |
|------|----|----|------|------|------|---------|
| Large Title | **34** | 65 | 700 | 41 | 社区/消息大标题 | — |
| Title 1 | **28** | 53 | 700 | 34 | 区块主标题、会员卡 Points | rpx 54 → **53**（换算基准修正） |
| Title 2 | **22** | 42 | 600 | 28 | 分区标题 | — |
| Title 3 | **20** | 38 | 600 | 25 | 卡片标题 | ⚠ **不再用于导航居中标题**（见 §8 裁决 #3） |
| Headline | **17** | 32 | 600 | 22 | 列表主文字、商品名、导航居中小标题 | rpx 33 → **32** |
| Body | **17** | 32 | 400 | **26** | **正文**（行高 1.53，满足 G4 ≥1.5） | — |
| Callout | **16** | 31 | 400 | 24 | 描述、次要行 | — |
| Subhead | **15** | 29 | 400 | 22 | 列表副文本、chip 文字、按钮文字 | ⚠ 消息预览 14pt → **15pt**（14pt 不在阶梯内） |
| Footnote | **13** | 25 | 400 | 18 | **时间戳、计数、辅助说明**（承载信息最小字号） | — |
| Caption 1 | **12** | 23 | 400 | 17 | 角标、标签、状态词、日期 | — |
| Caption 2 | **11** | 21 | 400 | 15 | **纯数字 / 徽标专用**（如 "9 张"、"99+"） | ⚠ 降级，见 §8 裁决 #4 |

**字重**：仅保留 4 档 —— `regular 400` / `medium 500` / `semibold 600` / `bold 700`。删除 `light 300`（现状定义但 0 处使用）。**medium 500** 专用于：分段控件选中标签、选中 chip 文字、评分数字。

**字体栈**（无 @font-face 时用系统栈；A5 禁用 Playfair）：
```
Inter, -apple-system, BlinkMacSystemFont, "Helvetica Neue",
"PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif
```
> ⚠ 现状 `common.scss` 声明了 `'Playfair Display'` 与 `'Inter'` 但**项目无任何 `@font-face`**，两者均不生效。Playfair 更在中文下 fallback 宋体 → 廉价感（X9）。**删除 Playfair 声明**；`'Inter'` 若无字体文件请一并移除。

**数字排版**：价格 / 计数 / 积分使用 `font-variant-numeric: tabular-nums`，保证纵向对齐。

### 3.3 几何量

#### 3.3.1 圆角（iOS continuous 圆角）

> CSS 端用标准 `border-radius` 近似 Apple continuous curve（视觉差异 <2%）；原生端由 UIKit `.continuous` 提供。
> **每个圆角只有 1 个定义源**，按语义命名而非数字。

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-media` | **12pt** | 卡内图、商品图、提醒条、小图标底、搜索图标底 |
| `--radius-group` | **14pt** | 分组卡 / 列表容器 / Action Sheet 分组 |
| `--radius-well` | **16pt** | 金刚区图标底、快捷入口图标底、弹出小卡 |
| `--radius-banner` | **18pt** | Banner 大卡 |
| `--radius-sheet` | **20pt** | 会员卡 / 底部 Sheet 顶部 / 底部弹层 |
| `--radius-pill` | **999pt** | chip / 搜索胶囊 / 角标 pill / 按钮（胶囊型） |
| `--radius-circle` | **50%** | 头像 / 圆形图标底 |

> 现状同屏出现 7–10 档圆角（2/4/6/12/16/20/24/32/36/999rpx）→ 收敛为 **7 个语义圆角**。

#### 3.3.2 间距（8pt 基准 + 4pt 半步）

| Token | pt | rpx | 用途 |
|-------|----|----|------|
| `--space-1` | 4 | 8 | 图标↔文字、chip 内间距 |
| `--space-2` | 8 | 15 | 卡内元素间距、图标↔标签 |
| `--space-3` | 12 | 23 | 卡内分节、组头↔组卡 |
| `--space-4` | 16 | 31 | **页面左右边距**、卡内 padding |
| `--space-5` | 20 | 38 | 紧凑组距（备用） |
| `--space-6` | 24 | 46 | **组间距** |
| `--space-8` | 32 | 61 | 区块间距 |
| `--space-10` | 40 | 76 | 大区块 |
| `--space-12` | 48 | 92 | 页脚留白 |

**精确布局规则**：
- 页面左右边距 **16pt** → 内容宽 **361pt**（393 − 32）
- 组卡圆角 14pt；卡内左右 padding **16pt**
- **组间距 24pt**（组卡底 → 下一组组头文字顶部），组头下方 6pt 到组卡顶
- 区块间距 **32pt**
- 商品卡：图↔正文 **10pt**，名称↔价格 **8pt**，价格↔评分 **6pt**
- 帖子卡之间 **12pt**；卡内左右 **12pt**；作者行↔正文 **8pt**；正文↔图片 **12pt**
- 金刚区：4 列，列间距 8pt；图标底 56pt；图标↔标签 8pt；卡片上下 padding 16pt

#### 3.3.3 组件高度

| 组件 | 高度 | 备注 |
|------|------|------|
| 状态栏 / 安全区顶 | 59pt（iPhone 14 Pro） | `max(--status-bar-height, env(safe-area-inset-top))` 取大不相加 |
| 紧凑导航栏 | **44pt** | 状态栏之下 |
| Large Title 区 | **52pt** | 34pt 文字 + 上下各 9pt；总高 = 状态栏 + 44 + 52 |
| Segmented Control | **33pt** | 内含 2pt 内缩 + 29pt 胶囊 |
| 搜索胶囊 / Search Field | **36pt** | radius 18pt（pill） |
| 列表行 | **44 / 52 / 76pt** | 单行 / 双行 / 带头像 |
| 头像 | 48pt（消息会话）· 36pt（社区作者）· 72pt（个人主页）· 44pt（宠物切换 rail） | 圆形 |
| TabBar | **49pt** + 安全区 34pt = **83pt** | 图标盒 29pt + 标签 14pt 行高 + 间距 2pt + 上下各 2pt = 49pt |
| 会员卡 | **132pt** | — |
| Banner | 361 × **203pt**（16:9） | radius 18pt |
| 商品图 | **174.5pt**（1:1） | 列宽 (361−12)/2 |
| FAB | **56pt** | 圆形 |
| 护理三卡 | 列宽 **120.3pt**，卡内 padding 12pt | 361/3 |
| 金刚区 4 列 | 列宽 **84.25pt**，图标底 56pt | (361−24)/4 |
| 快捷入口 5 列 | 列宽 **65.8pt**，图标底 44pt | (361−32)/5 |
| 按钮 | 主 **50pt** / 次 **44pt** / 图标 **44×44pt** | — |
| chip | **32pt** | 视觉 32pt，命中区扩至 44pt |
| 数字角标 | **18pt** | min-width 18pt，radius 9pt |
| 红点 | **8pt** | + 2pt 背景色环 |
| 拖拽把手 | 36 × **5pt**，radius 2.5pt | — |
| 底部 Sheet 拖拽区 | 上边距 8pt | — |

#### 3.3.4 分隔线

- 粗细 **0.5pt**（iOS hairline；@2x = 1 物理像素）
- 颜色 `--separator` `rgba(46,43,41,.20)`（对白等效 1.47，与 iOS `rgba(60,60,67,.29)` 观感一致但暖化）
- 强分隔（组间、导航栏底部）`--separator-strong` `rgba(46,43,41,.28)`
- **左 inset 对齐规则**：分隔线起点 = 该行**文字起点**（不是卡片左边缘）
  - 纯文字行文字起点 = 卡内 16pt → 线起点 = 卡左 + 16pt
  - 图标行（28pt 图标 + 12pt 间距）文字起点 = 卡内 56pt → 线起点 = 卡左 + 56pt
  - 头像行（48pt 头像 + 12pt 间距）文字起点 = 卡内 76pt → 线起点 = 卡左 + 76pt
  - 统一公式：**文字起点 = 16 + 前导元素宽 + 12**
- 分隔线**右端到卡片右边缘**（不内缩）
- **组内最后一行不画分隔线**；组卡顶部不画分隔线

#### 3.3.5 触摸热区

- 全站最小命中区 **44 × 44pt**
- 现状不达标项（附录 X8）全部修正：`.more-btn` 56rpx(29pt) → 44pt；`.icon-btn` 60rpx(31pt) → 44pt；`.navbar-icon` 56rpx(29pt) → 44pt；`.topic-tag` 高 56rpx(29pt) → 32pt 视觉 + 命中扩 44pt
- 图标按钮：命中区 44×44pt，内部图标 20–22pt
- 相邻命中区间距 ≥ 8pt

#### 3.3.6 阴影（仅 2 档）

| Token | 值 | 用途 |
|-------|-----|------|
| `--shadow-card` | `0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04)` | 贴面卡片：商品图、会员卡、弹出小卡 |
| `--shadow-float` | `0 8px 24px rgba(46,43,41,.10)` | 浮层：FAB、底部 Sheet、Action Sheet、TabBar 上浮 |

> 现状 `variables.scss` 有 **10 个阴影 token**（sm/md/lg/card-sm/md/lg/pop/top）→ 收敛为 2 档。D3 的会员卡暖色阴影 `rgba(179,138,90,.22)` 取消（见 §8 裁决 #5）。
> **禁止**：Material 彩色投影、大范围弥散阴影、卡片多重叠加阴影。

#### 3.3.7 Z-index

`base 0` → `sticky 100`（导航栏/TabBar）→ `overlay 200`（遮罩）→ `sheet 300` → `toast 400` → `fab 250`

### 3.4 动效

#### 3.4.1 缓动曲线

| Token | 值 | 用途 |
|-------|-----|------|
| `--ease-standard` | `cubic-bezier(.32,.72,0,1)` | 通用状态切换、导航栏折叠、分段指示胶囊 |
| `--ease-emphasis` | `cubic-bezier(.34,1.56,.64,1)` | 弹跳：点赞心形、Sheet 弹入、chip 选中回弹 |
| `--ease-exit` | `cubic-bezier(.4,0,1,1)` | 退出/关闭（Sheet 下沉、FAB 淡出） |

#### 3.4.2 时长

| Token | 值 | 用途 |
|-------|-----|------|
| `--dur-press-in` | **120ms** | 按下（scale 收缩 + 变灰） |
| `--dur-press-out` | **200ms** | 松开回弹（scale 恢复 + 褪灰） |
| `--dur-segment` | **250ms** | 分段指示胶囊位移（统一 home / community） |
| `--dur-nav` | **300ms** | 导航栏毛玻璃淡入 |
| `--dur-sheet-in` | **350ms** | Sheet / Action Sheet 弹入（`--ease-emphasis`） |
| `--dur-sheet-out` | **250ms** | Sheet 关闭（`--ease-exit`） |
| `--dur-fade` | **200ms** | 透明度淡入淡出 |
| `--dur-carousel` | **3000ms** | Banner 自动轮播间隔 |
| `--dur-skeleton` | **1200ms** | 骨架屏呼吸循环 |

#### 3.4.3 按压态

| 元素类型 | scale | 背景 | 过渡 |
|---------|-------|------|------|
| 小元素（chip、图标按钮、胶囊、角标） | **.97** | 变 `--press` | 120ms in / 200ms out |
| 卡片（商品卡、护理卡、Banner 卡） | **.98** | 变 `--press` | 同上 |
| 列表行 / 按钮 | **不缩放** | 变 `--press`（iOS 列表行只变灰） | 同上 |
| 分段控件选中胶囊 | .96 | 变 `--press` | 120ms |

- **全局 0 处 `:hover`**（移动端无 hover 语义）
- 按下即变灰，**不延迟 300ms**（iOS 无延迟）
- 拖拽中（FAB 跟手、Segmented 跟手）关闭过渡动画

#### 3.4.4 Large Title 折叠公式（3 屏共用）

```
p = clamp(scrollY / 52, 0, 1)          // 52pt 滚动距离内完成折叠
```

| 参数 | 公式 | 说明 |
|------|------|------|
| 导航栏毛玻璃 opacity | `p` | 0 → 1 线性 |
| 导航栏底部 hairline opacity | `p` | 随毛玻璃同步 |
| 大标题 opacity | `1 − clamp((p − 0.35) / 0.5, 0, 1)` | p=0.35 开始淡出，p=0.85 完全消失 |
| 大标题 translateY | `−12 × p` (pt) | 轻微上移，制造"沉入导航栏"感 |
| **居中小标题 opacity** | `clamp((p − 0.55) / 0.35, 0, 1)` | p=0.55 开始淡入，p=0.90 完全显现；字号 17pt/600 |
| 大标题 translateY 终值 | 导航栏底 44pt 处 | **滚动时导航栏高度恒定，禁止跳变** |

**首页（Compact 变体）额外规则**：
- 搜索胶囊：p ∈ [0, 0.15] 保持 36pt；p ∈ [0.15, 0.55] 高度收至 32pt 且 opacity → 0
- 居中「商城」小标题 opacity = `clamp((p − 0.55) / 0.35, 0, 1)`，17pt/600

**「我的」（透明 nav 变体）额外规则**：
- p = `clamp(scrollY / 40, 0, 1)`；毛玻璃 opacity = p；hairline opacity = p；居中「我的」opacity = `clamp((p − 0.55) / 0.35, 0, 1)`

#### 3.4.5 降级

`prefers-reduced-motion: reduce` 下：禁用粒子、禁用骨架屏呼吸、禁用弹跳（Sheet 用 `--ease-standard` + 200ms）、保留状态切换与透明度过渡。

### 3.5 安全区

| 项 | 规则 | 值 |
|----|------|-----|
| 顶部 | `max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px))` | 59pt（iPhone 14 Pro） |
| ⚠ | **取大不相加** —— iOS 上两者同为状态栏高度，相加会翻倍。现状代码已正确，保持 | — |
| 底部 | `env(safe-area-inset-bottom, 0px)` | 34pt |
| TabBar 总高 | 49 + 34 | **83pt** |
| 内容末项 padding-bottom | 83 + 24 | **107pt** |
| 适配视口 | 393×852（主机）· 390×844 · 430×932 · 375×667 | 横向全部按 16pt 边距自适应 |

### 3.6 令牌合并与废弃表（现状 → 唯一来源）

| 现状 token | 现状值 | 处置 | 归并到 |
|-----------|--------|------|--------|
| `--color-text-label` | — | **新增** | 语义别名 → `--color-text-secondary` |
| `--ink-gold` / `--on-primary` / `--ink-accent` | — | **新增** | ink 色族 |
| `--ink-success/warm/info/warning/danger` | — | **新增** | 语义墨色 |
| `--ink-care/gear/play/home` | — | **新增** | 产品线墨色 |
| `--color-danger-solid` / `--color-danger-ink` | — | **新增** | 危险拆分 |
| `--nav-veil` / `--nav-veil-solid` / `--separator` / `--separator-strong` / `--press` | — | **新增** | iOS 补齐 |
| `--surface-raised` / `--focus-ring` | — | **新增** | 语义别名 |
| `--color-fill` | `#f2f2f7` | **改值** | → `#F1EDE7`（暖化） |
| `--color-fill-hi` | `#f7f7fa` | **改值** | → `#F8F5F1`（暖化） |
| `--color-fill-dim` | `#d1d1d6` | **改值** | → `#DCD6CC`（暖化） |
| `--color-fill-dim-strong` | `#c7c7cc` | **改值** | → `#D2CCC2`（暖化） |
| `--color-overlay-subtle` | `rgba(0,0,0,.04)` | **改值** | → `rgba(46,43,41,.04)`（暖化） |
| `--secondary` | `#f2f2f7` | **改值** | → `var(--color-fill)` |
| `--secondary-foreground` | `#2e2b29` | **保留** | → `var(--color-text)` |
| `--color-divider` | `#eae5dd` | **改值** | → `var(--separator)` |
| `--color-card` | `#ffffff` | **保留** | → `var(--color-surface)` |
| `--color-text-primary` | `#2e2b29` | **保留** | → `var(--color-text)` |
| `--color-primary-bg` | `rgba(219,201,138,.15)` | **改值** | → `.22` |
| `--state-error-foreground` | `#ffffff` | **保留** | 白字在 danger-solid 上 4.77 ✅ |
| `--state-success-surface` / `--state-error-surface` / `--state-warning-surface` | 各自 .15 | **保留** | 数值不变（§3.1.6） |
| `--color-error` | `#c96e5f` | **改值** | → `var(--color-danger)` |
| `--shadow-sm/md/lg` | 3 档 | **废弃** | → `--shadow-card` |
| `--shadow-card-sm/md/lg` | 3 档 | **废弃** | → `--shadow-card` |
| `--shadow-pop` | 1 档 | **废弃** | → `--shadow-float` |
| `--shadow-top` | 1 档 | **废弃** | → 删除（TabBar 用 hairline，不用上投影） |
| `--radius-sm/md/lg/pill` | 4 档 rpx | **废弃** | → §3.3.1 的 7 个语义圆角 |
| `--space-xs/sm/md/lg/xl/2xl` | 6 档 rpx | **废弃** | → §3.3.2 的 9 档 pt 间距 |
| `--font-size-xs/sm/base/md/lg/xl/2xl/3xl` | 8 档 rpx | **废弃** | → §3.2 的 11 档语义字号 |
| `--font-weight-light` | 300 | **废弃** | 0 处使用 |
| `--font-weight-regular/medium/semibold/bold` | 4 档 | **保留** | 唯一字重来源 |
| `--brand-50…800`（9 档） | 硬编码 | **废弃** | 深浅两套写死色阶，无法跟随主题 → 删除 |

> **执行口径**：合并完成后，`variables.scss` 的颜色 token 数量应从 60+ 降到 **48 个语义 token**；阴影 10 → 2；圆角 4 → 7；间距 6 → 9；字号 8 → 11；字重 6 → 4。

### 3.7 深色模式槽位预留（本轮不填值）

按 D5，本轮只出浅色。但 token 表**结构完整**，深色下轮只需覆写同名变量，**不允许新增变量**。

```css
/* ===== 深色槽位结构（值下轮填充）=====
   命名与浅色 1:1 对齐；本轮不填写具体值。
   填充规则（下轮）：
   1) 底/面反转：background #1A1816 / surface #2A2724
   2) 文字反转：text #F6F2EE / secondary #B8B0A5 / tertiary #807A72
   3) ink 色族提亮为浅金/浅语义色（--ink-gold → #D9C68C 等），ink 与 fill 的角色关系不变
   4) --color-danger-solid → #C96E5F（白字 3.57，需同时提亮到 ≥4.5）
   5) --nav-veil → rgba(42,39,36,.88)；--glass-dark → rgba(0,0,0,.42)
   6) 阴影在深色下改为 rgba(0,0,0,.40)/(.55)
   7) 品牌 IP 插画与 logo 不变
*/
[data-theme="dark"],
.theme-dark { /* 深色值下轮填充 */ }

@media (prefers-color-scheme: dark) {
  :root:not(.theme-light) { /* 深色值下轮填充 */ }
}
```

> 浅色模式下 `:root` 的 48 个 token 全部有值；深色块为**空占位**，结构与浅色完全同构。

### 3.8 CSS 变量落地块（可直接覆盖 variables.scss）

> 仅 token 声明，无组件样式。深色块保留为空占位（见 §3.7）。
>
> **单位约定（重要）**：本块的**长度值一律写 `px`**。原型是 393px 宽的 HTML 视口 = 393pt 设计宽，故 **1 设计 pt = 1 px**，直接写 `pt` 会被浏览器按 1.333px 换算而错位。
> 生产 uni-app 落地时按 **`1pt = 1.908rpx`**（750/393）换算，注释已标注 rpx 值。
> **设计真值单位是 pt**；px / rpx 都是派生。

```css
:root {
  /* ===== 品牌主色（面/线专用） ===== */
  --color-primary: #DBC98A;
  --color-primary-light: #E8DDB5;
  --color-primary-dark: #B8A66B;
  --ink-gold: #6E5C30;
  --on-primary: #2E2B29;
  --color-accent: #B38A5A;
  --ink-accent: #7A5836;

  /* ===== 中性 ===== */
  --color-background: #F6F2EE;
  --color-surface: #FFFFFF;
  --color-text: #2E2B29;
  --color-text-secondary: #6E6962;
  --color-text-label: var(--color-text-secondary);
  --color-text-tertiary: #9A948C;
  --color-divider: rgba(46, 43, 41, 0.20);

  /* ===== 中性填充（暖化） ===== */
  --color-fill: #F1EDE7;
  --color-fill-hi: #F8F5F1;
  --color-fill-raised: #FFFFFF;
  --color-fill-dim: #DCD6CC;
  --color-fill-dim-strong: #D2CCC2;
  --color-overlay-subtle: rgba(46, 43, 41, 0.04);
  --secondary: var(--color-fill);
  --secondary-foreground: var(--color-text);
  --color-card: var(--color-surface);
  --color-text-primary: var(--color-text);

  /* ===== 强调 / 语义（色 + 墨色） ===== */
  --color-success: #ABB9AD;   --ink-success: #4E6152;
  --color-warm:    #D9B4B0;   --ink-warm:    #8A4F49;
  --color-info:    #8FA8B6;   --ink-info:    #4A6A7D;
  --color-warning: #E6B97A;   --ink-warning: #8A5A1E;
  --color-danger:  #C96E5F;
  --color-danger-solid: #B4574A;
  --color-danger-ink:   #8E3F35;
  --color-error: var(--color-danger);
  --state-success: var(--color-success);
  --state-success-surface: rgba(171, 185, 173, 0.15);
  --state-error: var(--color-danger);
  --state-error-surface: rgba(180, 87, 74, 0.12);
  --state-error-foreground: #FFFFFF;
  --state-warning: var(--color-warning);
  --state-warning-surface: rgba(230, 185, 122, 0.15);

  /* ===== 4 大产品线 ===== */
  --color-care: #DBC98A;  --ink-care: #6E5C30;
  --color-gear: #ABB9AD;  --ink-gear: #4E6152;
  --color-play: #B38A5A;  --ink-play: #7A5836;
  --color-home: #D9B4B0;  --ink-home: #8A4F49;
  --color-primary-bg: rgba(219, 201, 138, 0.22);

  /* ===== iOS 补齐 ===== */
  --nav-veil: rgba(255, 255, 255, 0.88);
  --nav-veil-solid: rgba(246, 242, 238, 0.96);
  --surface-raised: var(--color-fill);
  --separator: rgba(46, 43, 41, 0.20);
  --separator-strong: rgba(46, 43, 41, 0.28);
  --press: #ECE6DD;
  --focus-ring: 0 0 0 2px rgba(110, 92, 48, 0.28);
  --shadow-card: 0 1px 3px rgba(46, 43, 41, 0.05), 0 0 0 0.5px rgba(46, 43, 41, 0.04);
  --shadow-float: 0 8px 24px rgba(46, 43, 41, 0.10);
  --glass-dark: rgba(46, 43, 41, 0.32);
  --glass-border: rgba(255, 255, 255, 0.18);
  --scrim-hero-top: linear-gradient(180deg, rgba(46,43,41,.50) 0%, rgba(46,43,41,.12) 18%, rgba(46,43,41,0) 34%);
  --scrim-hero-bottom: linear-gradient(0deg, rgba(46,43,41,.45) 0%, rgba(46,43,41,0) 28%);
  --scrim-banner: linear-gradient(to top, rgba(46,43,41,.58), transparent 55%);
  --brand-gradient-vip: linear-gradient(135deg, #E8DDB5 0%, #DBC98A 46%, #C09A6A 100%);

  /* ===== 圆角（语义唯一来源） ===== */
  --radius-media: 12px;
  --radius-group: 14px;
  --radius-well: 16px;
  --radius-banner: 18px;
  --radius-sheet: 20px;
  --radius-pill: 999px;
  --radius-circle: 50%;

  /* ===== 间距（8px 基准） ===== */
  --space-1: 4px;  --space-2: 8px;   --space-3: 12px;
  --space-4: 16px; --space-5: 20px;  --space-6: 24px;
  --space-8: 32px; --space-10: 40px; --space-12: 48px;

  /* ===== 字号（设计真值单位 pt；此处写 px：原型 393px 宽 = 393pt，故 1pt = 1px）
         rpx 派生 @393px 主机：1pt = 1.908rpx，注释中给出 ===== */
  --font-size-large-title: 34px;  /* 65rpx */
  --font-size-title-1: 28px;      /* 53rpx */
  --font-size-title-2: 22px;      /* 42rpx */
  --font-size-title-3: 20px;      /* 38rpx */
  --font-size-headline: 17px;     /* 32rpx */
  --font-size-body: 17px;         /* 32rpx */
  --font-size-callout: 16px;       /* 31rpx */
  --font-size-subhead: 15px;       /* 29rpx */
  --font-size-footnote: 13px;      /* 25rpx */
  --font-size-caption-1: 12px;     /* 23rpx */
  --font-size-caption-2: 11px;     /* 21rpx */

  /* ===== 行高 ===== */
  --lh-large-title: 41px; --lh-title-1: 34px; --lh-title-2: 28px;
  --lh-title-3: 25px;   --lh-headline: 22px; --lh-body: 26px;
  --lh-callout: 24px;   --lh-subhead: 22px; --lh-footnote: 18px;
  --lh-caption-1: 17px; --lh-caption-2: 15px;

  /* ===== 字重（4 档） ===== */
  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
  --font-weight-bold: 700;

  /* ===== 动效 ===== */
  --ease-standard: cubic-bezier(.32, .72, 0, 1);
  --ease-emphasis: cubic-bezier(.34, 1.56, .64, 1);
  --ease-exit: cubic-bezier(.4, 0, 1, 1);
  --dur-press-in: 120ms;   --dur-press-out: 200ms;
  --dur-segment: 250ms;    --dur-nav: 300ms;
  --dur-sheet-in: 350ms;   --dur-sheet-out: 250ms;
  --dur-fade: 200ms;       --dur-carousel: 3000ms;
  --dur-skeleton: 1200ms;
  --press-scale-sm: .97;
  --press-scale-card: .98;

  /* ===== 安全区 ===== */
  --safe-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  --safe-bottom: 34px;
  --tabbar-height: 49px;
  --tabbar-total: 83px;
  --content-bottom-padding: 107px;

  /* ===== 组件几何 ===== */
  --nav-bar-height: 44px;
  --nav-large-title-height: 52px;
  --h-segment: 33px;
  --h-search: 36px;
  --h-row-single: 44px;
  --h-row-double: 52px;
  --h-row-avatar: 76px;
  --h-fab: 56px;
  --h-chip: 32px;
  --h-badge: 18px;
  --d-badge-dot: 8px;
  --touch-min: 44px;
  --hairline: 0.5px;

  /* ===== 层级 ===== */
  --z-base: 0; --z-sticky: 100; --z-overlay: 200;
  --z-sheet: 300; --z-fab: 250; --z-toast: 400;
}
```

---

## §4 组件规范

> 每个组件给出：**几何 · 颜色 · 排版 · 状态 · 交互**。所有尺寸/颜色必须引用 §3 的 token，不得出现裸值。
> **通用规则**：① 命中区 ≥ 44×44pt；② 文字色按 §3.1 职责取用；③ 有 `:active` 无 `:hover`；④ 同屏白卡 ≤ 4 张。

### 4.1 导航栏（三种形态）

#### 4.1.1 Large Title（社区 / 消息）

| 项 | 规格 |
|----|------|
| 结构 | 状态栏区（`--safe-top`）→ 紧凑工具条 44pt（透明）→ Large Title 区 52pt（在滚动内容流内） |
| 工具条底 | 初始透明；滚动后 `--nav-veil` + `blur(20px) saturate(180%)` |
| 工具条底 hairline | 0.5pt `--separator`，opacity = p |
| Large Title | 34pt / 700 / 行高 41pt，色 `--color-text`，左 16pt，距工具条底 9pt，块高 52pt（文字 41 + 下 11） |
| 居中小标题 | 17pt / 600 / 行高 22pt，色 `--color-text`，**绝对居中**（不受左右按钮影响），opacity = `clamp((p−0.55)/0.35,0,1)` |
| 右侧动作 | 命中区 44×44pt，图标 22pt，色 `--color-text-secondary`；多个按钮间距 8pt |
| 左侧动作 | 同上（社区/消息无返回按钮） |
| 折叠 | 见 §3.4.4；**导航栏高度全程恒定 44pt，禁止跳变** |
| 背景色 | 折叠后工具条用 `--nav-veil`；内容区底 `--color-background` |

#### 4.1.2 Compact 工具条（首页）

| 项 | 规格 |
|----|------|
| 高度 | 44pt（状态栏之下），左右 padding 16pt |
| 搜索胶囊 | 高 **36pt**，radius **18pt**（pill），底 `--color-fill`，左内 padding 10pt；放大镜 16pt `--color-text-secondary`；占位文字 15pt `--color-text-secondary`（「搜索宠物好物、品牌、攻略」） |
| 右侧图标 | 扫码 / 铃铛，各命中区 44×44pt，图标 22pt `--color-text`；铃铛右上角挂角标（§4.12） |
| 滚动后 | 工具条底 → `--nav-veil` + blur；搜索胶囊高度收至 32pt 且 opacity → 0（p ∈ [0.15,0.55]）；居中「商城」17pt/600 淡入（p ∈ [0.55,0.90]） |
| 无 Large Title | 搜索胶囊占据导航栏主体位置 |

#### 4.1.3 透明 nav → 滚动浮出（我的）

| 项 | 规格 |
|----|------|
| 初始 | 完全透明，无 hairline；内容延伸到顶部（头部自带 `--safe-top` 内边距） |
| 滚动后 | 底 → `--nav-veil` + blur（opacity = p，p = clamp(scrollY/40,0,1)）；hairline opacity = p；居中「我的」17pt/600 淡入 |
| 过渡 | 300ms `--ease-standard` |

### 4.2 Segmented Control

| 项 | 规格 |
|----|------|
| 高度 | **33pt** |
| 轨道底 | `--surface-raised`（= `--color-fill` #F1EDE7）+ 0.5pt `--separator` 描边（保证在 Linen 底上可见） |
| 轨道圆角 | **9pt**（iOS continuous 近似） |
| 选中胶囊 | 高 29pt（上下各内缩 2pt），radius **7pt**，底 `--color-fill-raised` #FFFFFF + `--shadow-card` |
| 指示胶囊位移 | **250ms** `--ease-standard`（全站统一，home / community 一致）；跟手时关闭过渡 |
| 未选中标签 | 13pt / **400** / 行高 18pt，色 `--color-text`（对轨道 **12.06:1** ✅） |
| 选中标签 | 13pt / **600**，色 `--color-text`（对白胶囊 **14.06:1** ✅） |
| 宽度 | 等分；容器宽 = 361pt（首页推荐区 / 社区 tab 区） |
| 段间距 | 0（胶囊之间自然留白 2pt 内缩） |
| 按下 | 胶囊 scale .96 + 底 `--press`，120ms |

> **层级由"白胶囊 + 字重"承担，不由灰色文字承担** —— 因为在 1.17:1 的暖轨道上任何灰字都无法达到 4.5:1。

### 4.3 Inset Grouped List

| 项 | 规格 |
|----|------|
| 页面左右边距 | **16pt** → 卡片宽 **361pt** |
| 组卡 | 底 `--color-surface`，**无描边**（`border: none`），radius `--radius-group` 14pt，`overflow: hidden` |
| 组卡阴影 | **无**（iOS 分组列表无投影；只有浮出的卡片才给 `--shadow-card`） |
| **组间距** | **24pt** = 组卡底 → 下一组组头文字顶部；组头下方 6pt 到组卡顶；首个组头上方 8pt |
| 组头小标题 | 13pt / 400 / 行高 18pt，色 `--color-text-secondary`，左对齐 **卡左 + 16pt**（与卡内文字起点对齐）；**不得大写**（中文无意义） |
| 组内行 | 高 44 / 52 / 76pt（按 §3.3.3） |
| 卡内左右 padding | 16pt |
| 分隔线 | 0.5pt `--separator`；左起点 = 该行文字起点；右端到卡右边缘；**组内末行不画**；组卡顶部不画 |
| 卡内元素 | 前导图标/头像 + 12pt 间距 + 文字块；文字块右对齐元信息（时间/箭头） |

### 4.4 列表行

| 类型 | 高度 | 前导 | 文字 | 副文本 | 右侧 |
|------|------|------|------|------|------|
| 单行 | **44pt** | 可选 28pt 图标底（radius 12pt）+ 12pt | 17pt/400 `--color-text` | — | chevron 14pt `--color-text-tertiary` |
| 双行 | **52pt** | 同上 | 17pt/400 `--color-text` | 15pt/400 `--color-text-secondary` | chevron 14pt tertiary |
| 带头像 | **76pt** | 头像 48pt（圆形，`--radius-circle`），客服会话用 44pt 耳机图标底 | 17pt/400 `--color-text` | 15pt/400 `--color-text-secondary` 单行截断 | 时间 12pt `--color-text-secondary` + chevron |

- 行文字起点 = **16 + 前导宽 + 12**（卡内坐标）
- 头像行 = 16 + 48 + 12 = **76pt**（卡内）；页面绝对坐标 = 16 + 76 = **92pt**
- chevron `--color-text-tertiary`（3.00 图形 ✅，装饰性）
- 行按下：背景 → `--press`，**不缩放**，120ms in / 200ms out
- 行内**不出现任何金色文字**；元信息（时间/计数）用 `--color-text-secondary`

### 4.5 Search Field

| 项 | 规格 |
|----|------|
| 高度 | **36pt** |
| 圆角 | **18pt**（pill） |
| 底色 | `--color-fill` #F1EDE7 |
| 内 padding | 左右 10pt |
| 放大镜图标 | 16pt，`--color-text-secondary`（对填充 4.67 ✅） |
| 占位文字 | 15pt / 400，`--color-text-secondary`（对填充 4.67 ✅）；**禁用 tertiary**（2.58 ✗） |
| 聚焦态 | 外描边 0.5pt `--ink-gold` + 底保持；未聚焦无描边 |
| 命中区 | 视觉 36pt，上下各补 4pt 透明边 → **44pt** |
| 清除按钮（若有） | 14pt 圆 × 14pt，`--color-text-tertiary` |

### 4.6 Filter Chip / 分类 chip

| 状态 | 底 | 文字 | 描边 | 对比度 |
|------|-----|------|------|-------|
| 未选中 | `--color-fill-dim` **#DCD6CC** | `--color-text` 13pt/400 | 无 | 底对白卡 1.45 ✅；字对底 9.73 ✅ |
| **选中** | **品牌 tint 底** `rgba(219,201,138,.22)` → #F7F3E5 | **`--ink-gold`** 13pt/**500** | **0.5pt `--ink-gold`** | 字对底 5.84 ✅；描边 5.84 ✅ |

- 高 **32pt**，radius **16pt**（pill），左右内 padding 14pt，chip 间距 8pt
- 命中区扩至 **44pt**（上下各 6pt 透明 padding）
- 横向滚动条：**两端无渐隐遮罩**（iOS 无此惯例），容器左右 padding 16pt
- ⚠ **禁止金色底 + 金色字**（`#DBC98A` 底 + `#DBC98A` 字 = 1.65 ✗）；⚠ 禁止"金色底 + 白字"（1.65 ✗）；唯一合法组合是 **tint 底 + `--ink-gold` 字 + `--ink-gold` 描边**

**产品线 chip（首页新增横滑）** —— 4 个：Care 洗护 / Gear 出行 / Play 玩具 / Home 家居

| 线 | 弱底（22% over 白） | 文字 | 描边 |
|----|------|------|------|
| Care | `#F7F3E5` | `--ink-care` #6E5C30 | 0.5pt #6E5C30 |
| Gear | `#EEF1EF` | `--ink-gear` #4E6152 | 0.5pt #4E6152 |
| Play | `#F0E8DE` | `--ink-play` #7A5836 | 0.5pt #7A5836 |
| Home | `#F7F0EF` | `--ink-home` #8A4F49 | 0.5pt #8A4F49 |

> 产品线 chip 的底 **必须按线色渲染**（`color-mix` 或 22% alpha 的线色），不能用统一的金 tint，否则 4 条产品线失去区分度。

### 4.7 商品卡

| 项 | 规格 |
|----|------|
| 网格 | 2 列，列间距 **12pt**，列宽 **174.5pt**（(361−12)/2） |
| 图片 | **1:1**（174.5×174.5pt），radius `--radius-media` 12pt，**无描边**，`--shadow-card` |
| 图片底（未加载） | `--color-fill` |
| 图↔正文 | **10pt** |
| 名称 | 15pt / 400 / 行高 20pt / `--color-text`，**2 行截断**（`-webkit-line-clamp: 2`） |
| 描述 | 13pt / 400 / 行高 18pt / `--color-text-secondary`，1 行截断；图↔描述 4pt |
| 名称↔价格 | **8pt** |
| 现价 | **17pt / 600** / `--ink-gold`（`tabular-nums`）；行高 22pt |
| 划线原价 | 13pt / 400 / `--color-text-secondary`，`line-through`；与现价间距 6pt；**禁用 tertiary**（3.00 ✗ 不承载价格信息） |
| 价格↔评分 | **6pt** |
| 评分行 | 星 12pt **`--color-primary` 填充**（D1 允许"评分星"用主色；对白 1.65，**因评分数值同在行内、图形为冗余装饰**，故按 WCAG 1.4.11 例外适用；若审查要求 ≥3:1 则改用 `--ink-gold` 6.49）；评分值 12pt/**500** `--color-text`（14.06 ✅，**数值才是承载信息的部分**）；分隔点 12pt `--color-text-tertiary`；好评数 12pt/400 `--color-text-secondary` |
| 卡片底 | **无卡片底、无描边** —— 图 + 文直接落在 Linen 页底上 |
| 按下 | scale **.98** + 变 `--press`，120ms in / 200ms out |
| 同屏约束 | 商品图是首页次要焦点（Banner 是唯一大图块）；商品名禁金色 |

### 4.8 Banner 大卡

| 项 | 规格 |
|----|------|
| 尺寸 | 361 × **203pt**（16:9），左右边距 16pt |
| 圆角 | `--radius-banner` **18pt**，`overflow: hidden` |
| 描边 | 无 |
| 阴影 | `--shadow-card` |
| 压字遮罩 | `--scrim-banner` 三段式 `linear-gradient(to top, rgba(46,43,41,.80) 0%, rgba(46,43,41,.42) 42%, rgba(46,43,41,0) 78%)` —— **删除现状黑底 35% 矩形块**；两段式 .58 会把白字压到 2.77 ✗，必须三段式（实测最差 4.88 ✅） |
| 标题 | 17pt / 600 / 行高 22pt，`#FFFFFF`，左 16pt，底 **20pt**（白字对压暗区最差 4.88 ✅；原 14pt 与指示点距底 12pt 数学互斥，必然叠压 2pt，见审查 P1-1） |
| Tag | 底 `--color-primary`，文字 `--on-primary` 12pt/700，padding 4×8pt，radius 6pt；**禁金色底 + 白字**（1.65 ✗）；tag 紧贴标题左侧，间距 8pt |
| 指示点 | iOS 胶囊式：未选 **4pt 圆点** `rgba(255,255,255,.55)`；选中 **12×4pt 胶囊** `#FFFFFF`；间距 6pt；水平居中，**距底 6pt**（与标题底 20pt 拉开 ≥10pt 间隙，消除叠压；圆点对压暗区 3.66 ✅ ≥3） |
| 轮播 | 自动 3000ms；指示点跟手 |
| 命中区 | 整卡 |

### 4.9 金刚区快速入口

| 项 | 规格 |
|----|------|
| 容器 | 白卡（`--color-surface`），radius `--radius-group` 14pt，**无描边**，左右边距 16pt，上下 padding 16pt |
| 列 | 4 列等分，列宽 84.25pt，列间距 8pt |
| 图标底 | **56 × 56pt**，radius `--radius-well` **16pt**，底 **`--color-fill-dim` #DCD6CC**（对白卡 **1.45:1** ✅ 修掉现状 1.09:1） |
| 图标 | 24pt，色 `--ink-gold`（对底 4.49 ✅ ≥3） |
| 标签 | 13pt / 400 / `--color-text-secondary`，图标底下方 8pt，居中，1 行 |
| 命中区 | 整列（图标底 56pt 已 ≥44pt） |
| 按下 | 图标底变 `--press`，scale .98 |

> **4 个入口**（需求摘要确认）：分类 / 会员 / 领券 / 限时。
> **产品线入口职责划分**（解决 D2 决策遗留问题）：金刚区「分类」→ 打开独立分类页（路由 `pages/tabbar/category`，不在本次 5 屏内，做跳转占位）；首页 chip 行 Care/Gear/Play/Home → **在首页原地筛选商品**（切换下方商品网格数据源）。二者**不重叠**：一个是"去分类页"，一个是"本页筛选"。

### 4.10 Pet Hub 专用组件

#### 4.10.1 沉浸式 Hero

| 项 | 规格 |
|----|------|
| 容器 | 全幅（左右出血 0），高 = **60vh**（clamp 460pt – 520pt），顶端从 `--safe-top` 下方开始 |
| 图片 | `object-fit: cover`，`object-position: 50% 50%`（详见 §7 裁切规范） |
| 顶部遮罩 | `--scrim-hero-top`（`.50 → .12 → 0`，34% 内结束） |
| 底部遮罩 | `--scrim-hero-bottom`（`.45 → 0`，28% 内结束）—— 与护理卡上叠区衔接 |
| 粒子光点 | 保留但**降级**：仅 3 粒、opacity ≤ .4、`prefers-reduced-motion` 下全部移除 |
| 底部衔接 | 护理卡组**上叠 16pt**（负 margin −16pt），压住场景底部 |

#### 4.10.2 毛玻璃浮层（信息面板 / 胶囊按钮）

> ⚠ **关键修正**：需求摘要要求"半透明白底"。**实测白色玻璃在照片上白字仅 2.39–3.06:1，不达标。**改用**深色玻璃**。

| 项 | 规格 |
|----|------|
| 底 | `--glass-dark` `rgba(46,43,41,.32)` + `backdrop-filter: blur(20px) saturate(180%)` |
| 描边 | 0.5pt `--glass-border` `rgba(255,255,255,.18)` |
| 降级（无 backdrop-filter） | `rgba(46,43,41,.72)` 纯色 |
| 文字 | **白色**，白字对玻璃实测 **≥4.81:1** ✅（顶部亮区 4.81 / 暗区 7.68 / rail 带 5.82） |
| 信息面板（左上） | padding 10×14pt，radius 16pt，max-width 240pt；kicker 12pt/`rgba(255,255,255,.85)`，title 17pt/600 白 |
| 胶囊按钮（右上） | 高 **32pt**，radius 16pt（pill），padding 0 12pt；图标 15pt 白 + 文字 13pt/600 白；图标↔文字 6pt |
| 两个胶囊间距 | 8pt |
| 按下 | scale **.97**，120ms |

#### 4.10.3 宠物切换 rail

| 项 | 规格 |
|----|------|
| 容器 | 横向 scroll，**无滚动条**（`show-scrollbar=false`），左 padding 16pt，右 padding 16pt |
| 头像 | **44pt** 圆形；内圈 2pt `rgba(255,255,255,.9)` 白边 |
| 未选中环 | 1.5pt `rgba(255,255,255,.45)` |
| **选中环** | **3pt `#FFFFFF` 实心环** + `scale 1.06` + 外光晕 `0 0 0 4px rgba(219,201,138,.45)` |
| ⚠ 修正 | 需求摘要的"金色环"在照片亮区仅 **2.23:1**（对白环 3.67:1）→ **改白环 + 金色光晕**。金色环不能独立承担选中语义 |
| 头像间距 | 12pt |
| 占位"添加宠物" | 44pt 圆形 + `--glass-dark` 底 + paw 图标 22pt 白 |

#### 4.10.4 护理状态三卡

| 项 | 规格 |
|----|------|
| 容器 | **一张白卡内三列**（iOS 分组列表首组三列 stat），radius 14pt，无描边，**无投影** |
| 列宽 | 120.3pt（361/3）；列间 **0.5pt `--separator` 竖线**，上下各内缩 12pt |
| 图标底 | **28pt 圆形**，底 = 语义弱底（洗护=成功弱底 / 疫苗=警告弱底 / 驱虫=危险弱底）；图标 16pt 语义墨色 |
| 标签 | 13pt / 400 / `--color-text-secondary`（洗护 / 疫苗 / 驱虫） |
| 数值 | 17pt / 700 / `--color-text`（最近记录值），行高 22pt |
| 状态词 | 12pt / **500** / 语义墨色（正常=success ink / 将到期=warning ink / 已到期=danger ink） |
| 卡内 padding | 12pt |
| 按下 | 整列变 `--press`（**不缩放**，三列在同卡内缩放会撕裂分隔线） |
| 禁止 | 三张独立描边卡（现状）、金色状态词 |

#### 4.10.5 提醒条（inline banner）

| 项 | 规格 |
|----|------|
| 结构 | **左 3pt 语义色竖条** + 弱底 + 文字块 + 「更多」按钮 |
| 底 | 语义弱底（到期=危险弱底 / 今天=警告弱底 / 正常=成功弱底） |
| 竖条 | 3pt 宽，语义实色（危险用 `--color-danger-solid`，对危险弱底 **4.08:1** ✅ ≥3） |
| 圆角 | `--radius-media` 12pt；圆角裁剪（竖条跟随左侧圆角） |
| 高度 | ≥ 44pt；内 padding 12pt × 14pt |
| 主文案 | 15pt / 500 / `--color-text`（如「下次驱虫」） |
| 数值 | 15pt / 700 / `--color-text`（如「还有 3 天」） |
| 「更多」 | 文字按钮 13pt/600 `--ink-gold` + chevron 12pt `--ink-gold`；命中区 44×44pt |
| 禁止 | 促销红条、满幅红底、金色底 |

#### 4.10.6 快捷操作宫格（5 项）

| 项 | 规格 |
|----|------|
| 布局 | 5 列，列宽 65.8pt，列间距 8pt |
| 图标底 | **44 × 44pt**，radius `--radius-well` 16pt，底 `--color-fill-dim` |
| 图标 | 22pt，`--ink-gold` |
| 标签 | 13pt / 400 / `--color-text-secondary`，上方 8pt，1 行 |
| 5 项 | 购买（shopping-bag）/ 晒宠（camera）/ 日历（calendar）/ 记忆（image）/ 体重（scale） |
| 禁止 | 每项不同色（会与产品线 chip 抢语义）；彩色描边 |

### 4.11 底部 TabBar

| 项 | 规格 |
|----|------|
| 高度 | 49pt + 安全区 34pt = **83pt** |
| 底 | `--nav-veil` `rgba(255,255,255,.88)` + `blur(20px) saturate(180%)`；降级 `--nav-veil-solid` |
| 顶部 hairline | 0.5pt `--separator`（**取代现状 `borderStyle:"black"` 的 1px 实线**） |
| 项 | 5 项：宠物 / 商城 / 社区 / 消息 / 我的 |
| 图标 | 盒 29×29pt，内部线性图标 24pt |
| 标签 | **11pt** / 500 / 行高 14pt（**非现状 10pt**，见 §8 裁决 #6） |
| 图标↔标签 | 2pt；上下内边距各 2pt（29+2+14+2+2 = 49 ✓） |
| **选中态** | 图标 + 标签 **`--ink-gold`**（对 veil **6.39:1** ✅）；**禁用 `--color-primary`**（1.65 ✗） |
| **未选中** | 图标 + 标签 **`--color-text-secondary`**（对 veil **5.36:1** ✅）；**禁用 tertiary**（2.96 ✗，现状 #9A948C 即此问题） |
| 角标 | 见 §4.12，贴图标右上 |
| 按下 | scale .97，120ms |
| 命中区 | 每项 ≥ 44×44pt |

### 4.12 Badge / 红点

| 类型 | 规格 | 对比度 |
|------|------|-------|
| **红点** | **8pt** 圆 + **2pt 背景色环**（环色 = 宿主表面色：白卡用 #FFFFFF，TabBar 用 `--nav-veil`） | `--color-danger-solid` 对白 4.77 ✅ |
| **数字角标** | 高 **18pt**，min-width 18pt，radius 9pt（pill），左右 padding 5pt；底 `--color-danger-solid`；文字白 **11pt/600** | 白字 **4.77:1** ✅ AA |
| 上限 | `99+` | — |
| 位置 | 贴图标右上角（红点中心距图标右上 4pt；数字角标右上对齐 +6 / −6） | — |
| 规则 | **金色不承担"未读"语义**；TabBar 角标与导航栏角标样式**完全一致** | — |
| 待处理态 | 已读角标**消失**（不是变灰） | — |

### 4.13 按钮

| 类型 | 规格 |
|------|------|
| **主 CTA**（唯一实心金色） | 高 **50pt**，radius `--radius-media` 12pt（iOS 连续曲率），底 `--color-primary`，文字 `--on-primary` **17pt/600**（对金 **8.52 ✅**），左右 padding 20pt；**全站同一屏最多 1 个实心金色 CTA** |
| 次级按钮 | 高 **44pt**，radius 12pt，底 `--color-fill`，文字 `--color-text` 17pt/400 |
| 描边按钮 | 高 44pt，radius 12pt，**0.5pt `--separator`** 描边，文字 `--color-text` 17pt/400 |
| 文字按钮 | 命中区 44pt 高，文字 17pt/400 `--ink-gold`（品牌动作）或 `--color-text`（导航动作） |
| 图标按钮 | 命中区 **44×44pt**，图标 22pt `--color-text-secondary`（导航栏）/ `--color-text`（工具栏） |
| 胶囊按钮 | 高 32pt，radius 16pt（pill），文字 13pt/600；用于 chip 区与 Sheet |
| 禁用态 | 底 `--color-fill-dim-strong`，文字 `--color-text-tertiary`，`opacity .5`；**不透明**（避免透出下层） |
| 加载态 | 图标替换为 18pt 旋转指示器，`--ease-standard` 1s 线性循环；文字保留 |
| 按下 | scale **.97** + 底 `--press`，120ms in / 200ms out |
| 禁止 | 白字在金底（1.65 ✗）、金底金字（3.93 ✗）、多档圆角浮起 |

### 4.14 Sheet / Action Sheet（自绘，禁用 uni 原生）

> 现状 `community.vue` 用 `uni.showActionSheet`（原生外观割裂）与 `uni.showToast` 分享提示 → **全部改为自绘 iOS 组件**。

#### 4.14.1 底部 Sheet（装扮弹层 / 通用 Sheet）

| 项 | 规格 |
|------|------|
| 底 | `--color-surface` #FFFFFF |
| 顶部圆角 | `--radius-sheet` **20pt** |
| 拖拽把手 | 36 × 5pt，radius 2.5pt，色 `--color-fill-dim-strong`；上边距 8pt，居中 |
| 标题 | 17pt / 600 / `--color-text`，居中，上边距 12pt |
| 关闭 | 右上「关闭」文字按钮 15pt `--ink-gold`，命中区 44×44pt |
| 内容 | 3 个 tab 用 **Segmented Control**（§4.2）置于标题下 16pt，左右 16pt |
| 安全区 | 底部 padding = `--safe-bottom` 34pt |
| 遮罩 | `rgba(46,43,41,.40)`，点击关闭 |
| 入场 | translateY(100%) → 0，350ms `--ease-emphasis`；遮罩 200ms 淡入 |
| 退场 | translateY(0) → 100%，250ms `--ease-exit` |
| 手势 | 下拉跟手；释放速度 > 0.5pt/ms 或位移 > 25% 则关闭 |
| 圆角覆盖 | 顶部 20pt 圆角之外不加描边 |

#### 4.14.2 Action Sheet（⋯ 菜单）

| 项 | 规格 |
|------|------|
| 底 | `--nav-veil` + blur（浮在遮罩上）；圆角 `--radius-group` 14pt |
| 与屏幕边距 | 左右 8pt；组间距 8pt |
| 选项行 | 高 **57pt**，文字 **17pt/400** 居中，色 `--color-text`；destructive 行色 **`--color-danger-solid`**（对白 4.77 ✅） |
| destructive 加粗 | iOS destructive 为 Regular 字重，**不加粗** |
| 分组分隔 | 组间 8pt；同组内 0.5pt `--separator` |
| 「取消」按钮 | **独立分组**（与选项组间 8pt 间隙），高 57pt，文字 17pt/**600** `--color-text` |
| 遮罩 | `rgba(46,43,41,.40)` |
| 动作（社区 ⋯） | 删除（destructive，仅自己的帖子）/ 不感兴趣 / 举报 |
| 手势 | 下拉关闭，同上阈值 |

### 4.15 按压态规范（全局）

```
可点元素：
  小元素  transform: scale(.97)  +  背景 --press   120ms in / 200ms out
  卡片    transform: scale(.98)  +  背景 --press   120ms in / 200ms out
  列表行  不缩放                +  背景 --press   120ms in / 200ms out
全局禁止：任何 :hover 效果
```

- `--press` = `#ECE6DD`（对白 1.24，**是变灰不是变色**）
- 按下**无 300ms 延迟**
- 拖拽跟手期间（FAB、Segmented、Sheet 手势）关闭过渡动画
- 被遮挡/禁用的元素不响应按压态

### 4.16 空态 / 骨架屏 / 错误态

#### 4.16.1 空态

| 项 | 规格 |
|------|------|
| 容器 | 垂直居中偏上（顶部留 96pt），左右 32pt |
| 图形 | 48pt 线性图标，`--color-text-tertiary`（**纯装饰**，允许 3.00）；**禁用 emoji**；品牌 IP 插画（MILO/LUNA/ATLAS/OLIVE）**仅此可用** |
| 标题 | 17pt / 600 / `--color-text`，图形下方 16pt |
| 说明 | 15pt / 400 / `--color-text-secondary`，标题下方 8pt，2 行居中 |
| 动作 | 主/次按钮，标题下方 24pt |
| 文案 | 按 tab 区分（社区推荐/关注/话题 三套文案），**不编造统计数字** |

#### 4.16.2 骨架屏

| 项 | 规格 |
|------|------|
| 占位块 | 底 `--color-fill`，radius 随真实组件（商品图 12pt / 行块 6pt / 头像 50%） |
| 呼吸 | opacity .6 ↔ 1，1200ms 循环（`--dur-skeleton`），**不用横向流光**（廉价感） |
| 降级 | `prefers-reduced-motion` 下停止呼吸，固定 opacity .8 |
| 结构 | 与真实内容 1:1 对应（同样的行高/间距），**切态不引起纵向位移** |
| 商品区 | 2 列 × 3 行占位块 |

#### 4.16.3 错误态

| 项 | 规格 |
|------|------|
| 图形 | 48pt，`--color-danger-ink` #8E3F35（3.0+ 图形 ✅） |
| 标题 | 17pt / 600 / `--color-text`，图形下方 16pt |
| 说明 | 15pt / 400 / `--color-text-secondary` |
| 重试 | 文字按钮 17pt/400 `--ink-gold`，说明下方 16pt，命中区 44pt |
| 底 | 无卡片包裹，直接落在页面底上 |
| 禁止 | 满幅红底、金色图标 |

#### 4.16.4 未登录态

| 项 | 规格 |
|------|------|
| 消息页 | 白卡（radius 14pt，无描边）内：标题 17pt/600 + 说明 15pt secondary + **登录按钮**（主 CTA，50pt，底 `--color-primary` 文字 `--on-primary`） |
| 我的页 | 默认头像 72pt + 「登录 / 注册」17pt/600 + 副标 13pt secondary + 登录胶囊按钮（`--color-primary` 底 + `--on-primary` 字） |
| 禁止 | 用 tertiary 色写"未登录"说明（不承载信息） |

---

## §5 图标策略

### 5.1 生产 vs 原型

| 环境 | 方案 | 理由 |
|------|------|------|
| **生产** | 保留 `lucide.ttf` 字体图标（148 类，`lucide.css` 已就绪，H5/APP 跨端可用） | `pet.vue` 已记录 APP 端内联 SVG 不可见的坑；字体图标是既成事实 |
| **原型** | **内联 SVG**，与 lucide 图形语言严格一致 | 原型需独立离线、无字体依赖、可控描边粗细 |

> 两边的**图标语义、尺寸、颜色 token 必须一一对应**，以便 Phase 3 → 生产迁移零成本。

### 5.2 原型内联 SVG 写法约定

```html
<!-- 线性图标（lucide 标准） -->
<svg class="icon" width="24" height="24" viewBox="0 0 24 24"
     fill="none" stroke="currentColor" stroke-width="2"
     stroke-linecap="round" stroke-linejoin="round"
     aria-hidden="true" focusable="false">
  <path d="…"/>
</svg>
```

**约定（Phase 3 必须遵守）**：
1. **视窗固定 `0 0 24 24`**，`stroke-width="2"`，`stroke-linecap="round"`，`stroke-linejoin="round"`，`fill="none"`
2. **颜色一律继承 `currentColor`**，SVG 内**不写死色值**；颜色由外层容器的 `color` token 决定
3. `width`/`height` 由 CSS 尺寸 token 控制（`.icon{width:24px;height:24px}`），SVG 属性只作兜底
4. **实心变体**（评分星、已点赞心）单独定义 `fill="currentColor" stroke="none"` 版本
5. **禁止 emoji 当图标**（❌）；禁止引入 Material Symbols / Font Awesome
6. `aria-hidden="true"` + `focusable="false"`；纯装饰图标不加 `role`
7. 图标在 flex 容器中 `flex-shrink: 0`；光学对齐用 `vertical-align: -0.125em`
8. **图标颜色 token 映射**：导航栏动作 `--color-text-secondary` · 工具栏 `--color-text` · 列表 chevron `--color-text-tertiary` · 品牌标记 `--ink-gold` · 照片上 `--color-surface`（白）· 状态语义 → 语义墨色

### 5.3 图标清单（按 5 屏归类，全部取自现有 lucide 148 类）

#### TabBar（5）
| 屏 | 图标 | lucide 类 |
|----|------|-----------|
| 宠物 | 爪印 | `luc-paw-print` |
| 商城 | 购物袋 | `luc-shopping-bag` |
| 社区 | 用户群 | `luc-users` |
| 消息 | 信封 | `luc-mail` |
| 我的 | 人像 | `luc-user` |

#### ① Pet Hub
| 用途 | lucide 类 |
|------|----------|
| 宠物切换占位 | `luc-paw-print` |
| 装扮胶囊 | `luc-shirt` |
| 3D 空间胶囊 | `luc-sparkles` |
| 护理-洗护 | `luc-bath` |
| 护理-疫苗 | `luc-syringe` |
| 护理-驱虫 | `luc-pill` |
| 提醒条更多 | `luc-more-horizontal` |
| 快捷-购买 | `luc-shopping-bag` |
| 快捷-晒宠 | `luc-camera` |
| 快捷-日历 | `luc-calendar` |
| 快捷-记忆 | `luc-image` |
| 快捷-体重 | `luc-scale` |
| 状态-正常 | `luc-check-circle` |
| 状态-将到期 | `luc-clock` |
| 状态-已到期 | `luc-alert-triangle` |

#### ② 商城首页
`luc-search`（搜索）· `luc-scan-line`（扫码）· `luc-bell`（通知）· `luc-grid`（金刚区-分类）· `luc-crown`（金刚区-会员）· `luc-ticket`（金刚区-领券）· `luc-clock`（金刚区-限时）· `luc-chevron-right`（指示点/箭头）· `luc-star`（评分，**实心变体**）· `luc-flame`（今日爆款）· `luc-thumbs-up`（口碑好评）· `luc-heart`（猜你喜欢）· `luc-shopping-bag`（商品）

#### ③ 社区
`luc-search`（搜索）· `luc-bell`（通知 + 红点）· `luc-camera`（FAB 发布）· `luc-heart`（点赞，**实心变体**）· `luc-message-circle`（评论）· `luc-share-2`（分享）· `luc-more-horizontal`（⋯ 菜单）· `luc-trash-2`（删除）· `luc-thumbs-down`（不感兴趣）· `luc-flag`（举报）· `luc-user`（头像占位）· `luc-image`（图占位）

#### ④ 消息中心
`luc-bell`（通知 + 角标）· `luc-users`（群聊）· `luc-user-plus`（加好友）· `luc-headphones`（客服会话头像）· `luc-clock`（会话时间）· `luc-trash-2`（左滑删除 / 长按删除）· `luc-arrow-up`（置顶，**lucide 无 pin，此为近似**）· `luc-chevron-right`（进入）

#### ⑤ 我的
`luc-chevron-right`（进入/箭头）· `luc-crown`（会员卡 / 会员中心）· `luc-footprints`（足迹）· `luc-coins`（积分）· `luc-ticket`（优惠券）· `luc-heart`（收藏 / 粉丝）· `luc-users`（关注 / 社交）· `luc-user`（个人中心 / 头像）· `luc-credit-card`（待付款）· `luc-shopping-cart`（购物车）· `luc-truck`（待收货）· `luc-star`（待评价）· `luc-calendar`（每日签到）· `luc-clipboard-list`（任务中心）· `luc-gift`（邀请好友）· `luc-map-pin`（收货地址）· `luc-paw-print`（宠物档案）· `luc-bookmark`（我的收藏）· `luc-history`（浏览足迹）· `luc-help-circle`（帮助中心）· `luc-settings`（设置）· `luc-info`（关于我们）· `luc-log-out`（退出）

#### 全局通用
`luc-x`（关闭）· `luc-x-circle`（取消）· `luc-check`（勾选）· `luc-chevron-left` / `luc-chevron-right` / `luc-chevron-down` · `luc-more-horizontal` · `luc-plus` · `luc-refresh-cw`（重试）· `luc-search` · `luc-alert-triangle`（错误）· `luc-inbox`（空态）· `luc-award`（品牌空态 IP）· `luc-sparkles`（魔法/新品）

> ⚠ `luc-pin` **不在**现有 148 类中，置顶用 `luc-arrow-up` 近似；若生产需真 pin 图标，需新增字形（列入 P2 素材需求）。

---

## §6 落地映射表

> **这张表是原型构建师与未来生产改造的桥梁。**分两部分：
> **A. Token 层**（§3.6 已给，此处给页面级用法）· **B. 类名/属性层**（逐条到现状类名）

### 6.1 全局 Token 替换（variables.scss / common.scss）

| 现状写法 | 位置 | 替换为 | 理由 |
|---------|------|--------|------|
| `--color-primary` 用作 `color:`（文字） | `home.vue .recommend-price`、`community.vue .tab-text-active`、`.topic-tag`、`messages.vue .section-action`、`.conv-pin`、`.empty-btn-text`、`user.vue .member-level` | **`--ink-gold`** | 金色文字唯一来源（6.49:1） |
| `--color-primary` 用作 `background:`（填充） | `home.vue .banner-tag`、`messages.vue .login-btn`、`.conv-avatar`、`user.vue .login-btn` | 保留 `--color-primary`，**文字改 `--on-primary`** | 金底白字 1.65 ✗ → 8.52 ✅ |
| `#007AFF → #004FAF` 蓝渐变 | `user.vue .vip-bg` | **`var(--brand-gradient-vip)`** | 清除 iOS 蓝；终点改 #C09A6A 使炭字 5.40 ✅ |
| `rgba(0,122,255,.3)` 蓝阴影 | `user.vue .vip-card` | **`var(--shadow-float)`** | 清除蓝色光晕 |
| `rgba(0,122,255,.35)` 蓝阴影 | `community.vue .fab` | **`var(--shadow-float)`** | 清除蓝色光晕 |
| `#ff4d4f` | `messages.vue` × 5（`.navbar-badge` `.section-badge` `.conv-unread` `.conv-swipe-btn` `.empty-btn` border）、`community.vue` `confirmColor` | **`var(--color-danger-solid)`**（底/白字）或 **`var(--color-danger-ink)`**（文字） | 白字 4.77 ✅（原 3.27 ✗） |
| `#ff4d4f` destructive 文字 | Action Sheet 删除项 | **`var(--color-danger-solid)`** | 对白 4.77 ✅ |
| `1rpx solid var(--color-divider)` 描边 | `community.vue .post-card`、`user.vue .feature-item`、`messages.vue .conv-item` | **删除描边**，改用 **0.5pt `--separator` 分隔线**（左 inset 对齐文字起点） | iOS 分组列表无描边 |
| `--color-fill` / `#f2f2f7` | 全部 | **`--color-fill` #F1EDE7**（已暖化） | 冷灰 → 暖调 |
| `--background-200`（#ede9e4）图标底 | `home.vue .kingkong-icon`、`.banner-placeholder` | **`--color-fill-dim` #DCD6CC** | 对白 1.09 → **1.45** ✅ |
| `--color-text-tertiary` 用作状态/计数/时间 | `community.vue .post-time`、`messages.vue .conv-time`、`.conv-preview`、`user.vue .email`、`.wallet-label` | **`--color-text-secondary`** | 3.00 ✗ → 5.44 ✅ |
| `#9A948C` 原生 tabBar `color` | `pages.json` | **`#6E6962`** | 3.00 → 5.36 ✅ |
| `#DBC98A` 原生 tabBar `selectedColor` | `pages.json` | **`#6E5C30`** | 1.65 → 6.39 ✅ |
| `borderStyle: "black"` | `pages.json` | 自绘 tabBar 时删除；保留原生则改 `"white"` + 顶部 hairline | 1px 实线 → 0.5pt hairline |
| `fontSize: "20px"` 图标 | `pages.json` | 自绘 tabBar 图标盒 29pt | iOS ≈ 25–29pt |
| `Playfair Display` 字体 | `common.scss .section-title/.headline-1/.headline-2` | **删除该声明**，改用系统字体栈 | 无 @font-face + 中文 fallback 宋体（X9） |
| `'Inter'` 字体 | `common.scss page{}` | 无字体文件则**移除**，仅留系统栈 | 死声明 |
| `--brand-50…800` 9 档 | `common.scss` | **删除** | 深浅两套写死，无法跟随主题 |
| `--shadow-sm/md/lg/card-*/pop/top` | `variables.scss` | **`--shadow-card` / `--shadow-float`** | 10 档 → 2 档 |
| `--radius-sm/md/lg/pill` | `variables.scss` | 语义圆角 7 档（§3.3.1） | — |
| `--space-xs…2xl` | `variables.scss` | pt 间距 9 档（§3.3.2） | — |
| `--font-size-xs…3xl` | `variables.scss` | 语义字号 11 档（§3.2） | — |
| `--font-weight-light` | `variables.scss` | 删除 | 0 处使用 |

### 6.2 首页 `pages/tabbar/home.vue`

| 现状类名 | 现状 | 目标 |
|---------|------|------|
| `.navbar` | `background: var(--color-surface)`，无 blur | `background: var(--nav-veil)` + `backdrop-filter: blur(20px) saturate(180%)`；`padding: 0 16pt`；高 44pt + `--safe-top` |
| `.navbar-search` | 高 32pt 级（16rpx×2 padding）、底 `--color-background`、radius pill | 高 **36pt**、radius **18pt**、底 `--color-fill`、左右 padding 10pt |
| `.navbar-search-placeholder` | `--font-size-sm` + `--color-text-tertiary` | 15pt `--color-text-secondary` |
| `.navbar-icon` | 56rpx(29pt) | 命中区 **44×44pt**，图标 22pt `--color-text` |
| `.navbar-badge` | `--color-danger` 底 + 20rpx 字 | `--color-danger-solid` 底 + 白字 **11pt/600**，h **18pt** |
| `.banner` | `margin: 24rpx 32rpx`、radius 24rpx、`shadow-pop` | 左右 margin **16pt**、radius **18pt**、`--shadow-card` |
| `.banner-title-overlay` | **`background: rgba(0,0,0,.35)` 黑底矩形块** | **删除整块**，改 `.banner::after` 绝对定位底部渐变 `var(--scrim-banner)` |
| `.banner-tag` | `--color-primary` 底 + `#2e2b29` 字 | 保留 `--color-primary` 底，文字改 **`var(--on-primary)`** 12pt/700，radius 6pt |
| `.banner-title-text` | `#fff` 26rpx/600 | **17pt/600**，左 16pt、底 14pt |
| Banner 指示点 | uview `indicator-active-color="#2E2B29"` | **自绘**：未选 4pt `rgba(255,255,255,.55)`，选中 12×4pt `#FFFFFF`，间距 6pt，居中距底 6pt（标题底 20pt，二者不叠压） |
| `.kingkong` | 白卡 radius 24rpx | 白卡 radius **14pt**，无描边，上下 padding 16pt |
| `.kingkong-icon` | 96rpx(48pt)、radius `--radius-md`、`--background-200` | **56×56pt**，radius **16pt**，底 **`--color-fill-dim`** |
| `.kingkong-emoji` | 44rpx 图标 | 24pt `--ink-gold`（内联 SVG，**禁 emoji**） |
| `.kingkong-label` | `--font-size-xs` | **13pt** `--color-text-secondary` |
| `.recommend-tabs` | `border-bottom: 1rpx solid var(--color-divider)` | **删除**，换 **Segmented Control**（§4.2），轨道底 `--surface-raised` + 0.5pt `--separator` |
| `.recommend-tab-indicator` | 64×4rpx 金色下划线 | **删除**，换 29pt 白胶囊 + `--shadow-card`，位移 250ms |
| `.recommend-tab-label` | tertiary → active text | 未选 `--color-text` 13pt/400；选中 `--color-text` 13pt/**600** |
| `.recommend-grid` | 2 列 gap 16rpx | 2 列 gap **12pt** |
| `.recommend-card` | 白底 + `1rpx solid var(--color-divider)` + radius `--radius-md` | **删除描边与卡片底**；图 1:1 radius **12pt** + `--shadow-card`；文字直接落 Linen |
| `.recommend-name` | `--font-size-sm` 单行截断 | **15pt/400，2 行截断**，行高 20pt |
| `.recommend-desc` | `--font-size-xs` tertiary 单行 | 13pt `--color-text-secondary` 单行 |
| `.recommend-price` | **`--color-primary` 金色文字** 28rpx/600 | **`--ink-gold`** 17pt/600，`tabular-nums` |
| `.recommend-original` | tertiary 划线 | 13pt `--color-text-secondary` 划线 |
| `.recommend-star` | `--color-warning` 22rpx | **12pt `--color-primary` 实心星** |
| `.recommend-rating-num` | `--font-size-xs` + medium | 12pt/**500** `--color-text` |
| `.recommend-rating-count` | tertiary | 12pt `--color-text-secondary` |
| `.recommend-rating-sep` | `--color-divider` | 12pt `--color-text-tertiary` |
| **新增** 产品线 chip 行 | — | Care/Gear/Play/Home 横滑（§4.6），置于金刚区与 Banner 之间，容器左右 padding 16pt |
| `u-icon`（search/camera-fill/bell-fill） | **uview 图标 + 写死 color**（X1） | **全部换内联 lucide SVG**（§5.2），颜色走 token |

### 6.3 社区 `pages/tabbar/community.vue`

| 现状类名 | 现状 | 目标 |
|---------|------|------|
| `.top-bar` | `padding-top: 12rpx + safe-top` | `padding-top: var(--safe-top)`；工具条高 **44pt** |
| `.top-title` | **40rpx(20.8pt)/700 固定** | **Large Title 34pt/700**，置于工具条下方 52pt 高的标题区（滚动内容流内），**随滚动折叠** |
| （新增）居中小标题 | 无 | 折叠后 17pt/600 居中淡入（§4.1.1） |
| `.icon-btn` | 60rpx(31pt) | 命中区 **44×44pt**，图标 22pt |
| `.badge-dot` | 12rpx 圆 `--color-danger` | **8pt** 圆 + 2pt 白色环，底 `--color-danger-solid` |
| `.tabs` / `.tab` / `.tab-indicator` | 下划线 tab（80rpx 高 + 底部金条） | **Segmented Control** 33pt，轨道 `--surface-raised` + hairline，选中白胶囊 `--shadow-card`，位移 250ms |
| `.tab-text-active` | **`--color-primary` 金色文字** | `--color-text` 13pt/600（白胶囊上） |
| `.search-field` | 72rpx(36pt) 高、底 `--color-fill`、radius 36rpx | 高 **36pt**、radius **18pt**、底 `--color-fill`（不变）、左右 padding 10pt |
| `.search-placeholder` | tertiary 26rpx | 15pt `--color-text-secondary` |
| `.search-icon` | 30rpx | 16pt `--color-text-secondary` |
| `.topic-bar` | `padding: 16rpx` | 左右 padding 16pt，**两端无渐隐** |
| `.topic-tag` | 高 56rpx(29pt)、底 `--color-primary-bg`、**金色文字** | 高 **32pt**、radius **16pt**、未选底 `--color-fill-dim` + `--color-text` 13pt/400；选中底 `--color-primary-bg` + `--ink-gold` 13pt/500 + **0.5pt `--ink-gold` 描边**；命中区扩 44pt |
| `.feed` | `padding: 0 16rpx`（8pt） | **`padding: 0 16pt`** |
| `.post-card` | 白底 + `1rpx solid var(--color-divider)` + radius 24rpx + `shadow-card-sm` + `margin-top:16rpx` | **删除描边与卡片阴影**；改**无框 Feed**：作者行 → 图片 → 正文 → 互动行 直接排在 Linen 底上，帖子之间 **12pt** 间距；如需分隔用 0.5pt `--separator`（插图下方） |
| `.post-header` | `padding: 20rpx 20rpx 16rpx` | padding 0 16pt；高 **56pt**；头像 **36pt** |
| `.post-avatar` | 72rpx(36pt) | **36pt**（保持），兜底底 `--color-fill-dim` + 字 `--color-text`（现状金底白字 1.65 ✗） |
| `.post-username` | 28rpx/600 | 15pt/**600** `--color-text` |
| `.post-time` | 22rpx tertiary | 12pt `--color-text-secondary` |
| `.more-btn` | 56rpx(29pt) | 命中区 **44×44pt**，`luc-more-horizontal` 20pt `--color-text-secondary` |
| `.post-image-wrap` | `padding: 0 20rpx 12rpx`、单图 `widthFix` 无高度上限 | 单图 **限高 240pt 且比例 ≤ 4:3**（`aspect-ratio` + `max-height`），radius **12pt**；九宫格 cell radius 8pt；图片与正文间 **12pt** |
| `.post-content` | **30rpx(15.6pt)/600** | **17pt/400**，行高 **26pt**（1.53 ✅），色 `--color-text` |
| `.post-actions` | 无上边框 | **上方 0.5pt `--separator`**（左右 inset 16pt），行高 **44pt** |
| `.action-count` | 24rpx/500 tertiary | 13pt/**500** `--color-text-secondary` |
| `.action-icon` | 30rpx | 22pt；已点赞心形用 **`--color-danger-solid` 实心**（现状 `--color-danger` 3.57 ⚠） |
| `.fab` | 112rpx(58pt) 金底白字 + **蓝阴影** | **56pt** 圆、底 `--color-surface` + `--shadow-float`、图标 24pt `--color-text`；删除 `rgba(0,122,255,.35)` |
| `.fab-icon` | 48rpx 白 | 24pt `--color-text` |
| `uni.showActionSheet`（2 处） | 原生外观 | **自绘 iOS Action Sheet**（§4.14.2） |
| `confirmColor: '#ff4d4f'` | 确认框红 | **`'#B4574A'`** |

### 6.4 消息 `pages/tabbar/messages.vue`

| 现状类名 | 现状 | 目标 |
|---------|------|------|
| `.navbar` | 白底 sticky | Large Title 形态（§4.1.1），初始透明 → 滚动 `--nav-veil` + blur |
| `.navbar-title` | 32rpx(16.6pt) | **Large Title 34pt/700**；折叠后居中 17pt/600 |
| `.navbar-icon` | 56rpx(29pt) | 命中区 **44×44pt**，图标 22pt |
| `.navbar-badge` | **`#ff4d4f`** 底 + 20rpx 字 | **`--color-danger-solid`** 底 + 白字 **11pt/600**，h **18pt** |
| `.login-card` | 白卡 radius 24rpx padding 48rpx | 白卡 radius **14pt** 无描边，padding 32pt 24pt |
| `.login-btn` | `--color-primary` 底 + **`#fff`** 字 | 底 `--color-primary`，文字 **`--on-primary`** 17pt/600，radius pill，高 44pt |
| `.notice-card` | 白卡 + `0 4rpx 24rpx rgba(0,0,0,.06)` 阴影 | 白卡 radius **14pt** **无阴影**，**无描边**；4 格等分 |
| `.notice-icon-wrap` | 72rpx 圆、底 `--color-background` | 44pt 圆、底 `--color-fill-dim` |
| `.notice-icon` | 36rpx `--color-text` | 22pt `--ink-gold` |
| `.notice-dot` | 14rpx `#ff4d4f` | **8pt** + **2pt 白环**，`--color-danger-solid` |
| `.notice-label` | `--font-size-xs` secondary | **12pt** `--color-text-secondary` |
| `.section-head` | `padding: 24rpx 24rpx 12rpx` | 组头 13pt/400 `--color-text-secondary`，左 **卡左 + 16pt**；右侧「发起私信」13pt/**600** `--ink-gold` |
| `.section-title` | 28rpx/600 | **13pt/400** secondary（组头小标题规格） |
| `.section-badge` | **`#ff4d4f`** | **`--color-danger-solid`** + 白字 11pt/600，h 18pt |
| `.section-action` | **`--color-primary`** | **`--ink-gold`** 13pt/600 |
| `.conv-list` | `margin: 0 24rpx` + radius 24rpx | Inset Grouped：左右 **16pt**，radius **14pt**，无描边无阴影 |
| `.conv-item` | `padding: 24rpx` + `border-bottom: 1rpx solid var(--color-divider)` | 高 **76pt**；**删除 border**，改 0.5pt `--separator` **左 inset 76pt**（对齐文字起点）；组末行无线 |
| `.conv-avatar` | 88rpx(45.8pt) **金底白字** | **48pt** 圆；兜底底 `--color-fill-dim` + 字 `--color-text`（金底白字 1.65 ✗） |
| `.conv-avatar-cs .luc` | 白字 | 22pt `--ink-gold`（客服耳机） |
| `.conv-name` | 28rpx/500 | 17pt/400 `--color-text` |
| `.conv-pin` | **`--color-primary`** | **`--ink-gold`** 16pt |
| `.conv-time` | `--font-size-xs` tertiary | **12pt** `--color-text-secondary` |
| `.conv-preview` | `--font-size-sm` tertiary | **15pt** `--color-text-secondary` 单行截断（14pt 不在字号阶梯内） |
| `.conv-unread` | **`#ff4d4f`** + 20rpx 字 | **`--color-danger-solid`** + 白字 **11pt/600**，h **18pt** |
| `.conv-swipe-btn` | 140rpx(72.8pt) **`#ff4d4f`** | 宽 **80pt**，底 **`--color-danger-solid`**，白字 16pt（4.77 ✅） |
| `.empty-btn` | `1rpx solid var(--color-primary)` 描边 | 描边按钮：**0.5pt `--separator`**，文字 `--color-text` 17pt/400 |
| `.empty-btn-text` | **`--color-primary`** | **`--ink-gold`** 17pt/400 |
| `.empty-text` | `--font-size-sm` tertiary | 15pt `--color-text-secondary` |
| `uni.showActionSheet`（长按菜单） | 原生 | **自绘 Action Sheet**（§4.14.2） |

### 6.5 我的 `pages/tabbar/user.vue`

| 现状类名 | 现状 | 目标 |
|---------|------|------|
| `.header` | `padding: 48rpx 24rpx` + safe-top | 透明 nav 形态（§4.1.3）；顶部 padding = `--safe-top` + 44pt 导航预留 + 24pt |
| `.avatar` | 120rpx(62.5pt) | **72pt** 圆 |
| `.name` | `--font-size-lg`(16.6pt)/600 | **22pt/700** `--color-text` |
| `.email` | `--font-size-sm` tertiary | 13pt `--color-text-secondary` |
| `.member-level` | **`--color-primary-dark` 金字** 22rpx | **`--ink-gold`** 12pt/**600**，底 `--color-primary-bg` radius 6pt padding 2×8pt |
| `.login-btn` | `--color-primary` 底 + `--color-text` 字 | 底 `--color-primary`，字 **`--on-primary`** 15pt/600，radius pill，高 44pt |
| `.vip-card` | 高 200rpx(104pt) + **蓝阴影** | 高 **132pt**，radius **20pt**，阴影 **`--shadow-float`**（删除 `rgba(0,122,255,.3)`） |
| `.vip-bg` | **`linear-gradient(135deg, var(--color-primary,#007aff) 0%, #004fad 100%)`** | **`var(--brand-gradient-vip)`**（终点 #C09A6A，炭字 5.40 ✅） |
| `.vip-content` | `color: #f6f2ee` | **`var(--on-primary)`** #2E2B29（现状白字在金端仅 1.48 ✗） |
| `.vip-title` | `--font-size-xl`(18.7pt)/700 | **17pt/600** |
| `.vip-points` | **`--color-primary` 金字** 30rpx | **`--on-primary`** **28pt/700**，`tabular-nums`（金色底上金色字 3.93 ✗） |
| `.vip-tip` | 22rpx opacity .7 | 13pt/400 `--on-primary` @ 80% 不透明 |
| `.vip-arrow` | `#f6f2ee` opacity .7 | `--on-primary` @ 70% |
| `.card`（7 处） | 白底 radius 24rpx + `0 4rpx 24rpx rgba(0,0,0,.08)` | **合并为 3 张分组卡**（钱包+社交 / 订单 / 功能列表），radius **14pt**，**无描边、无阴影**（仅会员卡用 `--shadow-float`） |
| `.wallet-area` / `.social-grid` | 独立卡 | 合并为「我的资产」一组，**组头小标题 13pt**（组头在上、组卡在下，iOS 惯例） |
| `.wallet-num` | `--font-size-lg`/700 | 17pt/**700** `--color-text`，`tabular-nums` |
| `.wallet-label` | `--font-size-xs` tertiary | 12pt `--color-text-secondary` |
| `.order-item` | 图 48rpx + 标签 22rpx | 图 24pt `--ink-gold` + 标签 12pt `--color-text-secondary`；角标 `--color-danger-solid` h 18pt 白字 11pt |
| `.order-badge` | `--color-danger` + 20rpx 字 | **`--color-danger-solid`** + 白字 **11pt/600** |
| `.feature-item` | `border-bottom: 1rpx solid var(--color-divider)` | **删除 border**，改 0.5pt `--separator` 左 inset **44pt**（对齐文字起点）；行高 **44pt**；组末行无线 |
| `.feature-icon` | 36rpx 裸图标 | **28pt 圆角图标底**（`--color-fill-dim`）+ 16pt `--ink-gold` 图标 |
| `.feature-label` | `--font-size-base` | **15pt** `--color-text` |
| `.feature-arrow` | tertiary 32rpx | chevron 14pt `--color-text-tertiary` |
| `.footer` | `--font-size-xs` tertiary | 11pt `--color-text-tertiary`（纯装饰：版本号 + Slogan） |
| 底部 padding | `padding-bottom: 64rpx` | **`107pt`**（= tabBar 83 + 24） |

**功能列表 11 项的 3 组拆分**（每组 ≤ 5 行，组头小标题）：

| 组 | 组头 | 项（图标） |
|----|------|-----------|
| 常用 | 常用 | 每日签到（calendar）/ 任务中心（clipboard-list）/ 邀请好友（gift）/ 会员中心（crown）/ 收货地址（map-pin） |
| 账户与服务 | 账户与服务 | 宠物档案（paw-print）/ 我的收藏（bookmark）/ 浏览足迹（history）/ 帮助中心（help-circle）/ 设置（settings） |
| 关于 | 关于 | 关于我们（info）/ 退出登录（log-out，destructive 文字用 `--color-danger-ink`） |

### 6.6 Pet Hub `pages/tabbar/pet.vue`

| 现状类名 / 变量 | 现状 | 目标 |
|------------------|------|------|
| `--hub-bg` | `var(--color-fill-hi,#f7f7fa)` | **`var(--color-background)`** #F6F2EE |
| `--hub-card` | `#ffffff` | `var(--color-surface)` |
| `--hub-line` | `#e5e5ea` | **`var(--separator)`** |
| `--hub-chip` | `var(--color-fill,#f2f2f7)` | `var(--color-fill-dim)` |
| `--hub-muted` | `#8e8e93` | **`var(--color-text-secondary)`** #6E6962 |
| `--hub-ok` / `--hub-ok-bg` | `#34c759` / `#e9f9ee`（系统绿） | **`var(--ink-success)` #4E6152** / `rgba(171,185,173,.15)` |
| `--hub-err` / `--hub-err-bg` | `#ff3b30` / `#ffecea`（系统红） | **`var(--color-danger-ink)` #8E3F35** / `rgba(180,87,74,.12)` |
| `.scene` | `height: max(50vh,50vw)`、min 720rpx | **高 60vh**（clamp 460–520pt），`object-position: 50% 50%` |
| `.scene-mask` | `linear-gradient(180deg, rgba(0,0,0,.1) 0%, transparent 40%, rgba(0,0,0,.25) 100%)` | **拆为两段**：顶部 `--scrim-hero-top` + 底部 `--scrim-hero-bottom`（顶部实测需 ≥.30 才达 4.5:1） |
| `.glass` | `backdrop-filter: blur(20rpx)` + `border: 1rpx solid rgba(255,255,255,.2)` + `background: rgba(255,255,255,.2)`（**白玻璃**） | **`--glass-dark`** `rgba(46,43,41,.32)` + `blur(20px) saturate(180%)` + `0.5pt var(--glass-border)`（白玻璃实测白字仅 2.39 ✗） |
| `.scene-info` | radius 32rpx、padding 16rpx 24rpx | radius **16pt**、padding **10pt 14pt**、max-width 240pt |
| `.info-kicker` | 24rpx 白 85% | 12pt 白 85% |
| `.info-title` | 28rpx/600 白 | 17pt/600 白 |
| `.scene-top` | 顶部 `+24rpx`，右 32rpx | 顶部 `--safe-top` + 8pt，右 16pt |
| `.scene-pill` | padding 16rpx 24rpx | 高 **32pt**、padding 0 12pt、radius 16pt、图标 15pt + 文字 13pt/600 白 |
| `.avatar-ring` | 80rpx(41.6pt) 圆、2rpx 边 | **44pt** 圆；未选 1.5pt `rgba(255,255,255,.45)`；**选中 3pt `#FFFFFF` + scale 1.06 + 金色光晕** |
| `.avatar-ring.active` | `border-color: var(--color-primary)` + `box-shadow 0 0 0 6rpx rgba(219,201,138,.22)` | 改**白环 + `0 0 0 4px rgba(219,201,138,.45)` 光晕**（金环实测仅 2.23 ✗） |
| `.care-wrap` | `margin-top: -32rpx` | `margin-top: **-16pt**`（上叠 16pt） |
| `.care-grid` | 3 列 gap 24rpx | **1 张白卡内 3 列**，列间 0.5pt `--separator` 竖线（上下内缩 12pt），列宽 120.3pt |
| `.care-card` | **3 张独立描边卡** `1rpx solid var(--hub-line)` + radius 32rpx + shadow | **取消独立卡与描边** → 合并为分组卡；每列可点、按下变 `--press`（不缩放） |
| `.care-icon` | 64rpx 圆 + 系统绿/红底 | **28pt** 圆 + **语义弱底 + 语义墨色图标** 16pt |
| `.care-label` / `.care-value` / `.care-status` | 24rpx / 28rpx/700 / 20rpx | 13pt secondary / 17pt/700 text / 12pt/**500** 语义墨色 |
| `.reminder-banner` | 底 `--hub-chip` radius 32rpx | **inline banner**：语义弱底 + **左 3pt 语义色竖条** + radius 12pt + ≥44pt 高 |
| `.rm-text` / `.rm-num` / `.rm-btn` | 24rpx / 28rpx/700 / 56rpx | 15pt/500 text / 15pt/**700** text / 「更多」13pt/600 `--ink-gold` + chevron 12pt，命中 44pt |
| `.quick-grid` | 5 列 | 5 列，列宽 65.8pt，间距 8pt |
| `.quick-icon` | 裸图标 | **44×44pt 图标底** radius 16pt，底 `--color-fill-dim`，图标 22pt `--ink-gold` |
| `.quick-label` | `--font-size-xs` | 13pt `--color-text-secondary` |
| `.scene-figure` | 360rpx 绝对居中 | 保留居中；**与顶部浮层不重叠**（形象区中心下移 8%），装扮弹层内可预览 |
| `.scene-particles` | 4 粒、pulse 2.4s | 保留但降级为 3 粒、opacity ≤ .4；`prefers-reduced-motion` 移除 |
| `.dress-popup`（3 tab） | 现状弹层 | **底部 Sheet**（§4.14.1）+ 内部分段用 Segmented Control |

### 6.7 `pages.json` / `theme.js`

| 位置 | 现状 | 目标 |
|------|------|------|
| `pages.json` tabBar `color` | `#9A948C` | `#6E6962`（5.36 ✅） |
| `pages.json` tabBar `selectedColor` | `#DBC98A` | `#6E5C30`（6.39 ✅） |
| `pages.json` tabBar `backgroundColor` | `#FFFFFF` | `#F6F2EE`（配合毛玻璃降级底） |
| `pages.json` tabBar `borderStyle` | `"black"` | `"white"`（自绘后此配置整体移除） |
| `pages.json` tabBar `iconfont.fontSize` | `"20px"` | `"24px"`（自绘 tabBar 后移除 iconfont 配置） |
| `theme.js syncTabBar()` | 每次主题切换刷原生 tabBar 色 | **自绘 tabBar 后此函数可删**（X3） |

---

## §7 素材清单与裁切规范

### 7.1 可用素材（无水印，根目录 12 张 + pet-hub-scene）

| 文件 | 原始尺寸 | 用途 | 裁切/规格 |
|------|---------|------|----------|
| `hero-banner.jpg` | 1200×800 | **首页 Banner（16:9）+ Pet Hub Hero（竖版裁切）** | Banner：取源 x 0–1200 / y 62–737 → 1200×675（16:9），缩放至 361×203pt |
| `post-dog.jpg` | 800×1200 | 社区单图帖（竖版） | 容器 361×240pt（4:3 上限 240pt），`object-fit: cover`，`object-position: 50% 38%`（保留头部） |
| `post-cat.jpg` | 800×800 | 社区九宫格 / 2–3 图 | 1:1 直裁，cell 圆角 8pt |
| `post-flatlay.jpg` | 800×800 | 社区九宫格 / 2–3 图 | 1:1 直裁 |
| `prod-grooming.jpg` | 700×700 | 商品图（洗护） | 1:1 直裁 174.5pt |
| `prod-bowls.jpg` | 700×700 | 商品图（食具） | 1:1 直裁 |
| `prod-plush.jpg` | 700×700 | 商品图（玩具） | 1:1 直裁 |
| `prod-harness.jpg` | 700×700 | 商品图（出行） | 1:1 直裁 |
| `prod-carrier.jpg` | 700×700 | 商品图（出行） | 1:1 直裁 |
| `prod-cattower.jpg` | 700×700 | 商品图（家居） | 1:1 直裁 |
| `prod-fountain.jpg` | 700×700 | 商品图（家居） | 1:1 直裁 |
| `pet-hub-scene.jpg` | 1672×941 | Pet Hub 场景备选 / 3D 空间占位 | 见 §7.3 |

### 7.2 Pet Hub Hero 裁切规范（明确回答）

**结论：`hero-banner.jpg` 可直接裁切使用，但建议补一张竖版素材（P1）。**

**裁切参数（可直接落地）**

```
容器：393 × H pt，H = 60vh（clamp 460pt – 520pt）
图片：hero-banner.jpg，1200×800
object-fit: cover
object-position: 50% 50%
```

**等效源窗口**（供设计核对）：

| Hero 高 | 缩放比 | 可见源宽 | 源 x 区间 | 源 y 区间 |
|---------|--------|---------|----------|----------|
| 460pt | 0.575 | 683px | 258 – 941 | 0 – 800 |
| 470pt | 0.5875 | 669px | 265 – 934 | 0 – 800 |
| 511pt | 0.6388 | 615px | 292 – 907 | 0 – 800 |

**裁切结果评估**（对 470pt 版本实测）：
- 金毛犬完整保留在画面中部偏左，头部不被切；
- 左上为米色窗帘（相对亮度 L≈0.236）→ 配 `--scrim-hero-top`（.50 压暗）后白字 **7.17:1** ✅；
- 右上为深色窗帘（L≈0.116）→ 白字 **9.93:1** ✅；
- 头像 rail 所在横带（L≈0.180）→ 白字 **8.24:1** ✅；
- 底部沙发区（L≈0.397）→ 白字不足，但该区域**不放文字**，由护理卡上叠覆盖 ✅。

**结论表（区域 · 相对亮度 · 白字对比度）**

| 区域 | L | 无遮罩 | + 顶部遮罩 .50 | + `--glass-dark` .32 | 判定 |
|------|---|--------|---------------|-------------------|------|
| 左上信息面板 | 0.236 | 3.51 ❌ | 7.17 ✅ | 8.24 ✅ | 必须有遮罩 |
| 右上胶囊区 | 0.116 | 6.33 ✅ | 9.93 ✅ | — | 安全 |
| 头像 rail 带 | 0.180 | 4.56 ✅ | 8.24 ✅ | — | 安全 |
| 底部护理卡叠压区 | 0.397 | 2.36 ❌ | — | — | 不放文字 |

> ⚠ 若坚持**白色玻璃**（需求摘要原写法），白字在左上仅 **2.39:1**、右上 **3.06:1** —— **实测不达标**。这是本次唯一一处对需求摘要配色写法的实质性偏离，理由是 WCAG 实测。

**P1 素材需求（竖版主视觉）**

| 项 | 要求 |
|----|------|
| 尺寸 | 1200 × 1600px（3:4）或 1080 × 1350px（4:5），≥ 1200px 宽 |
| 构图 | 宠物主体居中偏下；**上方 1/3 为低对比柔光区**（米/沙金墙面或窗纱），供顶栏浮层使用 |
| 光线 | 主体面部有柔光，**避免顶部大面积高光直射**（会导致浮层白字不可读） |
| 色调 | 暖米 / 沙金 / 炭黑系，与 Linen 背景同族 |
| 排除 | 画面内不得有文字、水印、logo；不得出现 iOS 蓝/蓝紫 |
| 交付 | 同批交付 2–3 张备选，命名 `hero-pet-{01..03}.jpg` |

**同时标记**：现有 `hero-banner.jpg` 在 3:2 横构图下裁竖版会牺牲左右留白，且顶部亮区依赖遮罩补救 —— 竖版素材到位后应优先使用竖版。

### 7.3 素材可用性审计（重要）

| 目录 | 数量 | 水印 | 结论 |
|------|------|------|------|
| `assets/` 根目录 | 12 张（含 pet-hub-scene） | **无** | ✅ 可直接用于原型 |
| `assets/photo/` | 26 张 | **全部带「AI生成」水印**（右下角） | ❌ **不可用于产品级原型**（品牌 App 出现 AI 水印是 P0 级问题）。如确需使用，必须裁掉右下 20% × 10% 区域 |

> **同时注意**：`assets/photo/s-hero.jpg`（1368×768）虽然无水印，但**画面内烧录了中英文字**「MOYUYO / Mobile app hero Banner / Luxury Pet Lifestyle」—— 这是**设计稿样张**，不是素材，**不可直接用作 Banner**。
> `assets/photo/a-brand.jpg`（768×1368）是 MOYUYO 品牌放射光竖版图，可用于**品牌位/空态插画**，但同样带水印。

### 7.4 素材 → 组件映射

| 组件 | 素材 | 处理 |
|------|------|------|
| Pet Hub Hero | `hero-banner.jpg` | 竖向裁切（§7.2），双遮罩 |
| 首页 Banner | `hero-banner.jpg` | 16:9 裁切（y 62–737） |
| 社区单图帖 | `post-dog.jpg` | cover，4:3 / ≤240pt |
| 社区九宫格 | `post-cat.jpg` `post-flatlay.jpg` | 1:1，cell radius 8pt |
| 商品卡（7 张） | `prod-*.jpg` | 1:1 直裁 174.5pt |
| 3D 空间占位 | `pet-hub-scene.jpg` | 现状素材，占位用 |

---

## §8 定稿裁决记录（与需求摘要的冲突）

> 需求摘要是唯一输入契约，但其中存在**内部矛盾**与**实测不达标**。以下逐条给出裁决、依据与影响，供 Phase 4 审查核对。

| # | 冲突点 | 需求摘要写法 | **定稿** | 依据 |
|---|--------|------------|--------|------|
| **1** | `--color-text` 对比度数值 | D1 表写「12.62 / 13.33」 | **14.06（对白）/ 12.62（对 Linen）** | 实测：#2E2B29 相对亮度 L=0.0247。摘要把两个值写反且第一个偏低。结论不变（AAA），仅数值订正 |
| **2** | `--color-text-label` 与 `--color-text-secondary` | 两者分别定义 `#6F6960`(4.88/4.88) 与 `#6E6962`(5.44/4.88) | **合并为同一来源**：`--color-text-label: var(--color-text-secondary)` | 实测两者对比度差 **0.01**，视觉不可分；且 4.88 标注错（对白实为 5.43）。违反"令牌唯一来源"硬约束。`--color-text-label` 保留为**语义别名**，用于时间戳/计数/状态词 |
| **3** | 导航居中标题字号 | D2「Title 3 = 20pt → 导航居中标题」 vs D5/C3「折叠为 17pt 居中标题」 | **17pt / 600（Headline）**；Title 3 (20pt) 只用于卡片标题 | iOS 导航居中标题标准为 17pt Semibold。D5 与 C3 两处一致指 17pt，D2 为孤例 |
| **4** | Caption 2 = 11pt 的用途 | D2「Caption 2 → 极小说明」 | **保留 11pt，但降级为"纯数字/徽标专用"**（如 "9 张"、"99+"）；**承载信息的最小字号 = Footnote 13pt** | ① G4 要求全站最小 ≥ 11pt，故尺寸保留；② D2 给它挂"说明"任务会让时间戳/计数落到 11pt，中文 11pt 笔画粘连、可读性差；③ 承载信息一律走 `--color-text-secondary` + ≥13pt，双重保障 |
| **5** | 阴影档数 | D3「仅 2 档」+ 另给会员卡暖色阴影 `rgba(179,138,90,.22)` | **只有 2 档**：`--shadow-card` / `--shadow-float`；会员卡改用 `--shadow-float`，**取消暖色阴影** | "仅 2 档"是硬约束；暖色阴影在暖调背景上几乎不可见，保留反而制造第三档 |
| **6** | TabBar 标签字号 | D5「标签 10pt」 vs G4「全站最小字号 ≥ 11pt」 | **11pt / 500** | G4 是 Phase 4 的 P0 验收项；10pt < 11pt 会直接判不通过。11pt 与 iOS 10pt 视觉差异 <1pt，肉眼不可辨 |
| **7** | 会员卡渐变终点色 | D1「`#E8DDB5 → #DBC98A → #B38A5A`」+ 卡内炭色字 | 终点改 **`#C09A6A`**（渐变 `135deg, #E8DDB5 0%, #DBC98A 46%, #C09A6A 100%`） | 实测炭色 #2E2B29 在 #B38A5A 上仅 **4.49:1**，差 0.01 不达标。改 #C09A6A 后 **5.40:1** ✅。 Antique Brass 色相保留在终点相邻档 |
| **8** | Pet Hub 玻璃浮层底色 | 任务书与 D1 语境指向"半透明白底 + hairline" | **深色玻璃** `rgba(46,43,41,.32)` + `blur(20px) saturate(180%)` + 0.5pt `rgba(255,255,255,.18)` | 实测白玻璃在照片上白字 **2.39–3.06:1** ✗；深色玻璃 **4.81–7.68:1** ✅。深色玻璃同时是 Apple Music/Photos 的成熟做法 |
| **9** | 宠物切换选中环 | 任务书「金色环」 | **3pt 白环 + scale 1.06 + 金色光晕** | 实测金色环在照片亮区仅 **2.23:1** ✗、白环 3.67:1 ✅。金色以**光晕**形式保留品牌感，不承担独立语义 |
| **10** | 商品卡价格颜色 | C2「价格 Charcoal 17pt semibold」 vs D1「价格用 --ink-gold」 | **`--ink-gold` 17pt/600** | 依据：① D1 是"可直接落 token"的颜色职责表且明确点名"价格"；② 任务书落地映射表明确要求"`--color-primary` 写文字的地方全部换成 `--ink-gold`"；③ 价格需与商品名（Charcoal）拉开层级。若主理人更倾向 C2 的 Charcoal，只需把 `.recommend-price` 的 `color` 改为 `var(--color-text)`，其余不变 |
| **11** | rpx 换算基准 | D3「1pt = 1.923rpx」（按 390pt） | **1pt = 1.908rpx**（按 393pt 主机，= 750/393） | 需求摘要 A2 定 393×852 为主机，1.923 基于 390 宽不自洽。影响 2 档：Title 1 54→53rpx、Headline/Body 33→32rpx（≤1rpx，无视觉差） |
| **12** | 消息预览字号 | C4「预览 14pt」 | **15pt（Subhead）** | 14pt 不在 D2 的 11 档阶梯内，违反"字号全部取自阶梯、无裸值"（G4） |
| **13** | 消息会话文字起点 | C4「x = 16+48+12 = 76pt」 | **卡内 76pt / 页面绝对 92pt** | 16pt 是**页面**边距，卡内另有 16pt padding，坐标需换算。同时把分隔线左 inset 统一定为"文字起点 = 16 + 前导宽 + 12" |
| **14** | 金刚区图标底 | 现状 `--background-200` #ede9e4（对白 **1.09:1**） | **`--color-fill-dim` #DCD6CC（对白 1.45:1）** | X7 量化；1.45 已达"可辨识分界"（iOS 自身多在 1.05–1.15，靠形状而非亮度区分；本项目用圆角方块 + 1.45 更稳） |

---

## §9 交付自检（Phase 3 构建前逐条打钩）

**令牌唯一性**
- [ ] 颜色 token 全部来自 §3.8，无裸 hex（组件层允许 `#FFFFFF`/`#2E2B29` 等**语义已在 token 中**的场景改用 token）
- [ ] 阴影 2 档、圆角 7 个语义值、间距 9 档、字号 11 档、字重 4 档，**无第二套来源**
- [ ] `variables.scss` 中 `--shadow-*`(10) `--radius-*`(4) `--space-*`(6) `--font-size-*`(8) `--brand-*`(9) `--font-weight-light` 已删除/合并

**颜色职责**
- [ ] 全站 **0 处** `color: var(--color-primary)`
- [ ] 全站 **0 处** `#007AFF` / `#004FAF` / `#4C6EF5` / `rgba(0,122,255,*)` / 蓝紫渐变
- [ ] 全站 **0 处** `#FF4D4F`（统一 `--color-danger-solid` / `--color-danger-ink`）
- [ ] 金色实心块内文字全部 `--on-primary`；无白字在金底
- [ ] 承载信息文字（时间/计数/状态/价格/占位符）全部 ≥ 4.5:1
- [ ] 无 emoji 图标；无 Material 大圆角浮起卡

**几何与交互**
- [ ] 页面左右边距全站 **16pt**（现状 8/10/12pt 混排已清零）
- [ ] 所有命中区 ≥ 44×44pt
- [ ] 全站 **0 处** `:hover`
- [ ] 按压态齐全（scale .97/.98 + 变 `--press`）
- [ ] Large Title 折叠无高度跳变、标题不闪烁
- [ ] 分隔线左 inset 对齐文字起点；组末行无分隔线

**品牌与克制**
- [ ] 每屏唯一视觉焦点；同屏白卡 ≤ 4 张
- [ ] 金色填充面积单屏 ≤ 5%
- [ ] Banner 用底部渐变压字（**无黑底矩形块**）；指示点为 iOS 胶囊式
- [ ] Segmented Control 33pt + 白胶囊 + 250ms；全站无下划线 tab
- [ ] TabBar 自绘：毛玻璃 + hairline + 选中 `--ink-gold` + 标签 11pt

**素材**
- [ ] 仅使用 `assets/` 根目录 12 张（无水印）；`assets/photo/` 26 张未使用
- [ ] Hero 顶部/右上/rail 三区白字对比度均 ≥ 4.5（§7.2 表）
- [ ] 竖版 Hero 素材需求已登记（P1）

---

*文档结束 · 设计系统专家 彩格调（Cai） · 2026-11-19 · MOYUYO ATELIER iOS 重设计*
