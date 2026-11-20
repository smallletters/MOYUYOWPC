# MOYUYO ATELIER · 五 Tab 视觉语言规范 v1.14

> **适用范围**：`pages/tabbar/pet` `/home` `/community` `/messages` `/user` 五个 Tab 页
> **技术前提**：uni-app 3 + Vue3 同构 H5/APP，自绘元素单位 rpx（750 设计宽），图标仅 `luc luc-xxx` 图标字体，底部为 uni-app 原生 tabBar
> **文档性质**：唯一视觉依据。只定义「应该长什么样」，不含代码改动
> **配色基线**：不换血。Linen `#F6F2EE` + Sand Gold `#DBC98A` 不动，升级的是层级、留白、节奏与组件质感
> **配套文档**：`02-ux-spec.md`（流程/状态/手势/验收）。几何与 token 以本文为准，两文互改必须同步（见 §7.4）

---

## 目录

1. [设计概念](#1-设计概念)
2. [Token 表](#2-token-表)
3. [全局版式规则](#3-全局版式规则)
4. [逐页 Layout 规范](#4-逐页-layout-规范)
5. [共用组件清单](#5-共用组件清单)
6. [动效与质感](#6-动效与质感)
7. [附录：落地顺序与已知妥协](#7-附录落地顺序与已知妥协)

---

# 1. 设计概念

## 1.1 一句话主张

> **把「金色当颜料」改成「金色当光线」——五个 Tab 从"每屏都在喊的电商页"收敛为"一屏一焦点的高端画册"。**

具体含义：

- 金色**不再大面积铺在文字和底色上**，只出现在三个位置：**描边、下标线、图标底**，以及**唯一的实心 CTA 填充**（填充时文字必须是炭色 `#2E2B29`，对比度 8.52:1）。
- 每个屏只允许 **1 个视觉焦点**（Pet=场景、Home=商品图、Community=帖子图、Messages=会话行、User=会员卡），其余全部降为容器与灰度。
- 所有页共用一套 **32rpx 页面栅格 + 24rpx 卡片节奏**，切换到任何 Tab 时左边缘都是同一条线。

## 1.2 三个关键词

| 关键词 | 定义 | 落到数值上是什么 |
|---|---|---|
| **沉金** Settled Gold | 金色是"镀层"，不是"面料" | 金色文字一律改用 `--ink-gold #6E5C30`（5.83:1）；金色实心块内文字用 `#2E2B29`（8.52:1）；禁白字压金（1.65:1） |
| **呼吸** Breath | 一屏一焦点，留白承担结构 | 页面左右 32rpx、卡间距 24rpx、分区间 48rpx、大卡内边距 32rpx；同屏白卡 ≤ 3 个 |
| **秩序** Order | 圆角、字号、间距有唯一映射 | 圆角 4 档（8/16/24/32）、字号 8 档每档一个语义、表面 3 级（Canvas/Container/Chip） |

## 1.3 为什么这样改：现状实测诊断

下面每一条都是当前代码里可复核的数值问题，是新规范的直接依据。

### A. 金色被当作文字色使用，全线对比度不达标

| 位置 | 现状 | 对比度 | 结论 |
|---|---|---|---|
| Community 选中 Tab 文字 | `color: var(--color-primary)` #DBC98A on #F6F2EE | **1.48:1** | 远低于 4.5:1 |
| Home 商品价格 | `color: var(--color-primary)` on #FFF | **1.65:1** | 价格是核心信息，不可读 |
| 话题 chip 文字 | `#DBC98A` on `rgba(219,201,138,.15)` | **1.51:1** | 几乎看不见 |
| Community FAB | 金底 + `color:#fff` | **1.65:1** | 白图标糊在金底上 |
| Messages 会话头像兜底 | 金底 + `#fff` 字母 | **1.65:1** | 同上 |
| Messages 登录按钮 | 金底 + `#fff` | **1.65:1** | 同上（对比：`user.vue` 同一按钮用了炭色文字，是对的 → 说明缺规范而非缺能力） |

> 补一句话：`--color-primary` 是**填充色**，不是**文字色**。本次新增 `--ink-*` 一族专供文字，把两者的职责拆开。

### B. 品牌色板被 iOS 系统色"漏"出来

| 位置 | 现状硬编码 | 问题 |
|---|---|---|
| `pet.vue` | `--hub-ok:#34c759` `--hub-err:#ff3b30` | iOS 系统绿/红，与 Sage Mist / Dusty Rose 冲突 |
| `user.vue` 会员卡 | `linear-gradient(135deg, var(--color-primary,#007aff) 0%, #004fad 100%)` + `box-shadow rgba(0,122,255,.3)` | 浅色下渲染成 **金→深蓝** 渐变，且阴影是 iOS 蓝 |
| `community.vue` FAB | `box-shadow: rgba(0,122,255,.35)` | 蓝色光晕 |
| `community.vue` / `messages.vue` | `#ff4d4f`（角标、删除按钮、未读点） | 白字对比 **3.27:1**，未达 4.5 |
| `home.vue` navbar | `u-icon color="#2E2B29"`（uview 图标 + 硬编码色） | **全站唯一用 uview 图标的 tab 页**，深色模式下图标恒为深色 → 看不见 |
| `home.vue` | `indicator-active-color="#2E2B29"` | 同上，不跟随主题 |

### C. 圆角与间距无映射

- 同屏圆角值：`8 / 12 / 16 / 20 / 24 / 32 / 9999` 七种，出现顺序与控件面积无关。
- 页面外边距四套：Home navbar `24rpx` / banner `32rpx` / 金刚区 `24rpx` / feed `24rpx`；Community feed `16rpx` + 卡片内 `20rpx`；Messages `24rpx`；User `24rpx`；Pet `32rpx`。
- **结果**：`home.vue` 的 banner（32rpx）与金刚区（24rpx）左边缘错位 8rpx —— 在真机上是肉眼可见的"抖"。

### D. 字号阶梯断裂 + 触碰下限

- 在用字号：`20 / 22 / 24 / 26 / 28 / 30 / 32 / 36 / 40 / 44 / 56`（11 档，token 只定义了 8 档）。
- `20rpx`（=10px）出现在 `care-status`、`quick-label`、`order-badge`、`post-time` 四处 —— **角标文字不该低于 22rpx**。
- `pet.vue` 局部把 `--font-size-xs` 覆盖为 `24rpx`，而全局是 `22rpx` → 同名 token 两处不同值，语义漂移。

### E. 触摸目标不足 88rpx

| 元素 | 实测尺寸 | 规范 |
|---|---|---|
| `community .more-btn` | 56×56rpx | ≥88×88 |
| `pet .rm-btn` | 56×56rpx | ≥88×88 |
| `messages .navbar-icon` | 56×56rpx | ≥88×88 |
| `community .icon-btn` | 60×60rpx | ≥88×88 |
| `community .topic-tag` | 高 56rpx | 视觉 56 + 热区 88 |
| `home .navbar-search` | 高约 68rpx | 视觉 72 + 热区 88 |
| `.post-actions` 单项 | 高约 44rpx | 高 88 热区 |

### F. 顶部导航三种写法，切 Tab 会"跳"

| 页面 | 写法 | 标题 |
|---|---|---|
| Pet | 全图沉浸，浮层 `glass` | 无 |
| Home | 白底 `sticky` + 搜索条 | 无 |
| Community | **透明底** + `40rpx/700` 大标题 | 有 |
| Messages | 白底 `sticky` + `36rpx/600` | 有 |
| User | 无顶栏，直接白 header | 无 |

安全区实现也有 4 种：双声明覆盖（home/messages）、`calc()`（community/user）、`@supports` 变量（pet）。

### G. 衬线标题体系"声明了但没落地"

`common.scss` 声明 `.section-title/.headline-1/.headline-2 → 'Playfair Display'`，但 **5 个页面 0 处使用**；Playfair 只通过 `index.html` 的 Google Fonts 加载 → **APP 端无此字体**，会 fallback 到 Times/宋体。同时 Playfair 不含中文字形，中文标题必然 fallback。

---

## 1.4 旧 → 新：七个差异

| # | 维度 | 旧 | 新 | 为什么 |
|---|---|---|---|---|
| 1 | 金色职责 | 文字色 + 大块填充 | 描边/下标/图标底 + 唯一 CTA 填充（内文炭色） | 1.48:1 → 5.83:1，金色终于"贵"而不是"淡" |
| 2 | 表面层级 | 所有模块都是「白卡 + 1rpx 描边」 | 三级表面：Canvas(Linen) / Container(白 + 极浅双阴影，**无描边**) / Chip(暖灰 fill) | 去掉遍地描边后，卡片靠"抬升"分层，更接近印刷画册 |
| 3 | 圆角 | 7 种，无逻辑 | 4 档，**面积越大圆角越大**，父子差 ≥8rpx | 消除 32 卡里放 32 图的"糊边" |
| 4 | 栅格 | 16/20/24/32 混排 | 单值 32rpx 页面边距，24rpx 卡间距，48rpx 分区间 | 修掉 banner/金刚区 8rpx 错位；横向扫读有基线 |
| 5 | 字号 | 11 档散用，20rpx 承载信息 | 8 档，每档唯一语义，**22rpx 为下限** | 10px 标签在小屏上不可读 |
| 6 | 顶部栏 | 5 页 3 种写法 | 3 个受控变体（Standard / Serif / Immersive），内容高统一 88rpx | 切 Tab 时顶部不再跳；安全区一种写法 |
| 7 | 衬线 | 全局声明、无人使用、APP 无字体 | **衬线只用于拉丁字符**（品牌名/英文/大数字），中文标题用无衬线 600 + 负字距 | 中文 fallback 到宋体是最廉价的"高端感"陷阱 |

---

# 2. Token 表

## 2.1 颜色

标注说明：`新增` = 新 token；`改值` = 已存在但取新值（有视觉 diff，需回归）；`保留` = 值不变，只新增使用规则。

### 2.1.1 画布与表面（三级）

| Token | 浅色 | 深色 | 用途 | 状态 |
|---|---|---|---|---|
| `--color-background` | `#F6F2EE` | `#1A1816` | Canvas：页面底 | 保留 |
| `--color-surface` | `#FFFFFF` | `#2A2724` | Container：一级卡片/顶栏 | 保留 |
| `--color-surface-sunken` | `#F1ECE5` | `#232120` | 二级凹陷：分组底、图片占位底、banner 兜底 | 新增 |
| `--color-surface-veil-92` | `rgba(255,255,255,.92)` | `rgba(42,39,36,.92)` | 顶部栏磨砂 | 保留 |
| `--color-fill` | `#F1EDE7` | `#35312D` | Chip：图标底圆、输入框、次级按钮底 | **改值**（原 `#F2F2F7` 是 iOS 冷灰，破坏暖调） |
| `--color-fill-hi` | `#F7F3EE` | `#403B36` | 抬升 chip（选中段、骨架底） | 改值 |
| `--color-divider` | `#EAE5DD` | `#3A3530` | 装饰分隔线（1rpx hairline） | 保留 |
| `--color-line` | `#E2DCD2` | `#3A3530` | 卡片可选描边（默认不用） | 新增 |
| `--color-line-strong` | `#948C80` | `#857D74` | **表单控件/可聚焦元素描边**（3.32:1 / 3.67:1） | 新增 |

> 为什么单独给 `--color-line-strong`：`#E2DCD2` 在白底只有 1.3:1，作为输入框边界不满足 WCAG 1.4.11 的 3:1。所以**装饰分隔用 divider，控件边界用 line-strong**，不要混用。

### 2.1.2 文字

| Token | 浅色 | 深色 | 对比度（浅 / 深） | 用途 | 状态 |
|---|---|---|---|---|---|
| `--color-text` | `#2E2B29` | `#F6F2EE` | 12.62 / 13.33 | 标题、正文、可点文字 | 保留 |
| `--color-text-secondary` | `#6E6962` | `#B8B0A5` | 4.88 / 6.92 | 次要文字、正文级说明 | 保留 |
| `--color-text-label` | `#6F6960` | `#A29A90` | 4.88 / 4.64 | **承载信息的标签**：时间、数量单位、"已读/未读"、规格 | 新增 |
| `--color-text-tertiary` | `#9A948C` | `#807A72` | 2.70 / 3.50 | **仅装饰**：占位符默认态、禁用文字、非关键分隔符 | 保留（**降级为装饰专用**） |
| `--color-text-disabled` | `#B5AFA6` | `#6B655E` | 2.18 / 2.58 | 禁用态（不承载必须读到的信息） | 新增 |

> 迁移规则：**当前所有用 `--color-text-tertiary` 承载真实信息的地方（时间戳、条数、状态词）全部改用 `--color-text-label`**。这是本次收益最大的单个替换（2.70 → 4.88）。

### 2.1.3 金色（品牌核心）

| Token | 浅色 | 深色 | 用途 | 状态 |
|---|---|---|---|---|
| `--color-primary` | `#DBC98A` | `#C9B47A` | **仅填充**：CTA 底、图标底、进度条、选中描边 | 保留 |
| `--color-primary-light` | `#E8DDB5` | `#D9C68F` | 填充浅一档（hover/渐变起） | 保留 |
| `--color-primary-dark` | `#B8A66B` | `#A8964F` | 填充深一档（渐变末/pressed） | 保留 |
| `--ink-gold` | `#6E5C30` | `#E3CE93` | **金色文字**：选中 Tab、价格、会员等级、金色文字按钮 | **新增（关键）** |
| `--color-primary-soft` | `rgba(219,201,138,.18)` | `rgba(201,180,122,.20)` | 金色弱底：选中 chip、未读行底、标签底 | 新增（对齐现有 `--color-primary-bg`） |
| `--on-primary` | `#2E2B29` | `#2E2B29` | 金色填充上的文字/图标（8.52:1） | 新增 |

**金色硬规则（三条，不可例外）**：

1. 金色实心块上**只能**放 `--on-primary`（炭色），禁白字（1.65:1）。
2. 金色作为文字**只能**用 `--ink-gold`，禁用 `--color-primary`（1.48:1）。
3. 金色描边宽度 ≤ `2rpx`，禁止给金色加 glow 阴影（会显廉价）。唯一允许的金色柔光是 `box-shadow: 0 0 0 6rpx var(--color-primary-soft)`（用于选中头像环）。

### 2.1.4 语义色（墨色 + 弱底）

| Token | 浅色 | 深色 | 对比度（浅 / 深） | 用途 |
|---|---|---|---|---|
| `--ink-ok` | `#5F7259` | `#A8C3A1` | 4.67 / 6.75 | 良好状态文字 |
| `--color-success` | `#ABB9AD` | `#ABB9AD` | — | 成功填充/图标底 |
| `--color-ok-soft` | `rgba(171,185,173,.15)` | `rgba(171,185,173,.18)` | — | 良好状态底 |
| `--ink-warn` | `#8A6B2F` | `#E7C48C` | 4.46 / 8.96 | 临近/提醒文字 |
| `--color-warning` | `#E6B97A` | `#E6B97A` | — | 提醒填充 |
| `--color-warn-soft` | `rgba(230,185,122,.15)` | `rgba(230,185,122,.18)` | — | 提醒底 |
| `--ink-err` | `#A95245` | `#E8907F` | 4.74 / 5.36 | 错误/过期/危险文字 |
| `--color-danger` | `#C96E5F` | `#C96E5F` | — | 图标/描边/弱元素 |
| `--color-danger-solid` | `#B4574A` | `#B4574A` | 4.77（白字） | **承载白字的红色填充**：角标底、删除按钮 |
| `--color-err-soft` | `rgba(201,110,95,.12)` | `rgba(201,110,95,.16)` | — | 错误底 |
| `--ink-rose` | `#8C5F5A` | `#E2B3AE` | 4.83 / 7.99 | 情感/收藏类文字 |
| `--color-warm` | `#D9B4B0` | `#D9B4B0` | — | 情感填充 |
| `--ink-info` | `#4F6A78` | `#A8C0CC` | 5.14 / 7.83 | 信息提示文字 |
| `--color-info` | `#8FA8B6` | `#8FA8B6` | — | 信息填充 |

> **红色统一**：现有 `#ff4d4f`（白字 3.27:1）全部替换为 `--color-danger-solid #B4574A`（白字 4.77:1）。同时消除"金品牌里跳出一块 iOS 红"的割裂。

### 2.1.5 四产品线色（标签底色，不改）

`--color-care #DBC98A` / `--color-gear #ABB9AD` / `--color-play #B38A5A` / `--color-home #D9B4B0` 保留。
**规则**：产品线标签写作「弱底 + 墨色文字」，即 `background: <线色 15%>` + `color: <对应 ink>`：
- care → `--ink-gold`；gear → `--ink-ok`；play → `--ink-gold`（brass 加深后与 gold ink 同族）；home → `--ink-rose`。

### 2.1.6 可直接抄进 `variables.scss` 的增量块

> **v1.9 补登**：`--sab`（底部安全区）此前从未定义，却在多份讨论与指令里被 `var(--sab)` 直接引用 —— 这是本项目第 4 个"没定义却到处 `var()`"的坑（前三个是 `--color-card`、`--tabbar-h` 的早期 rpx 版、`--color-primary-dark`）。**CSS 变量未定义时，引用它的 `calc()` 整条在计算值阶段失效**，`bottom` 回落 `auto`，FAB 会脱离定位跑进文档流。现已正式入表，`--tabbar-offset` 也改为基于它定义，两种写法从此**可证明等价**。`--sab` 与明暗主题无关，只登记在 `:root`。

```scss
:root {
  /* ===== 表面：三级 ===== */
  --color-surface-sunken: #f1ece5;        /* 新增 */
  --color-fill: #f1ede7;                  /* 改值：原 #f2f2f7 为 iOS 冷灰 */
  --color-fill-hi: #f7f3ee;               /* 改值 */
  --color-line: #e2dcd2;                  /* 新增：可选卡片描边 */
  --color-line-strong: #948c80;           /* 新增：控件边界，3:1 */

  /* ===== 文字：新增两级 ===== */
  --color-text-label: #6f6960;            /* 新增：承载信息的标签，4.88:1 */
  --color-text-disabled: #b5afa6;         /* 新增 */

  /* ===== 墨色（Ink）：文字专用，替代把填充色当文字色 ===== */
  --ink-gold: #6e5c30;                    /* 5.83:1 */
  --ink-ok: #5f7259;                      /* 4.67:1 */
  --ink-warn: #8a6b2f;                    /* 4.46:1 */
  --ink-err: #a95245;                     /* 4.74:1 */
  --ink-rose: #8c5f5a;                    /* 4.83:1 */
  --ink-info: #4f6a78;                    /* 5.14:1 */

  /* ===== 金色：职责拆分 ===== */
  --on-primary: #2e2b29;                  /* 金色填充上的唯一可用文字色 8.52:1 */
  --color-primary-soft: rgba(219, 201, 138, 0.18);
  --color-ok-soft: rgba(171, 185, 173, 0.15);
  --color-warn-soft: rgba(230, 185, 122, 0.15);
  --color-err-soft: rgba(201, 110, 95, 0.12);
  --color-danger-solid: #b4574a;          /* 承载白字的红，4.77:1 */

  /* ===== 圆角：4 档 + pill ===== */
  --radius-xs: 8rpx;    /* 角标、tag 内小方块 */
  --radius-sm: 16rpx;   /* 输入框、chip、九宫格图片 */
  --radius-md: 24rpx;   /* 标准卡片、会话行 */
  --radius-lg: 32rpx;   /* 大卡、banner、沉浸容器 */
  /* --radius-pill 9999rpx 保留 */

  /* ===== 间距：新增 2xs + 语义化标尺 ===== */
  --space-2xs: 4rpx;
  --page-gutter: 32rpx;    /* 页面左右边距（全站唯一值） */
  --card-gap: 24rpx;       /* 卡片之间 */
  --section-gap: 48rpx;    /* 大分区之间 */
  --card-pad: 24rpx;       /* 标准卡片内边距 */
  --card-pad-lg: 32rpx;    /* 大卡内边距 */
  --row-h: 104rpx;         /* 列表行高 */
  --control-h: 88rpx;      /* 控件最小触摸高 */
  --chip-h: 56rpx;         /* chip 视觉高（热区仍需 88） */
  --nav-h: 88rpx;          /* 自绘顶栏内容高（rpx：随视口等比缩放，可接受） */
  /* 安全区：底部。**必须是 px 族**（env 返回 px，转 rpx 会二次缩放）。
     未定义即被 var() 引用会让整条 calc 在计算值阶段失效、回落 auto —— FAB 会脱离定位跑进文档流 */
  --sab: env(safe-area-inset-bottom, 0px);   /* v1.9 正式入表：此前被反复凭空引用 */
  /* 原生 tabBar：客户端渲染、尺寸恒定 50px，**不能用 rpx**
     430pt 视口下 100rpx = 57.3px，比原生 tabBar 高 7px → FAB 与底部留白会错位 */
  --tabbar-h: 50px;
  --tabbar-offset: calc(50px + var(--sab));   /* 唯一底部偏移基准，与 calc(var(--sab) + 50px) 等价 */

  /* ===== 阴影：3 档 + 顶栏 ===== */
  --shadow-card: 0 1rpx 2rpx rgba(46, 43, 41, 0.03), 0 2rpx 8rpx rgba(46, 43, 41, 0.04);
  --shadow-float: 0 2rpx 6rpx rgba(46, 43, 41, 0.04), 0 8rpx 24rpx rgba(46, 43, 41, 0.08);
  --shadow-overlay: 0 16rpx 48rpx rgba(46, 43, 41, 0.14);
  --shadow-nav: 0 1rpx 0 var(--color-divider);   /* 顶栏滚动后：用线而非阴影 */

  /* ===== 层级 z-index ===== */
  --z-base: 0;
  --z-sticky: 10;      /* 吸顶区块头 */
  --z-nav: 20;         /* 自定义顶栏 */
  --z-fab: 50;         /* 浮动发布按钮 */
  --z-sheet-mask: 100; /* 弹层遮罩 */
  --z-sheet: 110;      /* 弹层 */
  --z-toast: 200;

  /* ===== 动效 ===== */
  --ease-standard: cubic-bezier(0.2, 0, 0, 1);
  --ease-emphasized: cubic-bezier(0.3, 0, 0.2, 1);
  --dur-press: 120ms;
  --dur-state: 200ms;
  --dur-enter: 280ms;

  /* ===== 字体族（衬线仅拉丁） ===== */
  --font-ui: 'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', sans-serif;
  --font-display: 'Playfair Display', Georgia, 'Times New Roman', serif;
}

@media (prefers-color-scheme: dark) {
  :root:not(.theme-light) {
    --color-surface-sunken: #232120;
    --color-fill: #35312d;
    --color-fill-hi: #403b36;
    --color-line: #3a3530;
    --color-line-strong: #857d74;
    --color-text-label: #a29a90;
    --color-text-disabled: #6b655e;
    --ink-gold: #e3ce93;
    --ink-ok: #a8c3a1;
    --ink-warn: #e7c48c;
    --ink-err: #e8907f;
    --ink-rose: #e2b3ae;
    --ink-info: #a8c0cc;
    --color-primary-soft: rgba(201, 180, 122, 0.2);
    --color-ok-soft: rgba(171, 185, 173, 0.18);
    --color-warn-soft: rgba(230, 185, 122, 0.18);
    --color-err-soft: rgba(201, 110, 95, 0.16);
    --shadow-card: 0 1rpx 2rpx rgba(0, 0, 0, 0.28), 0 2rpx 8rpx rgba(0, 0, 0, 0.36);
    --shadow-float: 0 2rpx 6rpx rgba(0, 0, 0, 0.32), 0 8rpx 24rpx rgba(0, 0, 0, 0.45);
    --shadow-overlay: 0 16rpx 48rpx rgba(0, 0, 0, 0.55);
  }
}

/* 强制深色：与上面逐项一致（否则"系统浅色 + App 内强制深色"会漏覆盖） */
.theme-dark {
  --color-surface-sunken: #232120;
  --color-fill: #35312d;
  --color-fill-hi: #403b36;
  --color-line: #3a3530;
  --color-line-strong: #857d74;
  --color-text-label: #a29a90;
  --color-text-disabled: #6b655e;
  --ink-gold: #e3ce93;
  --ink-ok: #a8c3a1;
  --ink-warn: #e7c48c;
  --ink-err: #e8907f;
  --ink-rose: #e2b3ae;
  --ink-info: #a8c0cc;
  --color-primary-soft: rgba(201, 180, 122, 0.2);
  --color-ok-soft: rgba(171, 185, 173, 0.18);
  --color-warn-soft: rgba(230, 185, 122, 0.18);
  --color-err-soft: rgba(201, 110, 95, 0.16);
  --shadow-card: 0 1rpx 2rpx rgba(0, 0, 0, 0.28), 0 2rpx 8rpx rgba(0, 0, 0, 0.36);
  --shadow-float: 0 2rpx 6rpx rgba(0, 0, 0, 0.32), 0 8rpx 24rpx rgba(0, 0, 0, 0.45);
  --shadow-overlay: 0 16rpx 48rpx rgba(0, 0, 0, 0.55);
}
```

**深色模式注意**：`--ink-*` 必须在三个块（`:root` / `@media dark` / `.theme-dark`）各给一份。`--ink-*` 是**文字色**，深色下必须变亮而不是变暗——这是本次最容易写错的地方。

## 2.2 字号 / 字重 / 行高 / 字距

**原则：不新增字号档位，而是给现有 8 档绑定唯一语义。** 现状问题是 `22/24/26/28/30/32` 六档随意混用，读不出层级。

| Token | 值 | 唯一语义 | 字重 | 行高 | 字距 | 字体 |
|---|---|---|---|---|---|---|
| `--font-size-3xl` | 56rpx | 营销大数字（会员积分、订单金额、倒计时） | 600 | 1.1 | `-0.02em` | display |
| `--font-size-2xl` | 44rpx | 页面大标题（Community 顶栏、活动页） | 600 | 1.2 | `-0.02em` | display（拉丁）/ ui（中文） |
| `--font-size-xl` | 36rpx | 模块主标题、会员卡金额、用户名 | 600 | 1.25 | `-0.01em` | ui |
| `--font-size-lg` | 32rpx | 卡片标题、会话名、商品名（大卡） | 600 | 1.35 | 0 | ui |
| `--font-size-md` | 30rpx | 强调正文：价格、正文大号 | 400/600 | 1.5 | 0 | ui |
| `--font-size-base` | 28rpx | **正文默认**：帖子正文、列表项、表单值 | 400 | 1.55 | 0 | ui |
| `--font-size-sm` | 26rpx | 次要正文：描述、摘要、预览 | 400 | 1.5 | 0 | ui |
| `--font-size-xs` | 22rpx | **下限**：标签、时间、角标内文字、chip | 500 | 1.4 | `0.02em` | ui |

> **⚠️ 本表的 `line-height` 是"必须显式写入"的值，不是浏览器的默认行为**（v1.14 补，来自商品卡那轮实测）。
> 没写 `line-height` 的元素走浏览器 `normal`，实测 **≈1.35** —— 所以 30rpx 的价格行会是 **40.4**，既不是本表的 45（`30×1.5`）也不是卡内 override 的 36（`30×1.2`）。
> **凡是本表的字号被用来"定高度"的场合（卡片行、列表行、骨架块、任何参与构成式的行盒），`line-height` 必须写死**；否则规范写了 1.5、实现里根本不存在这一层，双方各自"算对了"，加起来差 16rpx。
> 商品卡 548 → 583.9 的整条链就是这个：规范只写了「价格 30rpx」「评分 22rpx」，没写行高，于是 548 / 583 / 583.9 / 591 / 593 / 600 六个值都能"算得出来"。

### 字号迁移映射（旧 → 新）

| 旧值 | 出现位置 | 新值 | 判定规则 |
|---|---|---|---|
| `20rpx` | care-status / quick-label / order-badge / post-time | **22rpx** | 全部上调，禁止 < 22 |
| `24rpx` | topic-tag / rm-text / action-count / conv-load-more | 单词/数字 → **22rpx**；成句 → **26rpx** | 按"是否成句"分流 |
| `26rpx` | 描述、预览 | 26rpx | 不变 |
| `30rpx` | 帖子正文（现 `600`）、价格 | 正文 → **28rpx/400**；价格 → **30rpx/600** | 正文不该比标题重 |
| `40rpx` | community 顶栏标题（`700`） | **44rpx/600** | 走 `--font-size-2xl` |
| `36rpx` | messages 顶栏标题 | 36rpx/600 | 不变 |

### 衬线使用规则（重要）

- `--font-display` **只用于拉丁字符**：品牌名 `MOYUYO`、英文大标题、数字摘要。
- **中文标题一律 `--font-ui` + 600 + `-0.02em`**，禁止让中文 fallback 到系统宋体。
- APP 端目前无 Playfair 字体文件（只靠 `index.html` 的 Google Fonts，H5 专属）。两种落地选择：
  - ~~内嵌 Playfair Display 的 latin 子集 ttf，两端一致~~ —— **已裁决不采用**（构建成本 > 收益，详见 §7.2 第 3 条）；
  - **执行方案**：不内嵌，拉丁大字用系统衬线近似，**中文标题一律 `--font-ui` 600 + 负字距**。
- 数字一律 `font-variant-numeric: tabular-nums`（价格、计数、金额对齐）。

## 2.3 圆角

| Token | 值 | 用于 | 禁止 |
|---|---|---|---|
| `--radius-xs` | 8rpx | 角标、`#话题` 内嵌 tag、图片角标 | 卡片 |
| `--radius-sm` | 16rpx | 输入框、chip、九宫格图片、内嵌缩略图 | 大容器 |
| `--radius-md` | 24rpx | 标准卡片、会话行组、商品卡 | 页面级容器 |
| `--radius-lg` | 32rpx | 大卡（Care Deck、会员卡）、banner、沉浸容器 | 图片 |
| `--radius-pill` | 9999rpx | 搜索条、话题 chip、按钮、头像、FAB | 卡片 |

**两条硬规则**：
1. **面积越大圆角越大**，同一容器内父/子圆角差 **≥ 8rpx**（32 容器 → 24 子卡 → 16 图）。
2. 圆角卡片内的图片，**图片圆角 = 0 或 16rpx**。禁止「32 圆角卡 + 32 圆角图」造成的双层弧线（`home.vue` 商品卡当前 24 卡 + 24 图 → 改为 24 卡 + 上图圆角 0，图片自己撑满卡片上部）。

## 2.4 间距

| Token | 值 | 用途 |
|---|---|---|
| `--space-2xs` | 4rpx | 图标与文字微距、数字与单位 |
| `--space-xs` | 8rpx | 紧密配对（名称 + 时间同列 gap） |
| `--space-sm` | 16rpx | chip 之间、九宫格图片 gap |
| `--space-md` | 24rpx | **卡间距**、卡片内距、列表行内垂直 |
| `--space-lg` | 32rpx | **页面左右边距**、大卡内距 |
| `--space-xl` | 48rpx | 分区间距、空态上下留白 |
| `--space-2xl` | 64rpx | 页面首屏顶部留白、页脚留白 |

### rpx / px 分工（唯一规则）

| 类别 | 单位 | 理由 | 覆盖 |
|---|---|---|---|
| **自绘元素**（所有页面内布局、字号、圆角、间距、热区） | **rpx** | 随视口等比缩放，视觉比例稳定 | 全部 |
| **由客户端原生渲染且尺寸恒定的量** | **px** | 不随视口缩放，用 rpx 必然在宽屏错位 | `--tabbar-h: 50px`、`--tabbar-offset`、`--status-bar-height`、`env(safe-area-inset-*)` |

反例（本次修掉）：`--tabbar-h: 100rpx` 只在 375pt 宽下等于 50px；430pt 下 100rpx = 57.3px，比原生 tabBar 高 7px。

### 底部偏移（唯一写法）

```scss
/* FAB / 悬浮层 / 底部留白都只认这一个基准 */
.fab {
  bottom: calc(var(--tabbar-offset) + 32rpx); /* 32rpx 是视觉间距，仍走 rpx */
}
.page-bottom-pad {
  padding-bottom: calc(var(--tabbar-offset) + 32rpx);
}
```

**H5 与 APP 的 fixed 基准不同，必须条件编译或统一走 `--tabbar-offset`**：H5 下 `bottom:0` 会被自绘/浮层 tabBar 遮住；APP 原生 webview 的可视区域已扣掉 tabBar，`bottom:0` 本身就在 tabBar 之上，此时再叠加偏移会偏高。落地时按 `#ifdef H5` / `#ifdef APP-PLUS` 分别给值，并真机复核一次。

## 2.5 阴影

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| 无 | — | — | **默认卡片不用阴影也不用描边**（靠白/暖灰对比分层） |
| `--shadow-card` | `0 1rpx 2rpx rgba(46,43,41,.03), 0 2rpx 8rpx rgba(46,43,41,.04)` | `0 1rpx 2rpx rgba(0,0,0,.28), 0 2rpx 8rpx rgba(0,0,0,.36)` | 需要"离开纸面"的卡片（商品卡、帖子卡、会员卡） |
| `--shadow-float` | `0 2rpx 6rpx rgba(46,43,41,.04), 0 8rpx 24rpx rgba(46,43,41,.08)` | 对应加深 | banner、FAB、吸顶后的顶栏 |
| `--shadow-overlay` | `0 16rpx 48rpx rgba(46,43,41,.14)` | 加深 | 弹层、ActionSheet 容器 |

**禁止**：彩色阴影（`rgba(0,122,255,.35)` 这类）、金色 glow、`0 4rpx 24rpx rgba(0,0,0,.08)` 这类"大范围灰雾"（`user.vue .card` 当前用的就是它，是卡片发灰的主因）。

## 2.6 层级 z-index

见 2.1.6 的 `--z-*`。使用规则：

| 层 | 值 | 谁用 |
|---|---|---|
| 内容 | `--z-base` | 页面流 |
| 吸顶区块头（Tabs 吸顶） | `--z-sticky` | `MySegmentedTabs` sticky 态 |
| 顶栏 | `--z-nav` | `MyNavBar`（必须高于内容浮层） |
| 沉浸页浮层 | `--z-nav` | `pet.vue` 场景内 pill/头像条（当前用 `z-index:20`，与导航同级合理） |
| FAB | `--z-fab` | 社区发布按钮 |
| 弹层 | `--z-sheet-mask` / `--z-sheet` | 装扮弹窗、ActionSheet |
| Toast | `--z-toast` | 自定义 toast |

> 现状问题：`pet.vue` 场景内混用 `z-index: 4/6/10/20` 四个魔数且无注释。统一后：遮罩 `1`、形象 `2`、信息浮层 `3`、交互按钮组 `--z-nav`。

## 2.7 动效 token

| Token | 值 | 用途 |
|---|---|---|
| `--dur-press` | `120ms` | 按压反馈（必须 ≤150ms 才跟手） |
| `--dur-state` | `200ms` | 颜色/描边/位移的状态切换、Tab 下划线 |
| `--dur-enter` | `280ms` | 弹层、骨架淡出 |
| `--ease-standard` | `cubic-bezier(.2,0,0,1)` | 通用（快出慢入） |
| `--ease-emphasized` | `cubic-bezier(.3,0,.2,1)` | 位移、弹层 |
| shimmer | `1200ms linear infinite` | 骨架屏扫光 |

---

# 3. 全局版式规则

## 3.1 页面栅格

```
╔══════════════════════════════════════╗  750rpx
║ ← 32rpx →                ← 32rpx →  ║  内容宽 686rpx
║ ┌──────────────────────────────────┐ ║
║ │  Card                            │ │  卡片内距 24rpx（大卡 32rpx）
║ └──────────────────────────────────┘ ║
║              ↕ 24rpx 卡间距           ║
║ ┌──────────────────────────────────┐ ║
║ │  Card                            │ │
║ └──────────────────────────────────┘ ║
║              ↕ 48rpx 分区间           ║
║  Section 2（可能无卡片）               ║
╚══════════════════════════════════════╝
```

| 规则 | 值 | 例外 |
|---|---|---|
| 页面左右边距 | **32rpx**（唯一值） | 沉浸式（Pet 场景）满宽；横向滚动条（话题 chip、宠物切换）**内容**首尾对齐 32rpx，轨道满宽 |
| 卡片间距 | 24rpx | 紧贴的分组（如"通知卡 + 私信列表"）用 16rpx |
| 分区间距 | 48rpx | 顶栏下方首个区块用 24rpx 起（避免过空） |
| 卡片内距 | 24rpx；大卡 32rpx | 图片类卡片 padding 0（图撑满） |
| 内容对齐线 | 卡片内的**文字左边缘统一 24rpx**（相对卡片），图片满宽 | 带图标行：图标左 24rpx，文字左 24+图标宽+16rpx |
| 底部留白 | `calc(var(--tabbar-offset) + 32rpx)` | 有 FAB 时额外 +120rpx |

## 3.2 卡片规范

### 3.2.1 三级表面（决定"这坨东西是什么"）

| 级 | 表面 | 外观 | 用途 |
|---|---|---|---|
| **Canvas** | `--color-background` | 无 | 页面底、纯内容分区（金刚区、区块标题） |
| **Container** | `--color-surface` + `--radius-md`/`--radius-lg` | **无描边**；需要抬升时加 `--shadow-card` | 信息卡、列表组、表单组 |
| **Chip** | `--color-fill` + `--radius-sm`/pill | 无描边无阴影 | 图标底、标签、输入框、次级按钮 |

**规则**：
- **禁止**「白卡 + 1rpx `--color-divider` 描边」作为默认卡片样式。现状 `community.vue .post-card`、`home.vue .recommend-card`、`pet.vue .care-card/.quick-item` 全部是"白卡 + 描边 + 阴影"三重叠加，是页面显脏的主因。
- 描边只用于：**可聚焦控件**（`--color-line-strong`）、**需要与同色底区分的卡**（`--color-line`）。
- 同一屏可见的 Container 级卡片 **≤ 3 个**。超过就说明该合并或改为无卡分区。

### 3.2.2 四种卡型

| 卡型 | 圆角 | 内距 | 阴影 | 典型 |
|---|---|---|---|---|
| **Hero 卡** | `--radius-lg` 32rpx | 32rpx | `--shadow-card` | 会员卡、Care Deck、banner |
| **Standard 卡** | `--radius-md` 24rpx | 24rpx | `--shadow-card` | 会话组、钱包、订单组、帖子卡 |
| **Media 卡** | `--radius-md` 24rpx | **0**（图撑满顶部，文字区 20rpx） | `--shadow-card` | 商品卡、图片卡 |
| **List 组** | `--radius-md` 24rpx | 行内 24rpx | 无 | 功能列表、设置项 |

### 3.2.3 分隔线规范

- 1rpx，色 `--color-divider`。
- 列表内分隔线**左端缩进到文字左边缘**（有图标时 = 24 + 图标宽 + 16rpx），**右端不缩进到边**（留 0，或按场景留 24rpx）。
- 最后一行**不画**分隔线（当前 `messages.vue` 已用 `:last-child`，保留）。

## 3.3 按钮规范

### 尺寸

| 尺寸 | 高 | 左右内距 | 字号 | 圆角 | 用途 |
|---|---|---|---|---|---|
| **L** | 96rpx | 48rpx | 30rpx/600 | pill | 支付、提交、登录主按钮 |
| **M**（默认） | 88rpx | 40rpx | 28rpx/500 | pill | 常规操作 |
| **S** | 64rpx | 28rpx | 26rpx/500 | pill | 卡内次要操作（**热区必须补到 88**） |
| **Text** | 88rpx 热区 / 文字行高 | 0 | 26rpx/500 | — | "全部订单""发起私信" |

### 四类样式 × 状态

| 类型 | 默认 | Pressed | Disabled | 对比度 |
|---|---|---|---|---|
| **主 Primary** | 底 `--color-primary`，字 `--on-primary` | 底 `--color-primary-dark` + `scale(.98)` | 底 `--color-fill`，字 `--color-text-disabled` | 8.52:1 |
| **次 Secondary** | 透明底，`1rpx --color-line-strong` 描边，字 `--color-text` | 底 `--color-fill` | 描边 `--color-divider`，字 `--color-text-disabled` | 12.62:1 |
| **文字 Text** | 无底，字 `--ink-gold` | 字 `--color-primary-dark` + 底 `--color-primary-soft`（pill） | 字 `--color-text-disabled` | 5.83:1 |
| **危险 Danger** | 底 `--color-danger-solid`，字 `#FFFFFF` | 底 `#9E463B` | 底 `--color-fill` | 4.77:1 |
| **危险-文字** | 无底，字 `--ink-err` | 底 `--color-err-soft` | — | 4.74:1 |

**禁止**：金底白字（1.65:1）；金色描边按钮用 `--color-primary`（1.48:1）；同一视图出现两个主按钮。

## 3.4 导航栏规范

### 3.4.1 统一骨架

```
┌──────────────────────────────────────┐
│  statusBar：--sb（见 3.4.4）           │  0 / 20px / 44px
├──────────────────────────────────────┤
│  content：88rpx（=44px）              │
│   ← 32rpx →  [左]  [右侧操作组]  ← 32rpx│
└──────────────────────────────────────┘
        总计 = --sb + 88rpx
```

### 3.4.2 三个受控变体

| 变体 | 用在哪 | 底色 | 左 | 中/右 | 标题 |
|---|---|---|---|---|---|
| **A. Standard** | Home / Messages / User | `--color-surface` | 标题 或 搜索条 | 1–3 个 88rpx 图标按钮 | 36rpx/600 |
| **B. Serif** | Community | `--color-background`，滚动 >8rpx 后渐变为 `--color-surface` + `--shadow-nav` | 大标题 | 2 个 88rpx 图标按钮 | 44rpx/600，显示 display 字体（拉丁）或 ui 600（中文） |
| **C. Immersive** | Pet | 无（浮在场景图上） | 宠物头像条（横向滚动） | 竖排 2 个 glass pill | 无 |

**变体 A 的两种形态**：
- **A1 搜索型**（Home）：搜索条 `flex:1`，高 72rpx，pill，底 `--color-fill`，内含 `luc-search` 32rpx + 占位文字 26rpx `--color-text-tertiary`；右侧 2 个图标按钮。
- **A2 标题型**（Messages/User）：标题 36rpx/600 左对齐。

### 3.4.3 尺寸与状态

| 项 | 值 |
|---|---|
| 图标按钮热区 | **88×88rpx**（图标本体 `font-size: 40rpx`，色 `--color-text`） |
| 图标间距 | 8rpx（即两个按钮中心距 96rpx） |
| 角标 | 高 32rpx，`min-width: 32rpx`，`padding: 0 8rpx`，圆角 pill，底 `--color-danger-solid`，字 `#FFF` 22rpx/500，定位 `top:-4rpx; right:-6rpx` |
| 红点 | 14×14rpx 圆，`--color-danger-solid`，`top:2rpx; right:2rpx` |
| 滚动后变化 | 位移 >8rpx：底不变或由透明渐显、加 `--shadow-nav`；**高度不变**（避免内容跳动） |

**必须替换的现状**：
- `home.vue` 的 `u-icon`（uview）+ 硬编码 `color="#2E2B29"` → 改 `luc luc-search` / `luc luc-scan-line` / `luc luc-bell`，色走 `--color-text`。否则深色模式下图标不可见。
- `messages.vue .navbar-icon` 56rpx、`community.vue .icon-btn` 60rpx → 88rpx。

### 3.4.4 顶部安全区（唯一写法）

当前 5 个页面有 4 种写法，必须收敛为一种。采用 `pet.vue` 的 `@supports` 能力查询方案（原因：自定义属性不能用"重复声明"兜底，第二条会直接覆盖第一条），做成全局工具类：

```scss
/* 全局唯一：顶部安全区。禁止在页面里再手写 calc() 或双声明 */
.safe-top {
  --sb: var(--status-bar-height, 0px);
  @supports (top: max(0px, 0px)) {
    --sb: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  }
  padding-top: var(--sb);
}
/* 顶栏：安全区 + 88rpx 内容高 */
.nav-safe {
  --sb: var(--status-bar-height, 0px);
  @supports (top: max(0px, 0px)) {
    --sb: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  }
  padding-top: var(--sb);
  min-height: calc(var(--sb) + var(--nav-h));
  box-sizing: border-box;
}
```

要点：**取 max 而非相加**（iOS 上 `env` 与 `--status-bar-height` 同为状态栏高，相加会翻倍）。

## 3.5 红线检查清单

落地后逐项验收，任一条不过就打回：

- [ ] 任意文字与其背景对比度 ≥ 4.5:1（大字 ≥ 3:1）；金色文字只用 `--ink-gold`
- [ ] 金色填充上无白字（用 `--on-primary`）
- [ ] 所有可点元素热区 ≥ 88×88rpx
- [ ] 无 `#ff4d4f`、`#007aff`、`#34c759`、`#ff3b30` 等非品牌硬编码色
- [ ] 无 20rpx 及以下字号承载信息
- [ ] 页面左右边距 = 32rpx（沉浸页除外）
- [ ] 无「白卡 + 1rpx 描边 + 阴影」三重叠加
- [ ] 深色模式下：无白色残留块、无不可见文字、无不可见阴影分层
- [ ] 无 `<svg>` / data-URI SVG 图标或占位图
- [ ] 衬线字体未用于中文字符

---

# 4. 逐页 Layout 规范

> 每页给：**版式骨架**（标数值）+ **关键模块规格** + **与旧版改动清单** + **▶ 单条最大改动**

---

## 4.1 Pet Hub · `pages/tabbar/pet`

### 4.1.1 版式骨架

```
 ┌─────────────────────────────────────────────┐ 750
 │ ▓▓▓ 场景 Immersive（满宽）▓▓▓                │
 │ h = max(50vh, 720rpx)，max-height 100vh      │
 │                                              │
 │  ● ● ● ●            ← 头像条 sb+24, 头像 80  │
 │                     ← gap 20,左 32          │
 │  ┌glass──────────┐          ┌glass───┐      │
 │  │GOLDEN  RETRIEVER│  sb+132  │装扮  ⌄ │      │
 │  │Luna, 3 岁      │           ├────────┤      │
 │  └────────────────┘           │3D 空间 │      │
 │                               └────────┘      │
 │              ┌──────────┐                    │
 │              │ 形象 320 │  居中              │
 │              └──────────┘                    │
 │           ░ 底部渐变遮罩 0 → 25% ░           │
 └─────────────────────────────────────────────┘
        ↓ margin-top: -32rpx（上叠场景）
 ┌─ Hero 卡 Care Deck ─ r32 · 白 ──────────────┐  24rpx 内距
 │ ┌──4rpx 状态条──┬──4rpx──┬──4rpx──┐         │
 │ │  ◯ 64 ok     │ ◯ warn │ ◯ err  │         │
 │ │  洗护        │ 疫苗   │ 驱虫   │         │
 │ │  12 天前     │ 3 天前  │ 今天   │         │
 │ │  良好        │ 即将到期│ 已过期  │         │
 │ └──────────────┴────────┴────────┘         │
 │ ─────────── 1rpx divider ───────────       │
 │ 下次护理  12 天后                 [⋯ 88]   │  行高 88
 └─────────────────────────────────────────────┘
        ↕ 48rpx
  快捷操作  ← 区块标题 32rpx/600，左 32
 ┌─────┬─────┬─────┬─────┬─────┐
 │ ◯80 │ ◯80 │ ◯80 │ ◯80 │ ◯80 │  图标底 fill 圆 80
 │ 商城 │ 晒宠 │ 日历 │ 记忆 │ 体重 │  标签 22rpx/500
 └─────┴─────┴─────┴─────┴─────┘  gap 16 · 无卡片容器
```

### 4.1.2 关键模块规格

| 模块 | 规格 |
|---|---|
| **场景图** | `h: max(50vh,720rpx)`，`max-height: max(920rpx,100vh)`；遮罩 `linear-gradient(180deg, rgba(0,0,0,.14), transparent 38%, rgba(0,0,0,.28))`（比现状顶部 `.1` 稍深，保证白字 4.5:1） |
| **宠物头像条** | 头像 80rpx，环 2rpx（未选 `rgba(255,255,255,.85)` / 选中 `--color-primary` + `0 0 0 6rpx --color-primary-soft`），gap 20rpx，`top: sb+24rpx`，左右 32rpx |
| **信息浮层** | `glass`：`background: rgba(26,24,22,.62)`（**由白底改炭底**）、`backdrop-filter: blur(20rpx)`、`1rpx rgba(255,255,255,.22)` 描边、圆角 pill（高 72rpx 时）/ `--radius-md`（两行时）；kicker 22rpx `rgba(255,255,255,.82)`，title 28rpx/600 `#FFF`。**选择 .62 的依据**：白字压在最坏情况（纯白场景图）下 = 4.94:1；旧方案 `rgba(255,255,255,.2)` + 白字在中等亮度图上仅 **1.58:1** |
| **右上按钮组** | 竖排 gap 16rpx；pill 高 64rpx，内距 `0 24rpx`，same glass；图标 28rpx + 文字 22rpx/600；**热区补到 88rpx 高（上下各留 12rpx 透明 padding）** |
| **Care Deck** | Hero 卡，`--radius-lg`，`--card-pad-lg`，`--shadow-card`，**无描边**；内部 3 列 `1fr`，列间 `1rpx` 竖分隔（`--color-divider`，上下各留 8rpx）；每列顶部 `4rpx` 状态条（`--ink-ok` / `--ink-warn` / `--ink-err`），圆角 2rpx，宽 100% 中留 32rpx；图标底圆 64rpx（`--color-ok-soft` / `--color-warn-soft` / `--color-err-soft`）内 `luc` 32rpx；标签 22rpx `--color-text-label`；值 28rpx/600 `--color-text`；状态词 22rpx/500 用 ink 色 |
| **提醒行** | 行高 88rpx，左"下次护理"26rpx `--color-text-label`，中"12 天"28rpx/600，右 `luc-more-horizontal` 40rpx `--color-text-tertiary` 但**热区 88×88** |
| **快捷宫格** | 5 列，gap 16rpx，**无卡片容器**（直接坐在 Canvas 上）；图标底圆 80rpx `--color-fill`，`luc` 36rpx `--color-text`；标签 22rpx/500，`margin-top: 16rpx`，单行省略；单项热区 = 整列 88rpx 高以上 |

### 4.1.3 与旧版改动

| # | 旧 | 新 | 原因 |
|---|---|---|---|
| 1 | 3 张独立护理卡（各带边框+圆角+阴影，3 列挤压） | 1 张 Care Deck，内部竖分隔 | 少 2 条边框、3 个阴影；高度省约 100rpx |
| 2 | `--hub-ok #34c759` / `--hub-err #ff3b30` | `--ink-ok #5F7259` / `--ink-err #A95245` | 去 iOS 系统色 |
| 3 | `care-status` 20rpx / `quick-label` 20rpx | 22rpx | 触碰可读下限 |
| 4 | 快捷宫格每项一张带描边白卡 | 无容器，图标圆 + 标签 | 5 个卡片框 = 10 条边框，纯噪音 |
| 5 | glass 用 `rgba(255,255,255,.2)` + 白字（中等亮度图上 1.58:1） | `rgba(26,24,22,.62)` + 白字（最坏 4.94:1） | 白底白字在场景图上完全失形 |
| 6 | `--font-size-xs` 本页覆盖为 24rpx | 移除覆盖，统一 22rpx | token 语义漂移 |
| 7 | 场景内 `z-index: 4/6/10/20` 魔数 | `1/2/3/var(--z-nav)` | 可维护 |
| 8 | 场景 `min-height: 720rpx` + `50vh` | 保留（宽屏逻辑正确），仅统一遮罩值 | 无功能缺陷，不动 |

### 4.1.4 ▶ 最大改动点

> **三张护理卡合并为一张 Care Deck（含 4rpx 状态条 + 竖分隔）**：把 3 套边框/圆角/阴影收敛为 1 套，同屏白卡从 9 个（3 护理 + 1 提醒 + 5 快捷）降到 2 个，页面的"呼吸"全部来自这一处释放。

---

## 4.2 Home · `pages/tabbar/home`

### 4.2.1 版式骨架

```
 ┌─ NavBar A1 ─ sb + 88 · 白 ──────────────────┐
 │ ( 🔍 搜索想要的…        )  [⛶ 88] [🔔 88]   │  搜索条 flex1 h72 pill
 └─────────────────────────────────────────────┘
        ↕ 24
 ┌─ 金刚区：无卡片，直接坐 Canvas ────────────┐
 │  ◯96     ◯96     ◯96     ◯96              │  底 fill 圆 96，icon 44
 │  分类     会员     领券     限时            │  标签 22/500 label
 └─────────────────────────────────────────────┘
        ↕ 24
 ┌─ Banner · r32 · 16:9 ──────────────────────┐
 │ [img aspectFill]                            │
 │ ░底部渐变 40% ░                             │
 │ [今日限定] 秋日护理礼盒          26/600 #FFF │  标签 22/700 金底炭字 r8
 └─────────────────────────────────────────────┘
        ↕ 32
 ┌─ Tabs 3 等分 · h88 ────────────────────────┐
 │  猜你喜欢    今日爆款    口碑好评            │  28/500 → 28/600
 │  ▁▁▁▁                                       │  下划线 48×4 r2 金
 └─────────────────────────────────────────────┘
        ↕ 24
 ┌─ Media 卡 ×2 列 · gap 24 ──────────────────┐
 │ ┌───────────┐ ┌───────────┐               │  卡宽 331
 │ │  图 1:1    │ │  图 1:1    │               │  图圆角 0（上随卡 24）
 │ │───────────│ │───────────│               │
 │ │ 商品名 2 行 │ │           │               │  28/500 1.4
 │ │ 描述 26    │ │           │               │  26/400 1.5 单行
 │ │ ¥128 ¥199 │ │           │               │  30/600 ink-gold
 │ │ ★4.8 · 1.2k│ │          │               │  22/400 label
 │ └───────────┘ └───────────┘               │
 └─────────────────────────────────────────────┘
```

### 4.2.2 关键模块规格

| 模块 | 规格 |
|---|---|
| **顶栏** | 变体 A1。搜索条 `h72` `--radius-pill` 底 `--color-fill`，内 `luc-search` 32rpx `--color-text-tertiary` + 占位 26rpx；右侧 2 个 88rpx 热区按钮，图标 40rpx `--color-text`；铃铛角标见 3.4.3 |
| **金刚区** | **无卡片**；4 列 gap 16rpx；图标底 96×96rpx `--radius-pill`（**圆，不是 16rpx 方**）底 `--color-fill`，`luc` 44rpx `--color-text`；标签 22rpx/500 `--color-text-label`，`margin-top: 12rpx`；单项竖向 padding 8rpx → 热区 ≈ 96+12+30+16 = 154rpx 高 ✓ |
| **Banner** | 左右 32rpx，比例 **16:9**（`padding-top: 56.25%`），`--radius-lg`，`--shadow-float`；标题浮层改为"底部渐变 + 无底板"：`linear-gradient(0deg, rgba(46,43,41,.62), transparent 100%)`，标题 26rpx/600 白，tag 22rpx/700 金底炭字 `--radius-xs`；**指示点必须带底板、禁止裸压图，色值见 §4.2.2.1（v1.11 重写，原「金 + `rgba(46,43,41,.3)`」全线不达标已撤回）** |
| **Tabs** | 3 等分，高 88rpx；默认 28rpx/500 `--color-text-label`；选中 28rpx/600 `--color-text`；下划线 **48×4rpx** `--radius-xs` 金色，位于 `bottom:0`，切换 `--dur-state var(--ease-standard)`；可吸顶（`--z-sticky`） |
| **商品卡** | Media 卡：`--radius-md`，图 `1:1`（`padding-top:100%`）圆角 0、`--shadow-card`、无描边；文字区 `padding: 20rpx`；标题 **2 行** 28rpx/500 `line-height:1.4`（`-webkit-line-clamp:2`，**最小高度 `2*1.4*28 = 78.4rpx` 固定**，避免卡高不齐）；描述 26rpx/400 `--color-text-label` 单行（lh 1.5）；价格 30rpx/600 `--ink-gold` + 原价 22rpx 删除线 `--color-text-tertiary`（**行高 1.2**）；评分 22rpx `--color-text-label` + 星 `luc-star` 24rpx `--color-warning`（**行高 = 24rpx = 图标高**）；行间距 **12rpx × 3**。**卡高 584rpx，完整构成见 §4.2.2.2 —— 不许再手估** |
| **加载/空态** | 骨架屏 6 张卡，见 §6.3 |

#### 4.2.2.1 Banner 指示点（v1.11 重写，值可直接抄）

**为什么重写**：原写法「`--color-primary` 激活 + `rgba(46,43,41,.3)` 非激活」**在任何底上都不成立**。实测 `rgba(46,43,41,.3)` —— 对品牌渐变左端 **1.70:1**、右端 **1.58:1**、页面底 **1.81:1**、白卡 **1.83:1**、遮罩最坏值 **1.42:1**，全线低于图形元素 3:1 的下限。（由 `frontend-engineer` 原型实测提出，复核一致。）

**结构性结论（比色值本身重要）**：**激活态不能用金色，也不能让半透明白裸压图。**

- **半透明白裸压图**：55% 白 vs 遮罩 **2.39:1**、60% 白 **2.57:1** —— 连 3:1 都不到。（`frontend-engineer` 原型试过这一版。）
- **金 `--color-primary` 当激活点**：浅色底板上对底板只有 **1.43:1**（金与暖白亮度太近，压根不构成图形对比）；即便补上底板，对非激活点也只有 **2.48:1**（浅）/ **1.51:1**（深）。
- **换 `--ink-gold` `#6E5C30`**：对底板 **5.64:1** ✓，但对非激活点掉到 **1.58:1** ✗。

> **根因**：非激活点为了过 3:1，必须被钉在"距底板刚好 3:1"的那一档亮度上（浅色底板 L ≈ **0.2544**、深色底板 L ≈ **0.2041**）。**3:1 这条线把可用亮度切成两段，每段只够站一个颜色** —— 中间调（金 L=0.5867、墨金 L=0.1118）夹在钉住点与极端档之间，对哪边都不够 3:1。所以激活态只能取极端档：**浅色底板取更暗（炭）、深色底板取更亮（纯白）**。品牌感交给底板承载，不押在那颗 8rpx 的点上。

**唯一可行解：先给指示点一块底板（把"局部背景"变成已知量），再把激活态推到极端档。**

| 项 | 浅色皮肤 | 深色皮肤 |
|---|---|---|
| 底板 | `rgba(255,255,255,.88)`，`--radius-pill`，`padding: 6rpx 10rpx`，点间 `gap: 8rpx` | `rgba(26,24,22,.72)`，其余同左 |
| 非激活点 | 8×8rpx 圆，`rgba(46,43,41,.58)` | 8×8rpx 圆，`rgba(255,255,255,.47)` |
| 激活态 | 16×8rpx pill，`--color-text` `#2E2B29` | 16×8rpx pill，纯白 `#FFFFFF` |
| 位置 | **点组 `right: 26rpx; bottom: 20rpx`**（点组自身约 68×20rpx）；**标题容器 `.banner__cap` 才是 `right: 132rpx`** —— 两个数属于两个元素，**不要写到一起** | 同左 |

> **位置这一行的 v1.11 写法是错的（v1.13 更正）**：原文只写「`right: 132rpx`」，读起来像"点组右偏移 132rpx"，实际那是**标题容器的右侧预留**。若点组也取 132，它会与标题容器右边缘齐平并向左侵入标题区。正确关系：点组 `right: 26`、标题容器 `right: 132`，实测标题最右不越 132、点组最左不越 96，**中间留 36rpx 空隙，`overlap: false`**（由 `frontend-engineer` 落地时按实测纠正）。

**四组实测对比度**（取 banner 图像的最亮 / 最暗两极，即最坏与最好情况）：

| 皮肤 | 图像 | 非激活 vs 底板 | 激活 vs 底板 | **激活 vs 非激活** |
|---|---|---|---|---|
| 浅 | 纯白（最坏） | 3.56:1 | 12.23:1 | **3.43:1** |
| 浅 | 纯黑（最好） | 3.42:1 | 11.05:1 | **3.23:1** |
| 深 | 纯白（最坏） | 4.04:1 | 12.40:1 | **3.07:1** |
| 深 | 纯黑（最好） | 4.76:1 | 17.53:1 | **3.68:1** |

四格全部 ≥3:1，**包括"激活 vs 非激活"这一项** —— 不靠"2 倍宽度"兜底，颜色本身就能分辨。

**三条铁律**：

1. **指示点禁止裸压图**，必须有底板。裸压图时背景是任意像素，任何色值都只在"刚好"的那张图上成立。
2. **激活态禁止用 `--color-primary` / `--ink-gold`**（理由见上）。可选：浅色底板加 `1rpx` `rgba(219,201,138,.55)` 描边作装饰 —— **但纯装饰，不承载信息，不参与验收**。
3. 这两组 `rgba` 是**混色值，不是品牌色，不写入 §2 token 表**（避免"就地造 token"被滥用）。现有 `--color-text-tertiary`（浅色 `#948E87`）在底板上仅 **2.82:1**，没有一档能过 3:1 —— 这也是这里必须写 rgba 的原因。

#### 4.2.2.2 商品卡高度构成（定值 **584rpx**）

卡宽 `331 = (750 − 32×2 − 24) / 2`，图 1:1 撑出 331。以下是**完整子元素清单**，每一项都写出**高度来源** —— 少一项就是少一个加数，写不出来源就是拍数：

| # | 子元素 | 高度来源 | 高度 |
|---|---|---|---|
| 1 | 图片区 | `padding-top: 100%`，1:1 | **331** |
| 2 | 文字区上内距 | `padding: 20rpx` 上 | **20** |
| 3 | 标题（2 行，固定最小高） | `28 × 1.4 × 2 = 78.4` | **78** |
| 4 | 行间距（标题→描述） | 定值 | **12** |
| 5 | 描述（单行） | `26 × 1.5 = 39` | **39** |
| 6 | 行间距（描述→价格） | 定值 | **12** |
| 7 | 价格行 | `30 × 1.2 = 36`（**卡内单行数字行，行高 override 为 1.2**） | **36** |
| 8 | 行间距（价格→评分） | 定值 | **12** |
| 9 | 评分行 | 由 `luc-star` **24rpx 图标**撑高，**必须显式 `line-height: 24rpx`** | **24** |
| 10 | 文字区下内距 | `padding: 20rpx` 下 | **20** |
| | **合计** | `331 + 20 + 78 + 12 + 39 + 12 + 36 + 12 + 24 + 20` | **584** |

> **两处行高 override 必须写进样式，不能吃默认值**（这是 548 / 583 / 593 三个数都"算得出来"的真正原因 —— **不是算式错，是行盒口径没定**）：
> ① 价格行 `line-height: 1.2`。按 §2.2 的 30rpx 默认 1.5 走，这一行变 **45**，卡高变 **593**（虚高 9rpx）。
> ② 评分行 `line-height: 24rpx`（= 图标尺寸）。让 22rpx 文字按默认 1.4 走，行盒变 30.8，卡高变 **591**（虚高 7rpx）。

> **规范 584 / 实现实测 583.9 / 容差 ±1rpx（v1.14 更正）**：本文给**完整构成式**（能自证的那个数）= 584；实现按同一份清单落地后实测 **583.9**，骨架搭 584，**两者差 0.1rpx**。0.2rpx 的缺口来自 12rpx 间隙的亚像素舍入（`12 × 0.52 = 6.24px`），不是某项口径错。
> **验收判据不是「卡高 == 584」，而是「骨架高 == 真实卡高」** —— 现状 0.1rpx，相等即通过。**不要把 584 当成一个必须命中的靶子。**
> **「583」已不存在，不要再引用**：那是**行高写死之前**的测量值（价格走浏览器默认 1.35 → 40.4；评分行 22×1.4 与 24 图标取大 → 28.8；间隙 26 = 3×8 加一个 `margin-top:2` 的历史补丁）。两处 `line-height` 写死后价格精确 36、评分精确 24，那个 583 已不可复现 —— 它和 583.9 差 0.9rpx，会被误当成"另一套基准"。

> **骨架必须照抄上面 10 行**：`skeleton-product` = 图 331 + 4 个文字块（78 / 39 / 36 / 24）+ 3 个 12rpx 间距 + 上下 20rpx 内距 = **584**。写成"图 331 + 3 行文字（78/39/36）"会得 **548**，实测矮 **36rpx**（= 评分行 24 + 间距 12）。

> **标题的 78 槽位内部可以再分**（`frontend-engineer` 落地做法，**几何不变，允许**）：78 里画两条 33rpx 骨块 + 12rpx 间距（`33 + 12 + 33 = 78`），观感像"两行字"而不是一整块 78 高的灰砖。**这不违反"4 个块"** —— 那 4 个块指**视觉行**（标题 / 描述 / 价格 / 评分），槽位内部怎么切由骨架自己定，只要外层仍是 78。写进这条是为了防止下一个人把它"修正"回单条大块，或反过来以为骨架有 6 个块。

### 4.2.3 与旧版改动

| # | 旧 | 新 | 原因 |
|---|---|---|---|
| 1 | 金刚区在 24rpx 边距的白卡里，banner 32rpx | 全部 32rpx，金刚区去卡片 | 8rpx 错位 + 少一个卡片框 |
| 2 | 金刚区图标底 `--radius-md` 16rpx 方块 + `--background-200` | `--radius-pill` 圆 + `--color-fill` | 圆底在小尺寸下更精致；且 `--background-200` 系列是硬编码色阶，深色易漏 |
| 3 | navbar 用 `u-icon` + 硬编码 `#2E2B29` | `luc` 图标 + `--color-text` | 深色模式图标消失 |
| 4 | 商品卡 24rpx 圆角 + 24rpx 图圆角 + 1rpx 描边 | 24rpx 卡 + 图圆角 0 + 无描边 + `--shadow-card` | 双层弧线 + 描边叠加 |
| 5 | 商品名单行省略 | 2 行 + 固定高度 | 单行截断浪费信息，2 行不固定高度会参差 |
| 6 | 价格色 `--color-primary`（1.65:1） | `--ink-gold`（5.83:1） | 不可读到可读 |
| 7 | 推荐 Tabs 无吸顶 | 吸顶 `--z-sticky` + 滚动后 `--shadow-nav` | 长列表里可切换 |
| 8 | `gap: 16rpx` | `gap: 24rpx` | 卡间距统一 |

### 4.2.4 ▶ 最大改动点

> **金刚区去卡片化 + 全页左基线统一 32rpx**：顶部到商品流的所有左边缘落在同一条线上，首屏从"四块拼贴"变成一个整体，同时同屏白卡从 8 个降到 3 个（banner、6 商品卡合并视作一组）。

---

## 4.3 Community · `pages/tabbar/community`

### 4.3.1 版式骨架

```
 ┌─ NavBar B · Serif ─ sb + 88 · 透明→白 ──────┐
 │  社区 44/600            [🔍 88] [🔔 88 ·]   │
 └─────────────────────────────────────────────┘
 ┌─ Tabs 3 等分 · h88 · 吸顶 ──────────────────┐
 │   推荐      关注      话题                   │
 │   ▁▁▁▁                                       │
 └─────────────────────────────────────────────┘
        ↕ 16
 │  ( 🔍 搜帖子 / 话题 )               h72     │  左 32 右 32
        ↕ 16
 │ #秋日遛狗  #新手养猫  #宠物友好餐厅  →       │  chip h56 · gap 16
        ↕ 16
 ┌─ Standard 卡 · r24 · 无描边 · shadow-card ──┐
 │ ◯72  昵称 28/600               ⋯ 88        │  pad 24 24 16
 │      2 小时前 22/label                      │
 │ ┌─────────┬─────────┬─────────┐            │  九宫格 gap 8 r16
 │ │  img    │  img    │  img    │            │  cell 1:1
 │ └─────────┴─────────┴─────────┘            │
 │ 正文 28/400 · lh1.55 · 最多 4 行             │  pad 0 24 16
 │ ♡ 128      💬 32      ↗ 分享                 │  行高 88 · gap 48
 └─────────────────────────────────────────────┘
        ↕ 24（卡间距）
 ┌─ 下一张卡 ──────────────────────────────────┐
                                                    ┌──────┐
                                                    │  ✎   │ FAB 112
                                                    └──────┘ r32 · 24
```

### 4.3.2 关键模块规格

| 模块 | 规格 |
|---|---|
| **顶栏** | 变体 B。底色 `--color-background`；滚动 >8rpx 后过渡到 `--color-surface` + `--shadow-nav`（`--dur-state`）。标题 **44rpx/600**（拉丁用 `--font-display`，中文用 `--font-ui` + `-0.02em`）。右侧 2 个 88rpx 按钮，图标 40rpx `--color-text`，通知点见 3.4.3 |
| **Tabs** | 3 等分，高 88rpx；默认 28/500 `--color-text-label`；选中 28/600 `--color-text`；下划线 48×4 金色；**金不是文字色**（旧版选中文字是金 → 1.48:1） |
| **搜索条** | 左右 32rpx，高 72rpx，pill，底 `--color-fill`，图标 32rpx，占位 26rpx `--color-text-tertiary`；整条热区 88rpx（上下各扩 8rpx） |
| **话题 chip** | 高 56rpx，内距 `0 24rpx`，pill，底 `--color-primary-soft`，字 **22rpx/500 `--ink-gold`**（5.96:1，旧为 1.4:1）；gap 16rpx；首尾与 32rpx 边距对齐；**热区上下各扩 16rpx 至 88rpx** |
| **帖子卡** | Standard 卡：`--radius-md`、**无描边**、`--shadow-card`；头行 `padding: 24rpx 24rpx 16rpx`，头像 72rpx（圆）+ 昵称 28/600 + 时间 **22rpx `--color-text-label`**，右侧 `luc-more-horizontal` 40rpx / **热区 88** |
| **图片** | 单图：**`mode="widthFix"`，高度随原图比例**（≈4:3，**不锁死**）+ `--radius-sm` 16rpx；多图只定**列数** —— **1 张 = 单图 / 2 张与 4 张 = 2 列 / 3 张与 5–9 张 = 3 列**（行数由张数自然流成）；`grid gap 8rpx`，cell 1:1，圆角 16rpx；图区左右各 24rpx。逐档尺寸与卡高见 §4.3.2.1 |
| **正文** | **28rpx/400，`line-height: 1.55`，`--color-text`，最多 4 行**（`-webkit-line-clamp:4`），左右 24rpx |
| **互动行** | 高 88rpx，gap 48rpx；图标 32rpx + 计数 22rpx/500；默认 `--color-text-label`；已赞用 `--ink-rose` 实心（`luc-heart`，色变即可，图标字体无填充版）；单项热区 ≥ 88×88 |
| **FAB** | 112×112rpx，`--radius-pill`，底 `--color-primary`，图标 `luc-camera` 48rpx **`--on-primary`（炭色）** —— 8.52:1（浅）/ 6.89:1（深），符合"金色实心块只放炭色内容"总规则；`--shadow-float`；位置 `right: 32rpx; bottom: calc(var(--tabbar-offset) + 32rpx)`，`--z-fab`。可选 `inverse` 变体：底写死 `#2E2B29` + 图标写死 `#DBC98A`（8.52:1），用于需要更强压场的场景；**两变体都不允许白字/白图标** |
| **空态/加载** | 见 §6.3；空态用 `MyEmptyState`：`luc-message-square` 120rpx `--color-text-tertiary` + 26rpx 文案 + 次按钮 |

> **正文截断的两个实现陷阱**（`frontend-engineer` 落地实测，写进规范防回归）：
> ① `-webkit-line-clamp:4` 生效需要 `display:-webkit-box` + `-webkit-box-orient:vertical` + `overflow:hidden`，**不要额外加 `white-space: pre-wrap`** —— 加了之后换行符参与行盒计算，实际行数超过 4，末行会被切掉半截。
> ② 截断框的**底部留白用 `margin-bottom`，不要用 `padding-bottom`** —— 实测写成 `padding-bottom: 16rpx` 时第 4 行被裁掉下半截，改 `margin-bottom: 16rpx` 后正常（"4 行排满、再空 16rpx 到互动行"）。

#### 4.3.2.1 帖子卡几何（定值，Phase 3 直接取用）

宽度推导链（750 设计宽）：`页面 750 − 左右 gutter 32×2 = 卡宽 686 − 图区左右 24×2 = 媒体区 638rpx`。

**图片布局分档（唯一规则）** —— **只需决定"列数"，行数由条目数自然流成**，不要按行数建类：

> **⚠️ 表头前提：「卡总高」列是「正文 4 行」时的参考值**（正文块 `174 = 28 × 1.55 × 4 = 173.6` 取整）。正文行高 = **43.4rpx**，因此 **实际卡高 = 表值 − (4 − 实际行数) × 43.4rpx**。3 行 −43.4 / 2 行 −86.8 / 1 行 −130.2；纯文本帖（无图、正文 2 行）≈ **319rpx**。**跳过这句，回归验收会把这几十 rpx 当成 bug 反复核**（由 `frontend-engineer` 落地实测提出）。

| 张数 | 列数 | 行数（自然流成） | cell 边长 | 图区总高 | 卡总高 | 理由 |
|---|---|---|---|---|---|---|
| 1 | 单图 | — | 638 × **≈478（随原图）** | **≈478rpx（浮动）** | **≈884rpx（浮动）** | 宽幅单图，信息流主视觉；**卡高随原图比例浮动，不参与骨架等高要求** |
| 2 | **2 列** | 1 行 | **315rpx** | **315rpx** | **721rpx** | 并排一对，不留空 |
| 3 | **3 列** | 1 行 | **207rpx** | **207rpx** | **613rpx** | 一排三张，最紧凑 |
| 4 | **2 列** | 2 行 | **315rpx** | **638rpx** | **1044rpx** | 2×2 方块，**不做 3+1** |
| 5–6 | **3 列** | 2 行 | **207rpx** | **422rpx** | **828rpx** | 末行允许 2–3 张 |
| 7–9 | **3 列** | 3 行 | **207rpx** | **638rpx** | **1044rpx** | 满格，与 4 图同高（见下注） |

推导参数：`卡宽 686 = 750 − 32×2`；`媒体区 638 = 686 − 24×2`；`2 列 cell = (638−8)/2 = 315`；`3 列 cell = (638−16)/3 = 207.33`；`卡总高 = 112(头行) + 图区总高 + 16 + 174(正文 4 行) + 16 + 88(互动行)`。

> **取整口径**：表内一律取整到 rpx，**误差 ≤1rpx 属正常**（3 图精确 613.33→记 613、5–6 图精确 828.67→记 828）。实测 612 或 829 **都不是缺陷**，不要为这 1rpx 反复核。
> **全表精确值一览（v1.14 补，防止再出现"凭印象取整"）**：2 图 **721.0000**（精确）／3 图 **613.3333**（floor）／4 图 **1044.0000**（精确）／5–6 图 **828.6667**（floor）／7–9 图 **1044.0000**（**精确**）。
> 注意两点：① **7–9 图没有任何小数** —— 图区 `3×(622/3) + 8×2 = 622 + 16 = 638`，整整齐齐；所以**「1043.67」是一个不存在的数**（若卡高 1043.67，图区须为 637.67 = `3×207.22+16`，无来源）。② **全表只有"精确值"与"floor"两类，没有一处向上取整** —— 因此 ±1rpx 的来源是**实现侧浏览器 subpixel 舍入**，不是文档侧取整方向；**规范给出的定值永远是 613 / 828，实测偶尔差 1 不等于规范允许 612**。两条混讲会让验收的人以为"612 是规范放的"，实际是"规范 613、实现差 1"。判据用「**同类卡片互相等高 + 骨架与真实卡同高**」，不用"命中某个整数"。（本条由 `ux-ui-designer` 复算 `622/3` 全精度后纠正 —— 我此前口头给过 `1043.67` 与"floor/ceil 混用"，两个说法都错，未进正文，此处留痕。）

> **7–9 图 = 1044，与 4 图完全相同，不是笔误**：两种**满格**宫格（2 列 2 行、3 列 3 行）都正好填满媒体区宽度，都是 **638 × 638 的正方形**，图区同为 638 → 卡高必然相同。**凡是满格宫格，卡高恒为 1044，与列数无关。**（此前表内记 1043，无任何算式支撑、也无出处；从 v1.3 起躺了 8 个版本未被质疑，v1.11 更正，见 §7.4。）

**正文行数 → 卡高（默认 = 3 图形态；换别的图区直接叠加图区差）**：

| 正文行数 | 正文块 | 卡高 | 与 4 行的差 |
|---|---|---|---|
| 4（默认，`-webkit-line-clamp:4`） | 174rpx | **613rpx** | — |
| 3 | 130rpx | 570rpx | −43.4 |
| 2 | 87rpx | 526rpx | −86.8 |
| 1 | 43rpx | 483rpx | −130.2 |
| 纯文本帖（无图，正文 2 行，图区按 0 计） | 87rpx | **319rpx** | 不套用本列 |

> 逐行核对：`613 − 570 = 43.4`、`570 − 526 = 43.4`、`526 − 483 = 43.4`，与行高 `28 × 1.55 = 43.4` 一致。**骨架屏固定取「4 行」那一档（613rpx）**，见 §6.3。

> **4 张为什么不做 3+1**：3 列放 4 张会在第二行右侧留 `207×207rpx` 的空洞，也就是卡片底部一块 2 个 cell 宽的空白，在信息流里是最显眼的"未完成感"。2×2 虽然把卡高推到 1044rpx，但视觉上是完整的方块，符合品牌"画册感"。
> **3 张为什么必须 3 列**：2 列放 3 张会变成「2 + 1（右侧空一格）」，卡高翻倍到 721rpx 且右下留洞 —— 这是最差解。

> **一句话规则（主理人 v1.8 定稿，对外统一用这句）**：**2 或 4 图用 2 列，其余多图用 3 列。**

**改前 / 改后对照（回归与对外沟通都用这张）**：

| 张数 | 旧（`count<=3 → col-2`） | 新（三支映射） | 变化 |
|---|---|---|---|
| 1 | ≈884rpx | ≈884rpx | 不变（**保持 `widthFix`，本次不回退成固定 4:3**） |
| 2 | 721rpx | 721rpx | **不变** |
| 3 | 1044rpx | **613rpx** | 矮 **431rpx**（变密） |
| 4 | 828rpx | **1044rpx** | **高 216rpx（变疏）** |
| 5–6 | 828rpx | 828rpx | 不变 |
| 7–9 | 1044rpx | 1044rpx | 不变（满格宫格恒 638 图区） |

> **只有 3 图帖变密，4 图帖反而变疏。** "整体变密"是错的说法，不要对外承诺"每屏多 X 张"这类固定值 —— 实际观感取决于该 feed 的图片张数分布。

**密度调节的杠杆顺序（防止未来为"密度"破坏网格）**：

若日后觉得信息流太疏或太密，可调项按此顺序取，且**必须止步于第 ④ 项**：

| 序 | 杠杆 | 收益 | 风险 |
|---|---|---|---|
| ① | 正文行数 4 → 3 行 | **−43.4rpx**/卡 | 信息量减少，需产品确认 |
| ② | 头行内距 `24/16` → `16/12` | −12rpx/卡 | 头像与昵称略挤 |
| ③ | 图区上下间距 `16` → `12` | −4rpx/卡 | 几乎无感 |
| ④ | 卡间距 `24` → `16` | −8rpx/张 | 卡组感变弱 |
| ✗ | **改 cell 比例（4:3 / 4:5）** | — | **禁止**：2 列与 3 列将无法共用 `grid-auto-rows`，网格一致性地基被拆 |

**「把 4 图帖做矮一点」这个需求，答案不是动图区，是动 ①–④。** `cell` 永远 1:1，不在可调项内。

**⚠️ 现状实现与本节不一致，落地时必须改**：

`community.vue` 第 565–569 行 `getImageGridClass()` 当前是
`count <= 1 → single` / `count <= 3 → col-2` / `else → col-3`。
即 **3 张走 2 列**（图区 638rpx、卡高 1044rpx）、**4 张走 3 列**（图区 422rpx、卡高 828rpx）—— 与本节规范分别差 **431rpx**（3 张）与 **216rpx**（4 张）。

正确映射 —— **不需要新增任何 CSS 类**。`grid-template-columns` 只定列数，4 张放 2 列会自动流成 2 行，2 张放 2 列自然只有 1 行。因此 `col-2` 同时承载 2 张与 4 张：

```js
getImageGridClass(count) {
  if (count <= 1) return 'single'              // 单图：widthFix，卡高随原图（≈884）
  if (count === 2 || count === 4) return 'col-2' // 2 列：2 张 1 行 / 4 张 2 行 → 315 / 638
  return 'col-3'                               // 3 列：3 张 1 行 / 5–9 张 2–3 行 → 207 / 422 / 638
}
```

> 曾考虑新增 `col-2x2` 表达 4 张的 2×2。**判定为不需要**：它的 `grid-template-columns` 与 `grid-auto-rows` 与 `col-2` 完全相同，唯一差异是条目数导致的行数，属于内容属性而非样式属性。多一个类只会带来两处风险——`::v-deep` 覆盖选择器漏写、以及后来人误以为 2×2 需要独立间距。**列数是样式，行数是内容。**

**三条落地铁律**：

1. **cell 边长必须用 `calc()` 由视口推导，不要写死 207 / 315**：
   ```scss
   /* 媒体区 638rpx = 100vw − 64rpx(页面 gutter) − 48rpx(图区内距) */
   .grid-col-2 { grid-auto-rows: calc((100vw - 112rpx - 8rpx) / 2); }   /* = 315 */
   .grid-col-3 { grid-auto-rows: calc((100vw - 112rpx - 16rpx) / 3); }  /* = 207.33 */
   ```
   写死整数会让 1rpx 误差在 3 列时累积成右侧 2rpx 的空白不齐，肉眼可见。
2. `uni-image` 在 `grid` 中不响应 `aspect-ratio`，必须用 `grid-auto-rows` 显式给行高 + 内层 `width/height:100%`（现有 `community.vue` 的做法是对的，保留该技巧）。
3. **骨架屏 `skeleton-post` 固定用「3 图形态 613rpx」，不逐张对高。**
   原因：骨架渲染的那一刻数据还没到，前端**不可能预知每张卡是几图**，要求"按张数查表"在实现上不可完成（v1.3 曾如此要求，v1.4 修正）。
   取值理由：3 图是发帖众数，613rpx 又是 6 档里的中位高，误差双向摊薄。
   配套约束（与 `02-ux-spec.md` §3.4.1「A/B 两类切换」一致）：
   - **A 类**（`normal / empty / error / guest / stale` 互切）→ **严格零位移**，同容器同几何，只换图标与文案；
   - **B 类**（`loading → normal`，骨架→内容）→ 允许高度变化，但必须：内容淡入 200ms、**不做高度补间**、**不重置滚动位置**、**首卡顶边不跳**；且首屏骨架**只渲染 2–3 张卡**（见 §6.3），把累计位移压到最小。


### 4.3.3 与旧版改动

| # | 旧 | 新 | 原因 |
|---|---|---|---|
| 1 | 选中 Tab 文字金色（1.48:1） | `--color-text` 600 + 金色下划线 | 可读性；金色归位到"下划线" |
| 2 | FAB 金底白图标（1.65:1） | 金底 + **炭色图标** `--on-primary`（8.52:1） | 图标必须看得见；且与 `user.vue` 登录按钮的正确写法对齐 |
| 3 | 话题 chip 金字 `--color-primary`（≈1.4:1） | `--ink-gold`（5.96:1） | 同上 |
| 4 | 帖子卡「白底 + 描边 + 阴影」 | 白底 + `--shadow-card`，**删描边** | 三重重叠 |
| 5 | 正文 30rpx/600 | **28rpx/400，lh1.55，4 行截断** | 正文不该比标题重；600 正文像标语不像内容 |
| 6 | ~~单图 `mode="widthFix"` 高度不定~~ → **保留 `widthFix`** | **不改代码**；规范侧改为「≈884rpx，随原图浮动」 | **本条已撤回**（主理人 v1.8 裁决）：那次改动修的是"宽屏下图片被裁成扁条"的真实缺陷，回退成固定 4:3 会重新引入裁切。且 B 类切换只要求"首卡顶边不跳、滚动不重置"，从未要求单图卡等高 |
| 7 | 卡片内图片内距 `20rpx` | 24rpx | 对齐 24 内距体系 |
| 8 | 图片占位用 `data:image/svg+xml` | 纯色块 `--color-surface-sunken` + `luc-image` 居中 | 违反"禁 SVG"约束，APP 端可能空白 |
| 9 | `.icon-btn` 60rpx / `.more-btn` 56rpx | 88rpx | 触摸目标 |
| 10 | feed 左右 16rpx | 32rpx | 栅格统一 |
| 11 | FAB 阴影 `rgba(0,122,255,.35)` | `--shadow-float` | 蓝色光晕 |
| 12 | 图片列数映射 `count<=3 → col-2` | **`2 张与 4 张 → 2 列；3 张与 5–9 张 → 3 列`**（无需新增类，行数由张数流成） | 3 张 2 列会渲染成「2+1 留洞」且卡高翻倍（差 431rpx）；4 张 3 列会「3+1 留洞」 |

### 4.3.4 ▶ 最大改动点

> **帖子正文降权（30rpx/600 → 28rpx/400/lh1.55/4 行）+ 卡片去描边**：信息流的焦点交还给图片，滚动时是"图—图—图"的节奏，而不是一排等粗的黑字在喊。

---

## 4.4 Messages · `pages/tabbar/messages`

### 4.4.1 版式骨架

```
 ┌─ NavBar A2 ─ sb + 88 · 白 ─────────────────┐
 │  消息                       [🔔 88][👥][＋] │  标题 36/600
 └─────────────────────────────────────────────┘
        ↕ 24
 ┌─ 通知聚合 Standard 卡 · r24 ───────────────┐
 │  ◯72     ◯72     ◯72     ◯72              │  icon 36, 底 fill 圆
 │  全部     互动     订单     系统             │  22/500 label
 │  ·未读点 14rpx --color-danger-solid        │
 └─────────────────────────────────────────────┘
        ↕ 32
  私信 ①3                    发起私信 ›      │  标题 32/600 + badge 32
 ┌─ List 组 · r24 · 无阴影 ───────────────────┐
 │ ◯88  昵称 32/600            14:20 22/label│  行高 104 · pad 0 24
 │      预览 26 label 单行      ②3            │  gap 8
 │ ───────── 1rpx 缩进 132 ─────────          │
 │ ◯88  客服小助手             昨天           │
 │      您好，请问…                            │
 └─────────────────────────────────────────────┘
  未读行：整行底 --color-primary-soft + 名称 600
  左滑：露出 140rpx 删除按钮（--color-danger-solid）
 ┌─ 加载更多 · h88 居中 ──────────────────────┐
```

### 4.4.2 关键模块规格

| 模块 | 规格 |
|---|---|
| **顶栏** | 变体 A2，标题 36rpx/600；右侧 **3 个** 88rpx 按钮（铃铛/群聊/发起），图标 40rpx，间距 0（相邻热区共用边）；铃铛角标按 3.4.3 |
| **通知聚合卡** | Standard 卡，`--radius-md`，`--shadow-card`，`padding: 24rpx 0`；4 列 `1fr`；图标底圆 72rpx `--color-fill`（**不用金色**，避免与状态色混淆），`luc` 36rpx `--color-text`；标签 22rpx/500 `--color-text-label`；未读点 14rpx 圆 `--color-danger-solid`，定位 `top:0; right:0`（相对图标圆） |
| **区块头** | 高 48rpx，左右 32rpx：标题 32rpx/600 + 未读数 pill（高 32rpx，底 `--color-danger-solid`，字 22/500 白）；右侧"发起私信"为 Text 按钮 26rpx/500 `--ink-gold`，热区 88rpx |
| **会话组** | List 组：`--radius-md`，`--color-surface`，**无阴影无描边**（它是列表不是卡）；行高 **104rpx**，`padding: 0 24rpx`，头像 **88rpx** 圆（字母兜底底 `--color-fill` + 字 `--color-text` 32/600，**不用金底白字**），主体 gap 8rpx；名称 32rpx/600（未读 700）；预览 26rpx/400 `--color-text-label` 单行；时间 22rpx `--color-text-label`；未读 pill 高 32rpx `--color-danger-solid` |
| **未读行态** | 整行底 `--color-primary-soft`；名称 600→700；未读 pill 保留。**行态是"整行"而非仅 pill**，提高扫读效率 |
| **分隔线** | `1rpx --color-divider`，左缩进 `24 + 88 + 20 = 132rpx`，右 0；末行不画 |
| **左滑删除** | 按钮宽 140rpx，底 `--color-danger-solid`，字 28/500 白；跟手时 `transition: none`，松手 `--dur-state`；首末项跟随组圆角（24rpx） |
| **加载更多** | 高 88rpx 居中，字号 26rpx `--color-text-label`，整行可点；加载中显示 `luc` 旋转或三点 |
| **空态** | `luc-message-square` 120rpx `--color-text-tertiary` + 26rpx 文案 + Secondary 按钮 |
| **未登录卡** | Standard 卡，`padding: 48rpx 32rpx`，标题 28rpx/600 + 主按钮 M（金底炭字） |

### 4.4.3 与旧版改动

| # | 旧 | 新 | 原因 |
|---|---|---|---|
| 1 | 未读只靠一个小红 pill | **整行浅金底 + 名称加粗** + pill | 列表 20 条时，扫读找未读是高频动作 |
| 2 | `#ff4d4f`（白字 3.27:1） | `--color-danger-solid #B4574A`（4.77:1） | 对比度 + 去 iOS 红 |
| 3 | 会话头像金底白字母（1.65:1） | `--color-fill` 底 + `--color-text` 字母 | 可读 + 金色不滥用 |
| 4 | 通知图标底 `--color-background` | `--color-fill` | 卡内凹陷层级更明确 |
| 5 | 会话行 `padding: 24rpx`（高约 136rpx 但留白均分） | 行高 **104rpx** 固定 + `padding: 0 24rpx` | 列表节奏统一，同屏多看 1 条 |
| 6 | 会话名 28rpx/500 | 32rpx/600 | 列表项标题需要压过预览 |
| 7 | 预览文字 `--color-text-tertiary`（2.70:1） | `--color-text-label`（4.88:1） | 预览是核心信息 |
| 8 | `navbar-icon` 56rpx | 88rpx | 触摸目标 |
| 9 | 未登录按钮金底白字 | 金底 `--on-primary` | **修 bug**：`messages.vue` 与 `user.vue` 同一按钮两套写法 |
| 10 | 会话组有 `overflow:hidden` + 无阴影 | 保留 `overflow:hidden`（左滑必需），**不加阴影** | 列表不该有"卡片感" |

### 4.4.4 ▶ 最大改动点

> **未读从"一个小红点"升级为"整行浅金底 + 名称加粗"**：这是唯一同时提升视觉节奏（列表出现金色横条分隔）与可用性（20 条列表里 0.3 秒定位未读）的改动，且只用了一个已有 token `--color-primary-soft`。

---

## 4.5 User · `pages/tabbar/user`

### 4.5.1 版式骨架

```
 ┌─ 无顶栏：Header 直接起（白底）────────────────┐
 │        sb + 48                                │
 │  ◯120  昵称 36/600                       › 48 │
 │        邮箱 26 label                          │
 │        ✦ GOLD 会员 22/500 ink-gold            │
 └───────────────────────────────────────────────┘
        ↕ 24
 ┌─ 会员卡 Hero · r32 · h200 ──────────────────┐
 │  Linen 纸感 + 1rpx --ink-gold 描边(32% )     │
 │  ATELIER 会员                    ◈ trophy 水印│
 │  1,280 分   ← 56/600 display                 │
 │  再消费 320 元升级 22 label        ›          │
 └──────────────────────────────────────────────┘
        ↕ 24
┌─ 钱包 Standard 卡 · r24 ────────────────────┐
│  ◯  12        ◯  1,280    ◯  3   ◯  12      │  图标 32 fill 圆 56
│  浏览历史      积分         优惠券    收藏      │  数字 32/600 · 标签 22
└──────────────────────────────────────────────┘
        ↕ 24
 ┌─ 社交 4 格（social-grid）────────────────────┐
 │   12 关注    348 粉丝    26 收藏    8 帖子    │
 └──────────────────────────────────────────────┘
        ↕ 24
 ┌─ 订单 Standard 卡 · r24 ────────────────────┐
 │  我的订单                        全部 ›      │
 │  ⬚        ⬚        ⬚        ⬚              │  luc 48 · 24 内距
 │ 待付款    待发货    待收货    待评价          │  标签 22
 │  ·②      ·①                                  │  badge
 └──────────────────────────────────────────────┘
        ↕ 24
 ┌─ 功能 List 组 · r24 ────────────────────────┐
 │  ▫ 每日签到                             ›   │  行高 104
 │  ▫ 任务中心                             ›   │  icon 40 · label 28
 │  ...                                        │
 └──────────────────────────────────────────────┘
        ↕ 48
         MOYUYO ATELIER / 与爱宠共度的每一天       22 label 居中
```

### 4.5.2 关键模块规格

| 模块 | 规格 |
|---|---|
| **Header** | 底 `--color-surface`，`padding: 48rpx 32rpx` + `.safe-top`；头像 120rpx 圆（`2rpx --color-surface` 内白边）；昵称 36rpx/600；邮箱 26rpx `--color-text-label`；会员等级 22rpx/500 `--ink-gold` + 前置 `luc-award` 22rpx；右侧 `luc-chevron-right` 40rpx `--color-text-tertiary` 热区 88 |
| **会员卡** | Hero 卡 `--radius-lg`，高 **200rpx**；底：`linear-gradient(135deg, #FBF7EE 0%, #F3EBD8 100%)`（浅）/ `linear-gradient(135deg,#332E26,#2A2724)`（深）；**`1rpx rgba(110,92,48,.28)` 描边**（深色 `rgba(227,206,147,.22)`）；右上 `luc-trophy` 160rpx `--color-primary` `opacity:.12` 作水印（`position:absolute`）；标题 26rpx/500 `--ink-gold` letter-spacing `.08em`；**积分 56rpx/600 display 字体 `--color-text`**；提示 22rpx `--color-text-label`；右下 `luc-chevron-right` 36rpx `--ink-gold` |
| **钱包 4 宫格** | Standard 卡 `padding: 24rpx 0`；4 列，**四格一律是"计数"而非金额**，顺序固定：**浏览历史 / 积分 / 优惠券 / 收藏**（对齐现状 `user.vue` 与主理人 F1 裁决 —— 首格是浏览历史数，**不是「余额」**，本套设计不引入钱包余额）；每列：图标底圆 56rpx `--color-fill` + `luc` 28rpx `--color-text-secondary`，下接数字 32rpx/600 `--color-text`，下接标签 22rpx/500 `--color-text-label`；竖排列 gap 8rpx。**四格数字必须 `font-variant-numeric: tabular-nums` 且同字号**，位数不同时不得左右抖动（切态显示 `—` 时同样占位对齐） |
| **社交 4 格** | 数字 32rpx/600 + 标签 22rpx/500；4 列均分；`social-grid` 组件沿用，仅换 token |
| **订单卡** | Standard 卡；头行：标题 32rpx/600 + "全部" Text 按钮（`--ink-gold` 26rpx/500，热区 88）；4 列，图标 **`luc` 48rpx `--color-text`**（**替换现有 emoji**：`luc-credit-card` / `luc-shopping-cart` / `luc-truck` / `luc-verified`），标签 22rpx/500 `--color-text-label`；角标 pill 高 32rpx `--color-danger-solid`，定位 `top:4rpx; right:50%; transform: translateX(60rpx)`（保持在图标右上，不压标签） |
| **功能列表** | List 组；行高 **104rpx**，`padding: 0 24rpx`；图标 40rpx `--color-text-secondary`（宽 40 + gap 24）；文字 28rpx/400 `--color-text`；`luc-chevron-right` 32rpx `--color-text-tertiary`；分隔线左缩进 `24+40+24=88rpx`；末行无 |
| **页脚** | `padding: 48rpx`，两行 22rpx/500 `--color-text-tertiary`，gap 8rpx，居中 |
| **未登录态** | Header 保持白底，头像用默认图 120rpx，标题 36rpx/600「登录 / 注册」，副标题 26rpx `--color-text-label`；右侧主按钮 S（金底炭字，高 64 + 热区 88）；会员卡/钱包/订单/社交**不渲染**（现状一致），功能列表保持可点（点击走 `promptLogin`） |

**「浏览历史」与「浏览足迹」的命名统一（待主理人确认，UX 已写入 `02-ux-spec.md` §2.5）**

钱包第 1 格与功能列表第 8 项在工程上指向**同一路由** `/pages/user/browsing-history`，但现状两处文案不同（`walletHistory` / `featureHistory`）。视觉侧口径：

- **文案**：两处统一「**浏览历史**」，清楚无歧义；
- **图标**：两处统一 `luc-history`（lucide 已存在该类）。同一功能出现两个图标，用户会误以为是两件事；
- **`luc-footprints` 保留给品牌叙事**（宠物记忆、成长足迹、时间轴），不用于功能入口 —— 这枚图标的情感色彩更强，用在"功能列表的一行"里是浪费。


### 4.5.3 与旧版改动

| # | 旧 | 新 | 原因 |
|---|---|---|---|
| 1 | 会员卡 `linear-gradient(--color-primary → #004fad)` + `rgba(0,122,255,.3)` 阴影 | Linen 纸感渐变 + 金色描边 + 无水印阴影 | 现状浅色下是**金→深蓝**渐变，与品牌完全冲突 |
| 2 | 订单图标是 emoji（4 个彩色字符） | `luc` 线性图标 48rpx `--color-text` | emoji 与全站线性图标体系冲突，且各机型渲染不一致 |
| 3 | `.card` 阴影 `0 4rpx 24rpx rgba(0,0,0,.08)` | 无阴影（List 组）/ `--shadow-card` | 大范围灰雾让整页发灰 |
| 4 | 钱包是「数字 + 标签」两行纯文本 | 图标底圆 56 + 数字 + 标签 | 4 个裸数字难以快速分辨 |
| 5 | 功能行 `padding: 24rpx 0`（高约 88）+ 卡片 `padding: 24rpx` | 行高 104rpx，内距在行上 | 行高与卡片内距混算，首末行留白不均 |
| 6 | 会员卡高 200rpx 但内容 `padding: 32rpx` 顶部无对齐 | 内容 `padding: 32rpx`，积分基线固定 | 会员卡内文字拥挤 |
| 7 | `member-level` 用 `--color-primary-dark`（2.41:1） | `--ink-gold`（5.83:1） | 等级是身份信息，必须可读 |
| 8 | 页脚 `padding: 48rpx 24rpx` | 48rpx，字号 22rpx | 栅格统一 |
| 9 | Header `padding-top: 48rpx + 安全区`（calc 写法） | `.safe-top` 工具类 | 安全区写法统一 |

### 4.5.4 ▶ 最大改动点

> **会员卡换肤：从 iOS 蓝渐变（`#007aff → #004fad`）改为 Linen 纸感渐变 + 1rpx 金色描边 + 金色奖杯水印**。这是全站唯一一处"品牌完全错位"的组件，修掉它，"我的"页的高端感立刻成立。

---

# 5. 共用组件清单（Phase 3 复用）

命名以 `My` 前缀避免与 uview-plus 冲突。所有组件：单位 rpx、色走 token、图标走 `luc`、无 `<svg>`。

| # | 组件 | 用途 | 状态 | 关键尺寸 |
|---|---|---|---|---|
| 1 | `MyNavBar` | 顶栏，3 变体 | `variant: standard\|serif\|immersive`；`scrolled`（布尔，控制 `--shadow-nav`） | 内容高 88rpx + `.nav-safe`；标题 36/44rpx；插槽 left/right |
| 2 | `MySearchBar` | 搜索入口 / 可输入 | `mode: entry\|input`；`focused` | 高 72rpx（热区 88）；pill；`--color-fill`；图标 32rpx |
| 3 | `MySegmentedTabs` | 3–4 等分 Tab | `value`；`sticky`；`scrolled` | 高 88rpx；标签 28/500→28/600；下划线 48×4rpx |
| 4 | `MySectionHeader` | 区块标题 + 右操作 | 有/无 badge；有/无操作 | 高 48rpx；标题 32/600；右侧 Text 按钮 26/500 `--ink-gold` |
| 5 | `MyCard` | 卡容器 | `variant: hero\|standard\|media\|list`；`shadow: none\|card\|float`；`padding: none\|sm(20)\|md(24)\|lg(32)` | 圆角 24/32rpx；无描边 |
| 6 | `MyIconBadge` | 图标底圆/方块 | `size: 56\|64\|72\|80\|96`；`shape: circle\|square`；`tone: neutral\|gold\|ok\|warn\|err\|rose`；`icon`（lucide 名） | 圆角 pill / 16rpx；图标 = size×0.45 |
| 7 | `MyStatCell` | 数字 + 标签 | `align: center\|left`；有/无图标；有/无角标 | 数字 32/600 tabular；标签 22/500 label；gap 8rpx |
| 8 | `MyChip` | 标签 / 话题 / 分类 | `active`；`tone`；`size: sm(56)\|md(64)`；`closable` | 高 56/64rpx；pill；热区补 88 |
| 9 | `MyBadge` | 角标 / 红点 | `type: dot\|count`；`max: 99` | dot 14rpx；count 高 32rpx，min-w 32，`--color-danger-solid` |
| 10 | `MyButton` | 按钮 | `type: primary\|secondary\|text\|danger`；`size: l\|m\|s`；`disabled`；`loading`；`block` | 96/88/64rpx 高；pill；`--on-primary` 文字 |
| 11 | `MyAvatar` | 头像 | `size: 56\|72\|88\|96\|120`；`ring: none\|plain\|active`；`fallback: letter\|icon` | 圆；环 2rpx；字母兜底 `--color-fill` + `--color-text` |
| 12 | `MyListRow` | 列表行 | `icon`；`value`；`chevron`；`badge`；`arrowless` | 行高 104rpx；内距 0 24；图标 40rpx；文字 28/400 |
| 13 | `MyEmptyState` | 空态 | `icon`；`title`；`desc`；`action`（按钮文案） | 图标 120rpx `--color-text-tertiary`；标题 28/600；按钮 M |
| 14 | `MySkeleton` | 骨架屏 | `variant: block\|text\|circle\|avatar\|card\|product` | 见 §6.3；`--color-fill` + 扫光 |
| 15 | `MySwipeAction` | 左滑操作容器 | `actions[]`；`open`；`threshold: 0.5` | 按钮宽 140rpx；`--color-danger-solid`；圆角跟随组 |
| 16 | `MyFab` | 浮动按钮 | `icon`；`variant: gold\|inverse` | 112rpx；gold = 金底炭图标（**默认**，8.52:1）；inverse = 炭底金图标（两值写死，不随主题） |
| 17 | `MyImageGrid` | 图集 | `images[]`；`max: 1\|3\|9`；`ratio: 1:1`（多图） | gap 8rpx；圆角 16rpx；**单图走 `widthFix` 随原图，不传 `ratio`** |
| 18 | `MyProductCard` | 商品卡（2 列） | `data`；`soldOut` | 卡宽 331rpx；图 1:1；标题 2 行/固定高 78rpx；价格 30/600 `--ink-gold` |
| 19 | `MyPostCard` | 社区帖卡 | `data`；`liked`；`deleting` | 见 §4.3.2 |
| 20 | `MyStatusBar` | 进度/紧迫度条 | `value: 0-1`；`tone`；`height: 4\|6` | 圆角 2rpx；轨道 `--color-fill` |
| 21 | `MyDivider` | 分隔线 | `inset`（左缩进）；`vertical` | 1rpx `--color-divider` |
| 22 | `MySheet` | 弹层容器 | `visible`；`position: bottom\|center` | `--radius-lg` 顶圆角；遮罩 `rgba(26,24,22,.45)`；`--z-sheet` |

**组件约束**：
- 所有组件**不吃 `--color-primary` 作文字色**，只用 `--ink-*`。
- 所有组件的可选区域默认补足 88rpx 热区（用 `padding` 或 `::after` 伪元素扩展，不用负 `margin` 压缩布局）。
- 所有组件必须有深色态，且新增颜色带浅/深两套 token。

---

# 6. 动效与质感

## 6.1 过渡时长与缓动

| 场景 | 时长 | 缓动 | 说明 |
|---|---|---|---|
| 按压反馈（缩放/底色） | 120ms | `--ease-standard` | 超过 150ms 就"粘手" |
| Tab 下划线位移 / 颜色切换 | 200ms | `--ease-standard` | 位移用 `transform: translateX`，不用 `left` |
| 顶栏滚动渐显（底+线） | 200ms | `--ease-standard` | 与滚动位置绑定，不用 JS 逐帧 |
| 弹层进出 | 280ms | `--ease-emphasized` | 进：`opacity 0→1` + `translateY(24rpx→0)` |
| 左滑删除回弹 | 200ms | `--ease-emphasized` | 跟手时 `transition: none` |
| 骨架屏扫光 | 1200ms | `linear` | 循环 |
| 场景粒子脉冲 | 2400ms | `ease-in-out` | 保留现状（已合适） |

**禁止**：`transition: all`（会把 `height/width` 也带上，造成抖动）；`linear` 用于位移动画；超过 400ms 的进场动画。

## 6.2 按压反馈（统一三档）

| 元素 | 反馈 | 实现 |
|---|---|---|
| 卡片 / 大区块 | `transform: scale(.985)` + `opacity .98` | 120ms；`transform-origin: center` |
| 图标按钮 / FAB | `scale(.96)` + 图标 `scale(.92)` | 120ms |
| 列表行 | 底色 → `--color-fill`（深色 `--color-fill-hi`） | 120ms，无缩放（行会抖） |
| 文字按钮 / chip | 底色 → `--color-primary-soft`（pill） + 文字 `--ink-gold` | 120ms |

必须在 H5 与 APP 一致：统一用 `:active` 伪类，不依赖 `@touchstart` 手写（`messages.vue` 已用 `:active` 是对的）。

## 6.3 骨架屏规则

| 类型 | 尺寸 | 用法 |
|---|---|---|
| `skeleton-card` | 高 = 目标卡高（**不得估数**），圆角 24rpx，`--color-fill` | 订单卡、会员卡 |
| `skeleton-product` | 图块 `padding-top:100%`（=331rpx）+ **4 个**文字块（78 / 39 / 36 / 24rpx 高）+ 3 个 12rpx 间距 + 上下 20rpx 内距 → 卡高 **584rpx**。构成式：`331 + 20 + 78 + 12 + 39 + 12 + 36 + 12 + 24 + 20 = 584`。**四个文字块缺一不可**：只画 3 块（78/39/36）会得 **548**，矮 36rpx（= 评分行 24 + 间距 12）。**照抄 §4.2.2.2 的 10 行清单，不要自己列**。标题 78 槽位内可切两条 33rpx 骨块 + 12rpx 间距（`33+12+33=78`），**几何不变、不算多一个块** | 商品卡（2 列 × 3 行 = 6 个） |
| `skeleton-post` | 头行 **112** + 图区 **207** + 行间 **16** + 正文 **174** + 行间 **16** + 互动 **88** → 卡高**固定 613rpx**（= 3 图形态，众数 + 中位高）。**六项缺一不可**：漏掉两个 16rpx 行间会算出 581，比目标矮 32rpx。**正文骨架必须画 4 行**（174 = 28 × 1.55 × 4），画 2 行会得到 526，同样是矮一截。**不按张数逐张对高** —— 骨架渲染时数据未到，无法预知张数；A/B 两类切换约束见 §4.3.2.1 铁律 3 | 社区帖卡 |
| `skeleton-row` | 高 **104rpx**：圆 88 + 2 行（60%/40% 宽） | 会话列表、功能列表 |
| `skeleton-text` | 高 = 行高，圆角 8rpx；宽度按 100%/80%/60% 三档 | 正文 |
| `skeleton-circle` | 圆 72/88/120rpx | 头像 |

> **写法铁律（v1.10 立三条，v1.13 加前置检查）** —— 都为了**让读者能自证**：
>
> **先算，再对。对不上时改式子，不改标注。**（`ux-ui-designer` 提炼。已知答案只能用来**发现**错，不能用来**推导**过程——用答案凑出来的式子，每一项都可能是假的。两次事故的第一反应都是"数应该没错，是写法问题"，然后去调标注；**标注是结论，结论不能迁就算式**，迁就一次，后面每一项都得迁就。）
>
> **▸ 第 1 条的前置检查（主理人提，不放平行条目，因为它不是新规则而是第 1 条的执行顺序）**：
> **构成式的加数必须来自真实组件的完整子元素清单；先列清单，再求和。** 少一个子元素 = 少一个加数，与"省掉一个 16rpx"**同罪**。清单的每一项还要写出高度来源（`font-size × line-height` / 图标尺寸 / padding / margin）—— 只写数值不算完成。
> 商品卡 548 就是这个检查漏掉的：清单看着是齐的（图 + 三行文字），但**第三行之后还有评分行**，且三个文字块的高度来源从没写过 —— 于是 548 / 583 / 591 / 593 / 600 五个数都"算得出来"。**加数省没省是事后能查的，子元素列没列全是事前就该查的 —— 前者靠把式子写全防住，后者只能靠逐项对照真实 DOM。**
>
> 1. **构成式不许省略任何加数**。可以写 `174 + 16×2`，不许把 `16` 直接抹掉。只给"613rpx"、或给一个加不出 613 的算式，读者就只能照抄或自创，两种都会做错。
> 2. **差额式不许漏被减项**（`ux-ui-designer` 补）。凡是"总量减掉 A+B+C 正好等于 D"的句子，**四项必须真的对上**；两边用的若不是同一套加法，差额就没有意义。实测对比请用"逐块两列并排"，不要用"总量相减去猜差在哪"。
> 3. **凡提出"你应该怎么验"，必须附一条自己已经跑过的验证路径**（渲染值 / 独立重算 / 反向代入），不能只说"我想过是对的"。**建议本身有出处，它才不是新的不可信来源**——本轮两次翻车都发生在纸面算术上，两次能定案都是因为回到了真实值。
>
> 病根是同一个：**规范给的是答案，不是推导**。本轮已因此出过五次事故——骨架 613 写成四项（581）、单图 4:3 与 `widthFix` 冲突、`grid-auto-rows` 漏 gutter、415 的差额式把 327 当成 415、商品卡 548 漏掉评分行。前四次是**算式层面**（省加数 / 漏被减项），第五次是**清单层面**（子元素本身就没列全）——两类对应上面「前置检查」与第 1、2 条。

> **铁律：骨架块高度必须复用真实卡高的同一套推导，禁止手估。** 否则「一键切 6 态」时页面会整体跳动，原型即失信。
> **唯一例外**：`skeleton-post` 因图片张数在骨架时刻不可知，固定 613rpx（3 图形态），并受 §4.3.2.1 铁律 3 的 B 类约束（淡入不做高度补间、不重置滚动、首卡顶边不跳、骨架只渲染 2–3 张）。这是"可实现的零位移"与"理想零位移"之间唯一诚实的折中。

> **等高要求的适用范围（主理人 v1.8 裁决）**：骨架等高**只约束多图宫格与商品卡**（2–9 图的 cell 是确定的 1:1，骨架可以对高）。**单图卡不参与骨架等高要求** —— 它走 `widthFix`，高度随原图比例浮动，用任何固定值去对高都会在数据到达后露出假缝。因此 §6.3 与 §3.4 里"切态零位移"的表述，对单图卡一律按 B 类（允许高度变化）执行。

**质感规则**：
- 底色 `--color-fill`（浅 `#F1EDE7` / 深 `#35312D`），扫光高光 = 底色 + 白 4%（深色 = 白 3%）。
- 扫光 1200ms，`background-size: 200% 100%`。
- **首屏骨架不超过 3 个**骨架块组，其余用空白，避免"满屏灰条"。
- 骨架 → 内容的切换用 `opacity` 淡出 200ms，不做 crossfade 位移。
- 尊重 `prefers-reduced-motion: reduce`：关闭扫光，只保留静态色块。

## 6.4 图片占位规则

| 阶段 | 视觉 |
|---|---|
| 加载中 | 底 `--color-surface-sunken` + 居中 `luc-image`（32rpx）`--color-text-tertiary`，**无扫光**（信息流里扫光会造成持续闪烁） |
| 加载失败 | 同上，但图标换 `luc-image` 且加 22rpx 文字"加载失败"（可选） |
| 空图（无图帖） | 不占位、不显示占位条，直接不渲染图片区域 |

**明确禁止**：`data:image/svg+xml` 占位图（现有 `community.vue` 两处）——APP 端 `uni-image` 可能不渲染，且与"禁 SVG"约束冲突。

## 6.5 图片与容器比例（避免布局抖动）

| 场景 | 比例 | 实现 |
|---|---|---|
| Banner | 16:9 | `padding-top: 56.25%` + `aspectFill` |
| 商品图 | 1:1 | `padding-top: 100%` + `aspectFill` |
| 帖子单图 | **随原图（≈4:3）** | `mode="widthFix"` + `height: auto`，**不设 `padding-top`**；卡高浮动属预期 |
| 九宫格 cell | 1:1 | `grid-auto-rows: calc((100vw - 64rpx - 16rpx) / 3)` |
| 头像 | 1:1 | 固定宽高 + `aspectFill` |
| 场景图 | 视口 | `height: max(50vh, 720rpx)` |

## 6.6 质感细节（决定"贵不贵"）

1. **双阴影替代单阴影**：`0 1rpx 2rpx`（贴边锐）+ `0 2rpx 8rpx`（柔和扩散），比单一 `0 4rpx 24rpx` 更像"纸片浮起"。
2. **1rpx hairline 优于 2rpx 描边**：在 750rpx 宽下，1rpx ≈ 0.5px，是真机上的"印刷级"分割。
3. **金色描边宽度 1rpx（结构）～2rpx（强调）**，且只在深色/浅色都能成立时才用。
4. **数字统一 `tabular-nums`**：积分、收藏数、价格、未读数并排时不跳动。
5. **渐变的唯一用途**：场景遮罩、banner 文字遮罩、会员卡纸感底。**禁止**按钮渐变、图标底渐变。
6. **玻璃（glass）只用于沉浸页浮层**（Pet 场景），且底色必须是炭色半透明（白底半透明在浅色场景图上会失形）。

---

# 7. 附录：落地顺序与已知妥协

## 7.1 建议落地顺序（风险从低到高）

| 阶段 | 内容 | 风险 |
|---|---|---|
| 1 | 把 §2.1.6 的 token 增量块写入 `variables.scss`（只新增，不改旧值） | 零 |
| 2 | 全局替换**文字色**：`--color-primary` → `--ink-gold`（6 处）；`--color-text-tertiary` → `--color-text-label`（承载信息处）；`#ff4d4f` → `--color-danger-solid` | 低（纯颜色） |
| 3 | 改 `--color-fill` / `--color-fill-hi` 为暖灰值 —— **仅本次五 Tab 页生效**；其它页面保持冷灰，作为后续独立回归任务（见 §7.2 第 4 条） | 低（范围已收窄） |
| 4 | 组件层：`MyNavBar` / `MyButton` / `MyListRow` / `MyBadge` / `MyChip` 先行 | 中 |
| 5 | 逐页 layout：Pet → User → Messages → Home → Community（信息流风险最高，最后动） | 高 |
| 6 | 深色回归：5 页 × 3 态（系统浅/系统深/强制深） | 必做 |

## 7.2 已知妥协（不要在这些点上反复讨论）

1. **原生 tabBar 选中色固定 `#DBC98A`**，而页面内选中态必须用 `--ink-gold`（可读性）。**两者颜色不一致是刻意的**，不要试图统一——统一只能靠降低可读性，不接受。
2. **不能用悬浮/异形 tabBar**：uni-app 原生 tabBar 不支持。因此 FAB 与底部留白必须避开 tabBar：`bottom = calc(var(--tabbar-offset) + 32rpx)`，其中 `--tabbar-offset = calc(50px + var(--sab))`。**不要用 rpx 表达 tabBar 高**（430pt 下 100rpx = 57.3px，必错）。**实测佐证（v1.9 入库代码）**：`community.vue:1026` 写的 `bottom: calc(98rpx + env(...))` —— 375pt 下 98rpx = **49px**，比 50px 的 tabBar 矮 1px，FAB **压进 tabBar**；390pt 下 = **50.96px**，余量仅 **0.96px**、刚擦着过；430pt 下 = **56.2px**，反而**悬空 6.2px**。**误差符号在 375↔390 之间翻转，量级随屏宽单调放大** —— 同一行代码三档三种结果，这就是本条必须存在的理由。H5 与 APP 的 fixed 基准不同，需条件编译，真机复核后再微调，不要在设计阶段追求"贴边悬浮"。
3. **衬线字体：已裁决「不内嵌」**（主理人裁定，终）。原型与首版实现一律用**系统衬线近似拉丁大字**，中文标题走 `--font-ui` 600 + `-0.02em` 负字距。理由：Playfair 中文 fallback 到宋体是"廉价高端感"陷阱；内嵌 latin 子集 ttf 需额外构建步骤与字重文件，收益低于成本。§2.2 保留两条路径的说明仅作背景，**执行按本条**。
4. **`--color-fill` 暖化：本次仅五 Tab 页生效**（主理人裁定）。`#F2F2F7 → #F1EDE7` 采用新值，但**全站替换列为后续独立回归任务，不并入本次**。因此交付验收时，若发现其它页面（非 5 Tab）的 chip/图标底仍是冷灰，**属预期，不是缺陷**。
5. **`--color-text-tertiary` 保留但不承载信息**：改它的值会波及全站几十处，本次只做"用法收敛"而非"取值变更"。若后续要统一，需单独立项。
6. **emoji 与 lucide 图标不共存**：`user.vue` 订单宫格的 emoji 必须换 lucide；但用户自定义内容（帖子正文里的 emoji）不在此约束内。
7. **`--sab` 为 token 表正式成员；`--tabbar-offset` 与 `calc(var(--sab) + 50px + 32rpx)` 等价，同一文件内不得混用，跨文件以 `--tabbar-offset` 为准。**（v1.9 主理人裁定，替换原"唯一入口、禁止另写"的硬禁令。）
   精确恒等式，落地时按这个核：`calc(var(--tabbar-offset) + 32rpx)` ≡ `calc(var(--sab) + 50px + 32rpx)`，因为 `--tabbar-offset: calc(50px + var(--sab))`。**32rpx 是视觉间距，两边都必须带**——只写 `var(--tabbar-offset)` 会少 16px，只写 `calc(var(--sab) + 50px)` 同样少 16px，都是"看起来生效、实际贴边"。
8. **钱包第 4 格（收藏）与点赞实心态定色**（主理人裁定，F1/F2 附则，v1.8 落地）：
   - ① 钱包第 4 格「收藏」的强调底用 `--color-primary-soft`（浅 `rgba(219,201,138,.18)` / 深 `rgba(201,180,122,.20)`，见 §2.1.3），**不新增 token**；
   - ② 点赞**已赞实心**用 `--color-danger-solid` `#B4574A`（白字 4.77:1），**不用 `--color-primary`**（作文字/图标色仅 1.48:1）；
   - ③ 两者深色侧一律取 §2.1.2 / §2.1.3 表内已配对的值，**不得为"更金"就地调亮**；
   - ④ 验收时若有人以"不够品牌"为由要把已赞态改回金色，**驳回**——那是拿可读性换调性；
   - ⑤ 两处均以 §5 的 `MyPostCard` / 钱包组件规格为准，**不在页面内另写色值**。

## 7.3 交付边界

本文档**只定义视觉规范，不含任何业务源码改动**。所有 SCSS 片段为"建议形态"，落地时需由前端按实际文件结构合并（`variables.scss` 用追加方式，避免覆盖历史别名）。

## 7.4 变更记录

> **这张表的价值不在于"我改对了什么"，而在于"下一个人能看出这套判断在过去哪里失手过"。** 因此：① **撤回与自查出的错一律照记，包括当天刚犯的**——只记旧错的账本会让人误以为当前版本已经收敛；② 每条都要写明**是谁发现的**，不写"经核查"，写"由 `ux-ui-designer` 复核发现"。

| 版本 | 变更 | 触发 |
|---|---|---|
| v1.0 | 初版 | — |
| **v1.1** | ① `--tabbar-h` 由 `100rpx` 改为 `50px`，新增 `--tabbar-offset: calc(50px + env(safe-area-inset-bottom,0))`；② 新增 §2.4「rpx / px 分工」唯一规则与底部偏移唯一写法；③ FAB / 底部留白 / 妥协清单全部改为走 `--tabbar-offset`；④ 修正 FAB 图标色（`--ink-gold` → `--on-primary`，原写法在炭底上不可见）；⑤ Pet 玻璃浮层改为 `rgba(26,24,22,.62)` 并给出最坏情况实测值 | 与 `02-ux-spec.md` 几何对齐评审：rpx 在 430pt 下换算为 57.3px ≠ 原生 tabBar 50px；FAB 图标色自查 |
| **v1.2** | 新增 §4.3.2.1「帖子卡几何（定值）」：卡宽 686 / 媒体区 638 / 单图 478 / 2 列 315 / 3 列 207 / 三档九宫格总高 207·422·637 / 头行 112 / 正文 174 / 互动 88 / 参考卡高 613·884，并规定 cell 必须用 `calc()` 推导；§6.3 骨架屏改为引用该表 | `02-ux-spec.md` §3.4 帖子卡图片格仍留变量，Phase 3 做骨架会二次猜数 |
| **v1.3** | ① **修正 v1.2 自身矛盾**：原文 §4.3.2 写"2–3 张 2 列"、§4.3.2.1 却按"3 张 3 列"给值，两处口径不一。现统一为五档 —— 1 图 4:3 / 2 图 2 列 1 行 / **3 图 3 列 1 行** / **4 图 2 列 2 行（不做 3+1）** / 5–9 图 3 列；② 新增按张数的**卡总高全表**（884 / 721 / 613 / 1044 / 828 / 1043）；③ 新增告警：`community.vue:565` `getImageGridClass()` 现状 3 张走 2 列、4 张走 3 列，与本规范差 431 / 216rpx | `ux-ui-designer` 复核 §3.4 时发现 3 图渲染高度与规范差 431rpx；追查后确认根因是本文 §4.3.2 与 §4.3.2.1 内部矛盾，非代码问题 |
| **v1.4** | ① **撤回 `col-2x2`**：4 张放 2 列会自动流成 2 行，行数属内容属性而列数属样式属性，映射收敛为三支（`single` / `col-2`(2 与 4 张) / `col-3`），**不新增任何 CSS 类**，同时消除 `::v-deep` 覆盖漏写的风险；② **撤回"骨架按张数查表"**：骨架渲染时数据未到、无法预知张数，改为**固定 613rpx（3 图形态）**，并引入 A/B 两类切换约束（A 类严格零位移；B 类允许高度变化但禁止高度补间/滚动重置/首卡跳动）；③ §6.3 铁律补"唯一例外"条款 | `ux-ui-designer` 指出"按张数查表"按字面不可实现（数据未到）；自查发现 `col-2x2` 与 `col-2` 规则完全相同，属多余抽象 |
| **v1.5** | ① 新增「改前 / 改后对照表」（2 图不变 / 3 图 −431 / 4 图 **+216 变疏** / 其余不变），并明确"整体变密"是错的说法；② 新增「密度调节杠杆顺序」：正文行数 → 头行内距 → 图区间距 → 卡间距，**止步于第 ④ 项，cell 永远 1:1 不在可调项内** | `ux-ui-designer` 更正其密度口径时发现"只有 3 图变密、4 图反而变疏"，需沉淀为可复用规则，避免日后为密度需求破坏网格 |
| **v1.6** | 记录主理人两项裁决并消除正文矛盾：① 衬线**不内嵌**（§2.2 与 §7.2 第 3 条同步为终稿，拉丁用系统衬线近似 + 中文走 ui 600）；② `--color-fill` 暖化**仅五 Tab 页生效**，全站替换后置为独立回归任务（§7.1 阶段 3 与 §7.2 第 4 条同步）；③ 新增 §7.2 第 7 条：`--tabbar-offset` 为底部偏移唯一入口，禁止业务侧另写 `calc(env(...) + 50px + 32rpx)` | 主理人裁决 |
| **v1.7** | ① **修正自身错误**：§4.5.1 骨架图钱包首格此前画成「余额 ¥0」，与现状代码（`user.vue` 首格为浏览历史数）及主理人 F1 裁决均不符，改为「浏览历史 12」；② §4.5.2 钱包条补齐四格定义（浏览历史 / 积分 / 优惠券 / 收藏，**一律计数、不引入余额**）与 tabular-nums 占位要求；③ 新增「浏览历史 / 浏览足迹」命名与图标统一口径（文案统一「浏览历史」+ `luc-history`，`luc-footprints` 留给品牌叙事），标待主理人确认 | `ux-ui-designer` 指出视觉稿可能与 F1 裁决不一致；核对后确认是我草案画错，非裁决变更 |

| **v1.8** | 按主理人三项裁决定稿：① **单图保留 `widthFix`、不回退固定 4:3** —— §4.3.2 图片行、§4.3.2.1 定值表与改前改后表、映射注释、§4.3.3 第 6 条（**该条已整体撤回**）、§5 `MyImageGrid`、§6.5 比例表共 **6 处**同步为「≈884rpx，随原图浮动」；② 新增「骨架等高的适用范围」：**只约束多图宫格与商品卡，单图卡不参与**（§4.3.2.1 表注 + §6.3 末尾）；③ **3 图定为 A（走 3 列）**，规则合并表述为「2 或 4 图用 2 列，其余多图用 3 列」；④ §7.2 新增第 8 条（钱包第 4 格 `--color-primary-soft` / 已赞态 `--color-danger-solid`，5 项明细） | 主理人裁决；第 6 条的撤回是承认 `community.vue:947-954` 那次改动修的是真实缺陷（宽屏裁成扁条），规范不该用定值表去推翻一个已修好的 bug |

| **v1.9** | ① **`--sab` 正式入表**：新增 `--sab: env(safe-area-inset-bottom, 0px)`，并把 `--tabbar-offset` 改为基于它定义（`calc(50px + var(--sab))`），两种写法从此可证明等价 —— 这是本项目第 4 个"没定义却到处 `var()`"的坑，此前仅因原型恰好自己有值才没暴露；② **§7.2 第 7 条按主理人原文放宽**：由"唯一入口、禁止另写 `calc(env(...) + 50px + 32rpx)`"改为"两者等价、同一文件内不得混用、跨文件以 `--tabbar-offset` 为准"，并补精确恒等式与"32rpx 两边都要带"的踩坑说明；③ §7.2 第 2 条补入库代码实测：`bottom: 98rpx` 在 375pt 压进 tabBar 1px、430pt 悬空 6.2px，**两端错得方向相反**；④ **修正 §6.3 `skeleton-post` 的算术缺陷**：原文只写四项 `112 + 207 + 174 + 88 → 613`，实为 **581**，漏了两个 16rpx 行间 —— 这是**简写导致的"算式与结论对不上"**（§4.3.2.1 主公式一直是六项），已补成 `112 + 207 + 16 + 174 + 16 + 88 = 613` 并注明"六项缺一不可，漏两个 16 会矮 32rpx；正文骨架必须画 4 行，画 2 行只有 526"。**不另开勘误，正文修正 + 本记录即为留痕** | 主理人裁定（`--sab` 选 A 方案 + 承认 B 等价）；`frontend-engineer` 即将落地的 FAB 写法需要一份不自相矛盾的依据；④ 由 `ux-ui-designer` 复核发现，主理人确认保留（理由：错值躺在"可直接抄"的位置，比多改一版危险）；⑤ **写法铁律扩为两条**（构成式不许省加数 / 差额式不许漏被减项）——导火索是第四起同族事故：我用差额式解释原型骨架 415rpx 时，写成 `613 − (88+32+40+126)`，实际得 **327** 却标成 415（漏了骨架的"行间多 8、卡内距多 48"两个加项）。二次纠错由 `ux-ui-designer` 完成 |

| **v1.10** | 只补写作纪律与实证数据，不动任何几何：① §6.3 写法铁律补第 **3** 条——凡提出"你应该怎么验"，**必须附一条自己已跑过的验证路径**（渲染值 / 独立重算 / 反向代入），"我想过是对的"不算；② 同处新增前置句 **"先算，再对。对不上时改式子，不改标注。"**；③ §7.4 表头新增**记法原则**：撤回与自查出的错一律照记（含当天刚犯的），每条写明是谁发现的，不写"经核查"；④ §7.2 第 2 条与 `03` `G-04` 补第三档实测（390pt = 50.96px，余量 0.96px），把"两端错得相反"升级为"三档三种结果、误差符号在 375↔390 翻转" | `ux-ui-designer` 指出本轮两次翻车都发生在**纸面算术**、两次定案都靠回到渲染值；并提出"只记旧错的账本会让人误以为当前版本已收敛"；第 ④ 项的两档验算与 390 档补测由她代为跑完（`98×375/750=49`、`98×390/750=50.96`、`98×430/750=56.19`） |

| **v1.11** | 两处**读者会照抄的值**修正 + 两条实现陷阱（①②④ 由 `frontend-engineer` 原型实测提出，③ 为自查）：① **Banner 指示点整条重写**（新增 §4.2.2.1）—— 原「`--color-primary` 激活 + `rgba(46,43,41,.3)` 非激活」实测 1.42–1.83:1，全线不达标；改为「浅色底板 + 极端档激活色」，四组实测（亮/暗两极图 × 双皮肤）非激活 3.42–4.76:1、激活 11.05–17.53:1、**激活 vs 非激活 3.07–3.68:1**，全项 ≥3:1。同时写入结构性结论：**金色不能当激活点**（其亮度恰好等于"刚好 3:1 的非激活色"亮度，实测撞车 1.43 / 1.51:1）；② **卡高表补「正文 4 行」前提**并新增「正文行数 → 卡高」小表（3 行 570 / 2 行 526 / 1 行 483 / 纯文本 319），行高 `28 × 1.55 = 43.4rpx` 显式给出；③ **更正 7–9 图卡高 1043 → 1044**：满格宫格（2×2 与 3×3）都是 638×638 正方形，卡高恒 1044、与列数无关 —— 这个 1043 从 v1.3 起在表内躺了 8 个版本，属"用答案凑式子"同族（没人回头验过 3 列 3 行的图区）；连映射注释里的 `637` 也一并同步为 `638`，并补**取整口径**（±1rpx 属正常，612 / 829 都不是缺陷）；④ 新增正文截断两条陷阱（不加 `white-space: pre-wrap`；底部留白用 `margin` 不用 `padding`） | `frontend-engineer` 原型实测：指示点在四种底上 1.42–1.83:1、10rpx 级图形需 3:1；卡高表缺 4 行前提会让回归验收把 43.4rpx/行的差当 bug。③ 属**自查** —— 重算 `207.33 × 3 + 8 × 2 = 638` 时发现 3 列 3 行与 2 列 2 行图区同值，原表 1043 站不住 |

| **v1.12** | 商品卡定值 **548 → 584**（主理人裁决"改数不改卡"：保留标题 2 行 + 描述 + 价格 + 评分四行）。新增 §4.2.2.2「商品卡高度构成」，把 10 行子元素逐项写出来源并求和 = 584：`331 + 20 + 78 + 12 + 39 + 12 + 36 + 12 + 24 + 20`。① 548 的病是**同时**漏了评分行、且三个文字块没写高度来源 —— 所以这次不写 583（实测值）也不写 593，而是先定行盒口径再求和，得出的 584 与实测 583 差 1rpx，入 §4.3.2.1 容差；② 显式定下两处行高 override（价格行 `1.2`、评分行 `24rpx`），这两处不写，同一个组件能算出 548 / 583 / 591 / 593 四个值；③ §4.2.2 商品卡行与 §6.3 `skeleton-product` 同步（骨架从「3 个文字块 548」改为「4 个文字块 584」，差额 36 = 评分行 24 + 间距 12）；④ §6.3 写法铁律新增**第 4 条**：定值必须对应完整子元素清单且每项写出高度来源；「病根」段落从四次扩为五次，并区分**算式层面**（省加数 / 漏被减项）与**清单层面**（加数没列全） | 主理人转来 `frontend-engineer` 实测：真实商品卡 **583**，规范 548 差 35rpx；主理人判定这是本轮第 5 次「规范漏一项、代码是对的」，并提出第 4 条铁律。我按第 1 条铁律「先算再对、改式子不改标注」复核后，**主理人给的分解式实际得 567（他把字号当行高、且最后一行还带了间距）**，故未直接采用，改由完整清单求和得 584 |

| **v1.13** | 三处收尾，全部是主理人与 `frontend-engineer` 落地反馈：① **「第 4 条铁律」改写成第 1 条的「前置检查」**（主理人裁定）—— 原文照办会变成三条变四条、每条都要重新解释相互关系；改为「构成式的加数必须来自真实组件的完整子元素清单；先列清单，再求和。少一个子元素 = 少一个加数，与省掉一个 16rpx 同罪」，并保留"每项写出高度来源"这半句（商品卡 548 的病是**清单没列全 + 来源没写**两个叠加）。铁律条目数维持 3 条不变，病根段五次事故的归因同步改为「前置检查 / 第 1 条 / 第 2 条」三档；② **修正 §4.2.2.1 位置行的歧义**：原文只写 `right: 132rpx`，读起来像点组偏移，实为**标题容器 `.banner__cap` 的右侧预留**；改为点组 `right: 26rpx` / 标题容器 `right: 132rpx` 两个数分属两个元素，并记录 36rpx 空隙的实测关系；③ **584 / 583 定为双轨口径**：规范给构成式 584（能自证），实现实测 583，差 1rpx 入容差；**验收判据改为「骨架高 == 真实卡高」而不是「== 584」** —— 主理人明确"为了 0.5px 调两处行高再引发新偏移不划算" | 主理人裁定（第 4 条铁律的落法 + 584 定稿）；`frontend-engineer` 落地实测指出 `right: 132rpx` 会把点组推进标题区、并给出 `overlap: false` 的三组实测值。② 属我的表述缺陷，不是他的误读 —— 一行里塞了两个元素的边距，任何人都会读错 |

| **v1.14** | 更正一处**已不存在于实现的记录值**：§4.2.2.2 原写「实现实测 583」，实为 **583.9（≈584）**。`frontend-engineer` 按 10 项清单落地并写死两处 `line-height` 后实测 583.9、骨架 584、两态首卡顶边同为 513.7px（差 **0.1rpx**）；那个 583 是**行高写死之前**的测量值（价格默认 1.35 → 40.4、评分 28.8、间隙 26 含一个 `margin-top:2` 历史补丁），已不可复现，留在文档里会被当成"另一套基准"。现明确写「**「583」已不存在，不要再引用**」，并把"差 0.2rpx 来自 12rpx 间隙的亚像素舍入"一并记入；另补一条骨架实现许可：**标题 78 槽位内可切两条 33rpx 骨块 + 12rpx 间距**（`33+12+33=78`），几何不变、不算多一个块 —— 写进去是为了防止后来人把它"修正"回单条大块 | `frontend-engineer` 主动更正：他上一轮报的 583 自己在同日已推翻，并给出逐项带来源的实测表（10 项全中，文字区 252.8）。这类"把已消失的值从记录里摘掉"的动作，比报新值更有价值 —— 记录里的旧值会被下一个接手的人当成验收基准 |

> 与 `02-ux-spec.md` 的关系：02 负责流程/状态/手势/验收，本文负责几何与质感。**任何一方改动 token 或尺寸，必须同步另一方并更新本节**。

> **v1.8 联动项已闭环**：`02-ux-spec.md` §3.4 的「1 图 = 884rpx」「单图 4:3 = 638×478」已由 `ux-ui-designer` 在 02 v1.8 同步为「≈884rpx（4:3 参考值，随原图浮动）」+ 单 cell `638 × auto`，并在 §3.4.1 B 类补「单图不参与等高」、§6.6 加「单图仍为 `widthFix`」打勾项。

---

*MOYUYO ATELIER · 视觉语言规范 v1.14 · 五 Tab 页*
