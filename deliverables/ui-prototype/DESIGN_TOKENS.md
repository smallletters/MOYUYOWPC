# MOYUYO App · 设计令牌文档 / BRAND SPEC（Phase 2）

> 产出：设计系统专家「彩格调」 · 适用：iOS 移动 App 五屏 UI（393×852，1pt=1px）
> 唯一视觉事实源：`VISUAL_DIRECTION.md`（§2.2/§2.3/§6.1/§7.1）与 `index.html` 前 130 行变量块 —— 本文 token 精确复制自该源，未擅自改色。
> 辅助事实源：`DESIGN_BRIEF.md`（功能实证）、`UX_SPEC.md`（交互验收）。
> 用途：下游原型构建师（Phase 3）直接用本文第 8 节 CSS 变量块落地；审查官（Phase 4）用第 7 节 Anti-Slop 清单对照。
> 品牌主张：**与爱同行 · 精致养宠日常**。

---

## 1. 设计系统结论

**结论：沿用 MOYUYO 自有设计体系（iOS 17/18 HIG 转译的「暖砂哑光」体系），本次为品牌视觉升级的精修与补充，不引入任何外部设计系统、不推倒重来。**

本次升级在完整保留品牌内核（米白暖灰底 `#F6F2EE` / 沙金 `#DBC98A` / 深棕文字 `#2E2B29` / 三级文字灰阶 / iOS 毛玻璃 / 圆角卡片 / 柔和阴影 / 五 Tab 结构 / 产品线四色）的前提下，做五件事：

1. **更克制的留白**：以 **24px 组距**为基准建立 section 呼吸，层级靠留白与发丝线，不靠阴影堆叠。
2. **有呼吸感的排版**：严格锁定 iOS 十档字号阶梯 `34/28/22/20/17/16/15/13/12/11`，字重只允许 `400/600/700`，价格/计数/时间一律 `tabular-nums`。
3. **精炼的金色点缀**：确立「**沙金做面、古铜墨做字**」双层强调色规则 —— `#DBC98A` 只做填充面（CTA/VIP/星/指示条），文字与 ≤15px 小图标统一用 `#82683A`（古铜墨）。
4. **通透的层次秩序**：毛玻璃 nav/tabBar + 0.5px 发丝线 + 暖底白卡，阴影 `alpha ≤ .14` 且禁彩色投影。
5. **实拍宠物图为绝对主角**：UI 退后，卡片/图标/文字均为中性哑光容器，不抢图。

**冲突项处理（遵循主理人最新拍板）**：`UX_SPEC.md` §0 曾建议 tabBar 重排并把「宠物」降为占位；本次**不采用**——保留原型现状五页完整结构，**宠物 Hub 为默认完整页**。本文所有组件 token 与该决策无关，均可直接沿用。若后续 IA 变动，仅需改默认选中项与顺序，token 不变。

**相对 VISUAL_DIRECTION 的精修点（共 6 处，均为补充/收紧，非改色）**：① TabBar 标签由 10px 归入十档阶梯的 **11px（Caption 2）**，满足「严格十档」硬约束；② 强化并文档化「沙金做面、古铜做字」双层规则；③ 24px 组距基准显式化；④ 阴影 `alpha≤.14` 禁彩色投影显式化；⑤ 补充「主金 / 次级 / 文字」三档按钮体系；⑥ 头像尺寸阶梯化。

---

## 2. 完整浅色 Token 表（原型默认）

### 2.1 背景分层与分隔（iOS systemGrouped 暖化）

| 变量 | 值 | 用途 |
|---|---|---|
| `--bg-grouped` | `#F6F2EE` | 页面底（亚麻暖灰，替代 iOS `#F2F2F7`） |
| `--bg-grouped-2` | `#FFFFFF` | 卡片表面 / inset grouped 行容器 |
| `--bg-grouped-3` | `#FFFFFF` | 三级分组表面 |
| `--bg-elevated` | `#FFFFFF` | 弹层、浮起卡片 |
| `--fill-2` | `#F4F1ED` | 搜索胶囊、chip、图标底、行按下态 |
| `--fill-3` | `#EAE5DD` | 进度轨道、次级按下态 |
| `--fill-4` | `#E2DDD5` | 禁用底 |
| `--overlay-subtle` | `rgba(46,43,41,.04)` | 幽灵表面：返回按钮、搜索框底 |
| `--separator` | `rgba(46,43,41,.14)` | 0.5px 发丝分隔线 |
| `--separator-opaque` | `#EAE5DD` | 需实色时的分隔线 |
| `--separator-strong` | `rgba(46,43,41,.22)` | 分组之间、tab 底边 |

### 2.2 文字（四级灰阶）

| 变量 | 值 | 用途 | 对亚麻底对比度 |
|---|---|---|---|
| `--label` | `#2E2B29` | 正文 / 标题（深炭） | 12.63:1 ✅ |
| `--label-2` | `#6E6962` | 摘要 / 副文案 | 4.88:1 ✅ |
| `--label-3` | `#9A948C` | 时间 / 占位 / 非关键提示 | 2.70:1 ⚠️ 不承载关键信息 |
| `--label-4` | `#B8B2AA` | 禁用 / chevron | 装饰级 |

### 2.3 品牌金（双层强调色 —— 本体系核心）

| 变量 | 值 | 用途 | 判定 |
|---|---|---|---|
| `--gold` | `#DBC98A` | **面**：CTA 填充 / VIP 卡 / 星 / 选中指示条 / 图标底 | 只做填充，禁做文字 |
| `--gold-light` | `#E8DDB5` | 浅金面、渐变起点 | 面 |
| `--gold-dark` | `#B8A66B` | 深金面、渐变过渡 | 面 |
| `--brass` | `#B38A5A` | **线（图形）**：≥24px 图标、tab 选中图标 | 对白 3.13:1 ✅ 仅图形 |
| `--brand-ink` | `#82683A` | **线（文字）**：品牌文字 / ≤15px 图标 / 选中标签 / 文字链接 | 对亚麻 4.72:1 ✅ |
| `--brand-pressed` | `#A8964F` | 主金按钮按下态 | — |
| `--brand-disabled-bg` | `#EDE7D6` | 主按钮禁用底 | — |
| `--brand-disabled-fg` | `#B8B2AA` | 主按钮禁用字 | — |
| `--brand-tint` | `rgba(219,201,138,.18)` | 选中 chip / 图标底 / 会员 pill | — |
| `--brand-tint-strong` | `rgba(219,201,138,.28)` | 强选中态 | — |
| `--on-gold` | `#2E2B29` | 沙金底上的文字 | 对沙金 8.53:1 ✅ |

> 层级澄清：`#DBC98A` 是沙金主色（面），`#B8A66B` 是它的深色档；`#B38A5A`（Antique Brass）是独立的强调色「古铜」，用于≥24px 图形；`#82683A` 是古铜加深得到的「古铜墨」，用于文字/小图标。四者不可混用。

### 2.4 语义色（保留 iOS 色相，降饱和 15–25%）

| 变量 | 值 | 用途 |
|---|---|---|
| `--red` / `--red-fg` / `--red-tint` | `#C96052` / `#A84237` / `rgba(201,96,82,.12)` | 危险语义 / 文字 / 淡底 |
| `--orange` / `--orange-fg` / `--orange-tint` | `#D98B4A` / `#9A5A20` / `rgba(217,139,74,.14)` | 警示 / 文字 / 淡底 |
| `--green` / `--green-fg` / `--green-tint` | `#7E9B83` / `#4F6E57` / `rgba(126,155,131,.14)` | 成功 / 文字 / 淡底 |
| `--blue` / `--blue-fg` / `--blue-tint` | `#7C9BB0` / `#4A6E86` / `rgba(124,155,176,.14)` | 信息 / 客服会话 / 淡底 |
| `--red-solid` | `#B04A3F` | 白字角标 / 左滑删除（白字 5.39:1） |
| `--like` | `#C96052` | 点赞填充 |

### 2.5 产品线四色（金刚区 / 分类图标底）

| 变量 | 主色值 | 图标深色档 | 浅底算法 |
|---|---|---|---|
| `--line-care`（护理金） | `#DBC98A` | `#82683A` | `color-mix(in srgb, var(--line-care) 18%, var(--bg-grouped-2))` |
| `--line-gear`（用具灰绿） | `#ABB9AD` | `#5F7465` | 同上，换 `--line-gear` |
| `--line-play`（玩具棕） | `#B38A5A` | `#8A6238` | 同上，换 `--line-play` |
| `--line-home`（居家粉） | `#D9B4B0` | `#9A6B67` | 同上，换 `--line-home` |

> 每屏最多出现 2 种产品线色，避免调色盘失控。index.html 已提供 `.tone-care/.tone-gear/.tone-play/.tone-home` 类（`--tile-bg`/`--ink`/`--t1`/`--t2`/`--t3`），直接复用。

### 2.6 毛玻璃

| 变量 | 值 | 用途 |
|---|---|---|
| `--nav-veil` | `rgba(246,242,238,.82)` | 大标题折叠后 nav 底 |
| `--tab-veil` | `rgba(250,247,244,.80)` | tabBar 底（比 nav 更白一档） |
| `--sheet-veil` | `rgba(246,242,238,.92)` | 弹层 / 动作面板 |
| `--veil-blur` | `saturate(180%) blur(20px)` | 统一背景模糊（blur 不得 > 24px） |

### 2.7 阴影（iOS 极轻，alpha 上限 .14）

| 变量 | 值 | 用途 |
|---|---|---|
| `--shadow-0` | `none` | 平面列表行 |
| `--shadow-1` | `0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04)` | 卡片 |
| `--shadow-2` | `0 2px 8px rgba(46,43,41,.07)` | sticky nav / 吸顶 tab |
| `--shadow-3` | `0 8px 24px rgba(46,43,41,.12), 0 2px 6px rgba(46,43,41,.05)` | sheet / FAB |
| `--shadow-4` | `0 12px 40px rgba(46,43,41,.14)` | 模态弹层 |

### 2.8 圆角标尺（iOS continuous corner）

| 对象 | 圆角 | 对象 | 圆角 |
|---|---:|---|---:|
| inset grouped 卡片 / 列表行容器 | **14px** | Banner 卡 / 帖子大图容器 | 18px |
| 列表行内图片 / 商品图 | 12px | VIP 会员卡 / sheet 顶部 | **20px** |
| 金刚区图标方块 | 16px | 小图标底（功能列表） | 8px |
| chip / 胶囊搜索框 / 角标 | 9999px | FAB / 圆形头像 | 50% |

### 2.9 间距刻度（8pt 基准）

| token | px | 用途 |
|---|---:|---|
| `space-1` | 4 | 图标与角文字、chip 内微调 |
| `space-2` | 8 | 行内元素、话题 chip 间距 |
| `space-3` | 12 | 卡片内上下 padding、宫格间隙 |
| `space-4` | 16 | **页面边距**、列表行左右 padding |
| `space-5` | 20 | 卡片间垂直间距（compact） |
| `space-6` | 24 | **section 之间 / 卡片之间（常规，基准）** |
| `space-8` | 32 | 大区块呼吸、页脚上间距 |

### 2.10 动效曲线

| 变量 | 值 | 用途 |
|---|---|---|
| `--ease-ios-out` | `cubic-bezier(0.2,0.9,0.25,1)` | 通用 UI 进入/退出 |
| `--ease-ios-sheet` | `cubic-bezier(0.32,0.72,0,1)` | 模态 / 动作面板 |
| `--ease-ios-inout` | `cubic-bezier(0.4,0,0.2,1)` | 指示条 / 进度 |
| `--spring-like` | 原型降级版 `cubic-bezier(0.34,1.56,0.64,1)`；增强版 `linear(0,0 8%,1.28 32%,.92 66%,1 100%)` | 点赞弹簧 |

---

## 3. 完整深色 Token 表（避免夜店感，保持哑光高级）

**原则：暖色相保留、降饱和而非反色；背景为暖棕黑（非纯黑），金色降饱和，文字反相为浅亚麻/浅金。**

### 3.1 背景与分隔

| 变量 | 值 | 变量 | 值 |
|---|---|---|---|
| `--bg-grouped` | `#1A1816` | `--fill-2` | `#35312D` |
| `--bg-grouped-2` | `#2A2724` | `--fill-3` | `#403B36` |
| `--bg-grouped-3` | `#35312D` | `--fill-4` | `#4A453F` |
| `--bg-elevated` | `#34302C` | `--overlay-subtle` | `rgba(255,255,255,.07)` |
| `--separator` | `rgba(255,255,255,.12)` | `--separator-opaque` | `#3A3530` |
| `--separator-strong` | `rgba(255,255,255,.18)` | | |

> 深色下 hairline **必须提亮**到 `rgba(255,255,255,.12)`，否则卡片消失。

### 3.2 文字

| 变量 | 值 | 变量 | 值 |
|---|---|---|---|
| `--label` | `#F6F2EE` | `--label-2` | `#B8B0A5` |
| `--label-3` | `#807A72` | `--label-4` | `#6B655E` |

### 3.3 品牌金（深色反相）

| 变量 | 值 | 用途 |
|---|---|---|
| `--gold` | `#C9B47A` | 面；对深色卡 7.28:1 |
| `--gold-light` | `#D9C68F` | 浅金面 |
| `--gold-dark` | `#A8964F` | 深金面 |
| `--brass` | `#C9A26B` | ≥24px 图形图标 |
| `--brand-ink` | `#E3CD95` | 反相为浅金（文字/小图标/选中标签） |
| `--brand-pressed` | `#A8964F` | 按下态 |
| `--brand-disabled-bg` | `#3A3428` | 禁用底 |
| `--brand-disabled-fg` | `#7A736A` | 禁用字 |
| `--brand-tint` | `rgba(201,180,122,.18)` | 选中 chip / 图标底 |
| `--brand-tint-strong` | `rgba(201,180,122,.28)` | 强选中态 |
| `--on-gold` | `#1A1816` | 沙金底上的文字 |

### 3.4 语义色

| 变量 | 值 | 变量 | 值 |
|---|---|---|---|
| `--red` / `--red-fg` / `--red-tint` | `#E58073` / `#F0A297` / `rgba(229,128,115,.16)` | `--blue` / `--blue-fg` / `--blue-tint` | `#9CC2DA` / `#BCD9EA` / `rgba(156,194,218,.16)` |
| `--orange` / `--orange-fg` / `--orange-tint` | `#E7A867` / `#F0C08C` / `rgba(231,168,103,.16)` | `--red-solid` | `#B8453A`（白字 5.32:1） |
| `--green` / `--green-fg` / `--green-tint` | `#9DBFA4` / `#BBDBC1` / `rgba(157,191,164,.16)` | `--like` | `#E58073` |

### 3.5 毛玻璃 / 产品线 / 阴影

| 变量 | 值 |
|---|---|
| `--nav-veil` / `--tab-veil` / `--sheet-veil` | `rgba(26,24,22,.78)` / `rgba(32,29,27,.80)` / `rgba(42,39,36,.94)` |
| `--line-care` / `--line-gear` / `--line-play` / `--line-home` | `#C9B47A` / `#9FB0A4` / `#C9A26B` / `#D6A9A5` |
| `--shadow-1` | `0 1px 3px rgba(0,0,0,.35), 0 0 0 .5px rgba(255,255,255,.06)` |
| `--shadow-2` | `0 2px 8px rgba(0,0,0,.45)` |
| `--shadow-3` | `0 8px 24px rgba(0,0,0,.55), 0 2px 6px rgba(0,0,0,.35)` |
| `--shadow-4` | `0 12px 40px rgba(0,0,0,.6)` |

> VIP 卡深色渐变：`#A8964F → #C9B47A → #E3CD95`，文字 `#1A1816`；不可做成纯黑底 + 亮金发光。

---

## 4. 排版规范

### 4.1 字体栈（直接复制）

```css
--font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
             "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei",
             "Helvetica Neue", Arial, sans-serif;
--font-num:  "SF Pro Display", -apple-system, "PingFang SC", sans-serif;
body { font-family: var(--font-sans); -webkit-font-smoothing: antialiased; }
.price, .count, .points, .time { font-family: var(--font-num); font-variant-numeric: tabular-nums; }
```

### 4.2 iOS 字号阶梯（393pt 视口，1pt=1px，严格十档）

| 样式 | 字号 | 字重 | 行高 | 字距 | 典型用途 |
|---|---:|---:|---:|---:|---|
| Large Title | 34 | 700 | 41 | -0.4 | 社区/消息大标题 |
| Title 1 | 28 | 700 | 34 | -0.35 | VIP Points、钱包数字 |
| Title 2 | 22 | 700 | 28 | -0.3 | 我的昵称、Banner 标题 |
| Title 3 | 20 | 600 | 25 | -0.25 | 区块大标题「我的订单」 |
| Headline | 17 | 600 | 22 | -0.2 | 卡片主标题、section 标题 |
| Body | 17 | 400 | 22 | -0.15 | 帖子正文、会话摘要 |
| Callout | 16 | 400 | 21 | -0.15 | 商品名、功能行 |
| Subhead | 15 | 400 | 20 | -0.1 | tab 文字、搜索占位 |
| Footnote | 13 | 400 | 18 | 0 | 时间戳、描述、标签 |
| Caption 1 | 12 | 400 | 16 | 0.1 | 角标、辅助说明、wallet 标签 |
| Caption 2 | 11 | 400 | 13 | 0.1 | **tabBar 标签**、最小提示 |

> 商品卡名称 14/600 为 393 视口特例（源于旧 rpx 迁移），允许作为 Callout 的紧凑变体；除此之外字号只允许上表十档。

### 4.3 硬性排版规则

- **字重只允许 400 / 600 / 700**，禁止 300/500/800；禁止给中文加 `letter-spacing > 1px`；禁止 `-webkit-text-stroke`。
- 单行超出：`text-overflow: ellipsis; white-space: nowrap; overflow: hidden`。
- 商品名最多 2 行、帖子正文列表态最多 3 行（`-webkit-line-clamp`），详情页不截断。
- 中英混排：数字与英文前后留 0.25em 自然间距，禁止手工插空格。
- 价格 `$1,280`、计数、Points、时间一律 `tabular-nums`，避免切 tab 时数字跳动。

---

## 5. 组件规范

> 以下为可直接落地的视觉规格；圆角/间距/阴影均引用第 2 节 token。

### 5.1 卡片（inset grouped）

```css
.group-card {
  margin: 0 16px 24px;              /* 左右 16，组距 24 */
  background: var(--bg-grouped-2);
  border-radius: 14px;
  box-shadow: var(--shadow-1);
  overflow: hidden;                  /* 杜绝子元素溢出直角 */
}
.row { min-height: 52px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; position: relative; }
.row:active { background: var(--fill-2); transition: none; }  /* 按下即变色，松开 200ms 淡出 */
.row + .row::before { content:""; position:absolute; left:56px; right:0; top:0; height:.5px; background:var(--separator); }
```
- 标准行高 52 / 双行 64 / 带头像 76；分隔线左 inset 与文字起点对齐（有图标 56、纯文字 16、会话 76），每组最后一行不画。
- 卡片宽度 361；2 列商品卡 174.5（横 gap 12 / 纵 gap 16）；4 宫格列宽 82.25。
- 浅色靠 0.5px hairline 分界即可；深色 hairline 提亮到 `rgba(255,255,255,.12)`。
- 层次建立顺序：分组留白(24) ＞ 发丝线 ＞ 底色差 ＞ 毛玻璃 ＞ 阴影（最后手段）。

### 5.2 按钮（三档体系 —— 本次补充）

| 档位 | 规格 | 用途 |
|---|---|---|
| **主金按钮** Primary | h44 / padding 0 20 / radius pill(9999) 或 12 / bg `var(--gold)` / 文字 `var(--on-gold)` 15/600 / pressed bg `var(--brand-pressed)` + `scale(.97)` 80ms / disabled `var(--brand-disabled-bg)`+`var(--brand-disabled-fg)` | 登录/注册 CTA、未登录引导卡主按钮、发布按钮 |
| **次级按钮** Secondary | h44 / padding 0 20 / radius 12 / bg `var(--fill-2)`（或透明 + .5px `var(--separator-strong)` 描边）/ 文字 `var(--label)` 15/600 / pressed bg `var(--fill-3)` | 取消、次要操作 |
| **文字按钮** Text | 无背景 / 文字 `var(--brand-ink)` 15/600（小号 14）/ pressed `opacity:.6` | 「查看全部 ›」「发起私信」「发起群聊」「› 更多」 |

> 主金按钮**文字必须用 `--on-gold`（深棕），不可用白色**（白字对沙金仅 1.65:1）；这是「沙金做面不做字」的落地要点。
> FAB 为独立特例：56 圆、right 20 / 距屏底 54、bg `var(--label)`、图标 24 反相、`shadow-3`、按下 `scale(.92)`。

### 5.3 标签 / chip / pill

| 类型 | 规格 |
|---|---|
| 话题 chip | h30 / radius 15 / padding 0 12 / bg `var(--fill-2)` / 13px `var(--label-2)`；选中 bg `var(--brand-tint)` + text `var(--brand-ink)` + .5px `rgba(130,104,58,.25)` 描边 |
| 未读 pill | h18 / min-w18 / padding 0 5 / radius 9 / 11/600 白字 / bg `var(--red-solid)`；数字 `tabular-nums`，>99 显示 `99+` |
| 会员等级 pill | h22 / bg `var(--brand-tint)` / 12/600 `var(--brand-ink)` / 前置小皇冠 12 |
| Banner tag | h20 / padding 0 8 / bg `rgba(255,255,255,.92)` / 11/600 `var(--brand-ink)` |
| 红点 | 7–8px `var(--red-solid)` + 1.5–2px 环境色描边环 |

### 5.4 分隔线

- 0.5px `var(--separator)`，左 inset 与文字起点对齐；实色场景用 `var(--separator-opaque)`，分组/tab 底边用 `var(--separator-strong)`。
- 每组最后一行不画；禁止满屏 1px 全宽分隔线。

### 5.5 导航栏（三形态）

- **Large 形态**（社区/消息）：safe-top 59 + 44 工具条 + 大标题区 52；标题 34/700/41/-0.4 左对齐 x=16 `var(--label)`；右侧 3 个 44×44 点击区、图标 22。
- **Compact 形态**（首页）：44 工具条内嵌搜索胶囊 + 扫码/铃铛各 44×44。
- **透明形态**（我的）：nav 底透明叠在 header 上，滚动后浮毛玻璃 + 46px 居中标题。
- **折叠公式**（滚动逐帧，无 CSS transition）：
  ```
  p = clamp(scrollY / 52, 0, 1)
  nav-veil opacity = p；bottom hairline = p * .5px
  large title opacity = 1 - clamp((p-.35)/.5,0,1)；translateY = -12 * p
  compact title opacity = clamp((p-.55)/.35,0,1)；17/600 居中
  ```
  工具条图标不参与折叠；大标题与居中标题不得重叠；禁止滚动时 nav 高度变化/标题跳变/毛玻璃闪烁。

### 5.6 TabBar

- 5 tab 结构保留（宠物 Hub 为默认完整页）；总高 49 + safe-bottom 34 = 83；每 item 78.6 宽。
- 背景 `var(--tab-veil)` + `backdrop-filter: var(--veil-blur)`；顶边 .5px `var(--separator)`。
- 图标 24 / 描边 1.8；标签 **11/400（Caption 2）**；图标与标签间距 2。
- 未选中 `--label-3`；选中图标 `var(--brass)`、标签 `var(--brand-ink)`（**禁用 `#DBC98A` 做标签色**）。
- 角标：红点 7px 或数字 h16，定位图标右上 (+9,−4)，1.5px `var(--tab-veil)` 环。
- 按下整项 `opacity:.6`，无缩放、无背景块（不做 Material ripple）；切换为瞬切 + 交叉淡入（180ms `--ease-ios-out` + translateY 8→0），无页面滑动动画。

### 5.7 搜索胶囊

- h36 / radius 18 / bg `var(--fill-2)`；放大镜 16px `var(--label-3)`；占位 15px `var(--label-3)`；点击区撑满。
- 社区搜索条同规格，margin 8px 16px。滚动 p>0.7 时可缩到 32 高并淡出（可选，交体验侧定）。

### 5.8 头像尺寸阶梯

| 场景 | 规格 |
|---|---|
| 功能列表图标底 | 28×28 / radius 8 / tint 底 + 18 glyph |
| 帖子作者 | 36 圆 |
| 会话行 | 48 圆（行高 76，正文从 x=76 起） |
| 我的页 | 72 圆 + 3px 白环 + `0 2px 8px rgba(46,43,41,.08)` |
| 客服会话 | 48 圆 + `var(--blue-tint)` 底 + `var(--blue-fg)` 耳机图标 |

---

## 6. 对比度校验（WCAG，工程自查）

| 组合 | 对比度 | 判定 / 用途 |
|---|---|---|
| `#2E2B29` on `#F6F2EE` | 12.63:1 | ✅ 主文字 |
| `#6E6962` on `#F6F2EE` | 4.88:1 | ✅ 次要文字（正文级） |
| `#9A948C` on `#F6F2EE` | 2.70:1 | ⚠️ 仅时间/占位/非关键提示，**不可承载关键信息** |
| `#82683A` on `#F6F2EE` | 4.72:1 | ✅ 品牌文字、tab 选中标签 |
| `#82683A` on `#FFFFFF` | 5.26:1 | ✅ 品牌文字（白卡上） |
| `#B38A5A` on `#FFFFFF` | 3.13:1 | ✅ 仅 ≥24px 图形图标（非文本 3:1） |
| `#DBC98A` on `#FFFFFF` | **1.65:1** | ❌ **禁止用于文字/细线，仅做填充** |
| `#2E2B29` on `#DBC98A` | 8.53:1 | ✅ 沙金按钮/VIP 卡上的文字 |
| `#FFFFFF` on `#B04A3F` | 5.39:1 | ✅ 未读角标、删除按钮 |
| `#C9B47A` on `#2A2724`（深） | 7.28:1 | ✅ 深色品牌色 |

**金色文字对比度问题与解决方案（关键决策，务必遵守）：**

- **问题**：沙金 `#DBC98A` 对白底仅 **1.65:1**，远低于 WCAG AA 正文 4.5:1；源码曾把 tabBar 选中标签写死为 `#DBC98A`，10–15px 完全不可读。
- **解决方案**：执行「**沙金做面、古铜墨做字**」——
  - 面：`#DBC98A` 继续大量用于 VIP 卡、CTA 填充、图标底、评分星、section 指示条，品牌识别不丢。
  - 字：文字与 ≤15px 小图标统一用 `#82683A`（对亚麻 4.72:1 / 对白 5.26:1）；≥24px 图形图标可用 `#B38A5A`（3.13:1，满足非文本 3:1）。
  - tabBar 选中：图标 `#B38A5A` + 标签 `#82683A`，**不使用沙金做标签色**（若强行保留沙金，需升至 17px 且加粗——不推荐）。
  - 沙金底上文字：`--on-gold: #2E2B29`（8.53:1），**不可用白字**。
- **三级灰 `#9A948C`（2.70:1）**只能承载时间/占位/非关键提示，禁止用于正文、价格、状态等关键信息。

---

## 7. Anti-Slop 约束清单（审查官对照，命中即判不合格）

**风格与品牌**
- [ ] 无 Material 大圆角浮起卡、无 elevation 层级动画、无全屏彩色 header。
- [ ] 无廉价电商红/橙渐变 CTA、无大红促销标签、无满屏 1px 全宽分隔线。
- [ ] 无 emoji 当主图标；图标为内联 SVG（24×24 viewBox、stroke-width 1.8、圆角端点，选中态 fill 变体）或 CSS 绘制。
- [ ] 沙金未滥用：无 `#DBC98A` 直接做 10–15px 文字或小图标。
- [ ] 深色模式非简单反色：暖色相保留，无纯黑 + 亮金发光的「夜店感」。

**色彩与文字**
- [ ] 所有颜色引用 CSS 变量，无写死 `#DBC98A` 当文字色。
- [ ] 字重只出现 400/600/700；字号只用 34/28/22/20/17/16/15/13/12/11（商品名 14 特例除外）。
- [ ] 价格/计数/Points/时间 `tabular-nums`；中文无 `letter-spacing > 1px`、无 `-webkit-text-stroke`。
- [ ] tabBar 选中标签为 `#82683A`（不是 `#DBC98A`）。
- [ ] 文字对比度：正文 ≥4.5:1；`#9A948C` 未承载关键信息。

**层次与阴影**
- [ ] 阴影最大 alpha ≤ .14，无彩色投影（如金色光晕）；无 blur > 24px / alpha > .18 投影。
- [ ] 无给每张列表行单独加阴影（应在容器层加一次）；无 `box-shadow: inset` 伪装质感。
- [ ] 无卡片 hover 抬升动画；iOS 列表不 hover 浮起。
- [ ] 层次靠 24px 留白 + 0.5px 发丝线 + 底色差，而非堆阴影。

**交互与工程**
- [ ] 毛玻璃底不是纯白写死；`.theme-dark` 一键生效且 nav/tabBar/VIP 渐变/状态栏/Home Indicator 全部换肤。
- [ ] 无外部 CDN 依赖，离线可打开；五 tab 结构保留且均可切换并有点击反馈。
- [ ] 安全区 59/34，末卡 padding-bottom = 83 + 24 = 107。
- [ ] tab 切换为瞬切 + 交叉淡入，无 Material 滑动页动画；按下无 Material ripple。
- [ ] `prefers-reduced-motion: reduce` 生效（所有动画/过渡降为 .01ms）。
- [ ] 触控热区 ≥44×44；未读/点赞有形状或数字双重编码，不单靠色相。

---

## 8. 给原型构建师的落地清单（可直接复制）

### 8.1 浅色 `:root`（完整，复制自 index.html，token 与 VISUAL_DIRECTION §2.2/§6.1/§7.1 一致）

```css
:root {
  /* 背景分层 */
  --bg-grouped: #F6F2EE;  --bg-grouped-2: #FFFFFF;  --bg-grouped-3: #FFFFFF;  --bg-elevated: #FFFFFF;
  --fill-2: #F4F1ED;  --fill-3: #EAE5DD;  --fill-4: #E2DDD5;
  --overlay-subtle: rgba(46,43,41,.04);
  --separator: rgba(46,43,41,.14);  --separator-opaque: #EAE5DD;  --separator-strong: rgba(46,43,41,.22);
  /* 文字 */
  --label: #2E2B29;  --label-2: #6E6962;  --label-3: #9A948C;  --label-4: #B8B2AA;
  /* 品牌 */
  --gold: #DBC98A;  --gold-light: #E8DDB5;  --gold-dark: #B8A66B;  --brass: #B38A5A;
  --brand-ink: #82683A;  --brand-pressed: #A8964F;
  --brand-disabled-bg: #EDE7D6;  --brand-disabled-fg: #B8B2AA;
  --brand-tint: rgba(219,201,138,.18);  --brand-tint-strong: rgba(219,201,138,.28);
  --on-gold: #2E2B29;
  /* 语义 */
  --red: #C96052;  --red-fg: #A84237;  --red-tint: rgba(201,96,82,.12);
  --orange: #D98B4A;  --orange-fg: #9A5A20;  --orange-tint: rgba(217,139,74,.14);
  --green: #7E9B83;  --green-fg: #4F6E57;  --green-tint: rgba(126,155,131,.14);
  --blue: #7C9BB0;  --blue-fg: #4A6E86;  --blue-tint: rgba(124,155,176,.14);
  --red-solid: #B04A3F;  --like: #C96052;
  /* 毛玻璃 */
  --nav-veil: rgba(246,242,238,.82);  --tab-veil: rgba(250,247,244,.80);  --sheet-veil: rgba(246,242,238,.92);
  --veil-blur: saturate(180%) blur(20px);
  /* 产品线四色 */
  --line-care: #DBC98A;  --line-gear: #ABB9AD;  --line-play: #B38A5A;  --line-home: #D9B4B0;
  /* 字体栈 */
  --font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
               "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  --font-num: "SF Pro Display", -apple-system, "PingFang SC", sans-serif;
  /* 阴影 */
  --shadow-0: none;
  --shadow-1: 0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04);
  --shadow-2: 0 2px 8px rgba(46,43,41,.07);
  --shadow-3: 0 8px 24px rgba(46,43,41,.12), 0 2px 6px rgba(46,43,41,.05);
  --shadow-4: 0 12px 40px rgba(46,43,41,.14);
  /* 曲线 */
  --ease-ios-out: cubic-bezier(.2,.9,.25,1);  --ease-ios-sheet: cubic-bezier(.32,.72,0,1);
  --ease-ios-inout: cubic-bezier(.4,0,.2,1);  --spring-like: cubic-bezier(.34,1.56,.64,1);
}
```

### 8.2 深色 `.theme-dark`（完整）

```css
.theme-dark {
  --bg-grouped: #1A1816;  --bg-grouped-2: #2A2724;  --bg-grouped-3: #35312D;  --bg-elevated: #34302C;
  --fill-2: #35312D;  --fill-3: #403B36;  --fill-4: #4A453F;
  --overlay-subtle: rgba(255,255,255,.07);
  --separator: rgba(255,255,255,.12);  --separator-opaque: #3A3530;  --separator-strong: rgba(255,255,255,.18);
  --label: #F6F2EE;  --label-2: #B8B0A5;  --label-3: #807A72;  --label-4: #6B655E;
  --gold: #C9B47A;  --gold-light: #D9C68F;  --gold-dark: #A8964F;  --brass: #C9A26B;
  --brand-ink: #E3CD95;  --brand-pressed: #A8964F;
  --brand-disabled-bg: #3A3428;  --brand-disabled-fg: #7A736A;
  --brand-tint: rgba(201,180,122,.18);  --brand-tint-strong: rgba(201,180,122,.28);
  --on-gold: #1A1816;
  --red: #E58073;  --red-fg: #F0A297;  --red-tint: rgba(229,128,115,.16);
  --orange: #E7A867;  --orange-fg: #F0C08C;  --orange-tint: rgba(231,168,103,.16);
  --green: #9DBFA4;  --green-fg: #BBDBC1;  --green-tint: rgba(157,191,164,.16);
  --blue: #9CC2DA;  --blue-fg: #BCD9EA;  --blue-tint: rgba(156,194,218,.16);
  --red-solid: #B8453A;  --like: #E58073;
  --nav-veil: rgba(26,24,22,.78);  --tab-veil: rgba(32,29,27,.80);  --sheet-veil: rgba(42,39,36,.94);
  --line-care: #C9B47A;  --line-gear: #9FB0A4;  --line-play: #C9A26B;  --line-home: #D6A9A5;
  --shadow-1: 0 1px 3px rgba(0,0,0,.35), 0 0 0 .5px rgba(255,255,255,.06);
  --shadow-2: 0 2px 8px rgba(0,0,0,.45);
  --shadow-3: 0 8px 24px rgba(0,0,0,.55), 0 2px 6px rgba(0,0,0,.35);
  --shadow-4: 0 12px 40px rgba(0,0,0,.6);
}
```

### 8.3 产品线色 tone 类（已含于 index.html，直接复用）

```css
.tone-care, .tone-gear, .tone-play, .tone-home {
  --tile-bg: color-mix(in srgb, var(--line-care) 18%, var(--bg-grouped-2)); --ink:#82683A;
  --t1:#F0E7C8; --t2:#DBC98A; --t3:#B38A5A;
}
.tone-gear { --tile-bg: color-mix(in srgb, var(--line-gear) 18%, var(--bg-grouped-2)); --ink:#5F7465; --t1:#DFE7E0; --t2:#ABB9AD; --t3:#6E8375; }
.tone-play { --tile-bg: color-mix(in srgb, var(--line-play) 18%, var(--bg-grouped-2)); --ink:#8A6238; --t1:#EEDFC9; --t2:#D8B98E; --t3:#A87943; }
.tone-home { --tile-bg: color-mix(in srgb, var(--line-home) 18%, var(--bg-grouped-2)); --ink:#9A6B67; --t1:#F3E0DE; --t2:#D9B4B0; --t3:#B0807C; }
.theme-dark .tone-care, .theme-dark .tone-gear, .theme-dark .tone-play, .theme-dark .tone-home { --ink: var(--line-care); }
.theme-dark .tone-gear { --ink: var(--line-gear); }
.theme-dark .tone-play { --ink: var(--line-play); }
.theme-dark .tone-home { --ink: var(--line-home); }
```

### 8.4 落地自检顺序

1. 整体复制 8.1 `:root` + 8.2 `.theme-dark` + 8.3 tone 类进 `index.html`；`html.theme-dark` 一键换肤。
2. 补 `.price/.count/.points/.time { font-variant-numeric: tabular-nums; }` 与 `prefers-reduced-motion` 降级块。
3. 按第 5 节组件规范搭卡片/按钮/chip/nav/tabBar/搜索胶囊/头像。
4. 导出前用第 7 节清单逐条对照，任一命中即修正。

> 增强可选：点赞弹簧 `--spring-like` 在支持 CSS `linear()` 的内核可改为 `linear(0,0 8%,1.28 32%,.92 66%,1 100%)`；不支持时保留降级版 `cubic-bezier(.34,1.56,.64,1)` + keyframes 三段。

---

*本文件为 Phase 2 交付物，与 `VISUAL_DIRECTION.md` 冲突处以 `VISUAL_DIRECTION.md` 为准（本文仅做体系化补充与收紧，不改任何权威色值）。*
