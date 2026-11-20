# MOYUYO App · Phase 1 视觉方向规范（VISUAL_DIRECTION）

> 面向：体验设计师（Phase 2）、前端工程师（Phase 3）
> 基线：iOS 17/18 HIG + MOYUYO 品牌 Design Token（`src/styles/variables.scss`）
> 原型视口：393 × 852 pt（iPhone 15/16 逻辑像素），**1pt = 1px**，本文所有数值均为 px，不再使用 rpx。
> 状态：本文件是 Phase 1 的唯一视觉事实源；与旧代码冲突时以本文件为准。

---

## 1. 美学方向定调

### 关键词：**暖砂哑光 · 通透秩序 · 精致日常**

| 关键词 | 具体含义 | 为什么适配 MOYUYO |
|---|---|---|
| **暖砂哑光** | 沙金 `#DBC98A` 只做低饱和的「面」，不发光、不加高光渐变；亚麻 `#F6F2EE` 替代 iOS 冷灰 `#F2F2F7` 作为 grouped 底 | 宠物生活的情感属性需要「体温感」；哑光避免做成高饱和促销电商 |
| **通透秩序** | 毛玻璃 nav/tabBar + 0.5px 发丝分隔线 + inset grouped 圆角卡片；层级靠「分组留白」而非阴影 | 「电商 + 社区」信息密度高，必须有强秩序才能不显廉价；毛玻璃提供 iOS 原生通透感 |
| **精致日常** | SF Pro 字体层级克制（全站仅 400/600/700 三档字重）；数字用等宽；图标统一 1.8px 线性笔画 | 面向注重品质生活的宠物主，克制比热闹更可信；社区 UGC 图片才是视觉主角，UI 必须退后 |

### 一句话定位
> 像一本放在玄关的哑光宠物生活方式杂志——沙金是封面烫印，亚麻是纸张，iOS 原生组件是装订线。

### 明确「不要」
- ❌ Material 风：大圆角浮起卡片 + 8px 彩色投影 + elevation 层级动画 + 全屏彩色 header
- ❌ 廉价电商风：大红促销标签、橙红渐变 CTA、满屏 1px 全宽分隔线、字号 5 档混排、emoji 当主图标
- ❌ 沙金滥用：不用 `#DBC98A` 直接做 10–14px 文字或小图标（白底对比度仅 **1.65:1**），沙金**做面不做字**
- ❌ 深色模式简单反色：暖色相必须保留，不能变成纯黑 + 亮金的「夜店感」

---

## 2. 色彩系统（iOS 化转译）

### 2.1 转译原则：沙金做面，古铜做线

iOS 系统色是「中性灰 + 系统蓝」，MOYUYO 的转译方式不是把系统蓝换成沙金，而是：

1. **灰阶暖化**：`systemGroupedBackground #F2F2F7` → 亚麻 `#F6F2EE`；`secondarySystemGroupedBackground #FFFFFF` 保持纯白，形成「暖底 + 白卡」的 iOS inset grouped 层次。
2. **强调色双层化**：
   - **面（Fill）**：`#DBC98A` 沙金 —— 用于按钮填充、VIP 卡、图标底、评分星、选中指示条。与深色文字 `#2E2B29` 组合对比度 **8.53:1**。
   - **线（Ink）**：`#82683A` 古铜墨（由 Antique Brass `#B38A5A` 加深而来）—— 用于品牌色文字、小图标、tabBar 选中态。对白 **5.26:1**、对亚麻 **4.72:1**。
   - 24px 以上的图形图标可用中间档 `#B38A5A`（对白 3.13:1，满足非文本 3:1）；**10–15px 文字/图标一律用 `#82683A`**。
3. **语义色保留 iOS 色相识别**（红/橙/绿/蓝），但降低饱和度 15–25% 以融入暖色环境；危险操作另设高对比实色 `--red-solid` 供白字使用。

### 2.2 浅色主题 Token（原型默认）

```css
:root {
  /* ── 背景分层：iOS systemGrouped 暖化 ── */
  --bg-grouped:        #F6F2EE;              /* systemGroupedBackground / 页面底 */
  --bg-grouped-2:      #FFFFFF;              /* secondarySystemGroupedBackground / 卡片 */
  --bg-grouped-3:      #FFFFFF;              /* tertiarySystemGroupedBackground */
  --bg-elevated:       #FFFFFF;              /* 弹层、浮起卡片 */
  --fill-2:            #F4F1ED;              /* secondarySystemFill / 搜索框、chip、图标底 */
  --fill-3:            #EAE5DD;              /* tertiarySystemFill / 进度轨道 */
  --fill-4:            #E2DDD5;              /* quaternarySystemFill / 禁用底 */
  --overlay-subtle:    rgba(46, 43, 41, 0.04); /* 幽灵表面：返回按钮、搜索框底 */
  --separator:         rgba(46, 43, 41, 0.14); /* 发丝分隔线（0.5px 渲染） */
  --separator-opaque:  #EAE5DD;              /* 需要实色时的分隔线 */
  --separator-strong:  rgba(46, 43, 41, 0.22); /* 分组之间、tab 底边 */

  /* ── 文字三级 ── */
  --label:             #2E2B29;  /* label：正文/标题 */
  --label-2:           #6E6962;  /* secondaryLabel：摘要/副文案 */
  --label-3:           #9A948C;  /* tertiaryLabel：时间/占位/次要提示（2.70:1） */
  --label-4:           #B8B2AA;  /* quaternaryLabel：禁用/chevron */

  /* ── 品牌 ── */
  --gold:              #DBC98A;  /* 面：CTA 填充 / VIP 卡 / 星星 / 选中指示 */
  --gold-light:        #E8DDB5;
  --gold-dark:         #B8A66B;
  --brass:             #B38A5A;  /* 线：≥24px 图形图标 */
  --brand-ink:         #82683A;  /* 线：品牌文字 / ≤15px 图标 / 选中标签 */
  --brand-pressed:     #A8964F;  /* primary pressed */
  --brand-disabled-bg: #EDE7D6;
  --brand-disabled-fg: #B8B2AA;
  --brand-tint:        rgba(219, 201, 138, 0.18); /* 选中 chip / 图标底 */
  --brand-tint-strong: rgba(219, 201, 138, 0.28);
  --on-gold:           #2E2B29;  /* 沙金底上的文字（8.53:1） */

  /* ── 语义色（红/橙/绿/蓝）── */
  --red:        #C96052;  --red-fg:    #A84237;  --red-tint:    rgba(201, 96, 82, 0.12);
  --orange:     #D98B4A;  --orange-fg: #9A5A20;  --orange-tint: rgba(217, 139, 74, 0.14);
  --green:      #7E9B83;  --green-fg:  #4F6E57;  --green-tint:  rgba(126, 155, 131, 0.14);
  --blue:       #7C9BB0;  --blue-fg:   #4A6E86;  --blue-tint:   rgba(124, 155, 176, 0.14);
  --red-solid:  #B04A3F;  /* 白字角标 / 左滑删除（白字 5.39:1） */
  --like:       #C96052;  /* 点赞填充 */

  /* ── 毛玻璃 ── */
  --nav-veil:    rgba(246, 242, 238, 0.82);  /* 大标题折叠后的 nav 底 */
  --tab-veil:    rgba(250, 247, 244, 0.80);  /* tabBar 底（比 nav 更白一档） */
  --sheet-veil:  rgba(246, 242, 238, 0.92);  /* 弹层/动作面板 */
  --veil-blur:   saturate(180%) blur(20px);

  /* ── 产品线四色（用于金刚区/分类图标底，均取 18% 透明度）── */
  --line-care: #DBC98A;  --line-gear: #ABB9AD;  --line-play: #B38A5A;  --line-home: #D9B4B0;
}
```

### 2.3 深色主题 Token

```css
.theme-dark {
  --bg-grouped:        #1A1816;
  --bg-grouped-2:      #2A2724;
  --bg-grouped-3:      #35312D;
  --bg-elevated:       #34302C;
  --fill-2:            #35312D;
  --fill-3:            #403B36;
  --fill-4:            #4A453F;
  --overlay-subtle:    rgba(255, 255, 255, 0.07);
  --separator:         rgba(255, 255, 255, 0.12);
  --separator-opaque:  #3A3530;
  --separator-strong:  rgba(255, 255, 255, 0.18);

  --label:             #F6F2EE;
  --label-2:           #B8B0A5;
  --label-3:           #807A72;
  --label-4:           #6B655E;

  --gold:              #C9B47A;  /* 降饱和，对深色卡 7.28:1 */
  --gold-light:        #D9C68F;
  --gold-dark:         #A8964F;
  --brass:             #C9A26B;
  --brand-ink:         #E3CD95;  /* 深色下反相为浅金 */
  --brand-pressed:     #A8964F;
  --brand-disabled-bg: #3A3428;
  --brand-disabled-fg: #7A736A;
  --brand-tint:        rgba(201, 180, 122, 0.18);
  --brand-tint-strong: rgba(201, 180, 122, 0.28);
  --on-gold:           #1A1816;

  --red:        #E58073;  --red-fg:    #F0A297;  --red-tint:    rgba(229, 128, 115, 0.16);
  --orange:     #E7A867;  --orange-fg: #F0C08C;  --orange-tint: rgba(231, 168, 103, 0.16);
  --green:      #9DBFA4;  --green-fg:  #BBDBC1;  --green-tint:  rgba(157, 191, 164, 0.16);
  --blue:       #9CC2DA;  --blue-fg:   #BCD9EA;  --blue-tint:   rgba(156, 194, 218, 0.16);
  --red-solid:  #B8453A;  /* 白字 5.32:1 */
  --like:       #E58073;

  --nav-veil:    rgba(26, 24, 22, 0.78);
  --tab-veil:    rgba(32, 29, 27, 0.80);
  --sheet-veil:  rgba(42, 39, 36, 0.94);

  --line-care: #C9B47A;  --line-gear: #9FB0A4;  --line-play: #C9A26B;  --line-home: #D6A9A5;
}
```

### 2.4 对比度校验（WCAG，供工程自查）

| 组合 | 对比度 | 判定 / 用途 |
|---|---|---|
| `#2E2B29` on `#F6F2EE` | 12.63:1 | ✅ 主文字 |
| `#6E6962` on `#F6F2EE` | 4.88:1 | ✅ 次要文字（正文级） |
| `#9A948C` on `#F6F2EE` | 2.70:1 | ⚠️ 仅时间/占位/非关键提示；**不可承载关键信息** |
| `#82683A` on `#F6F2EE` | 4.72:1 | ✅ 品牌文字、tab 选中标签 |
| `#B38A5A` on `#FFFFFF` | 3.13:1 | ✅ 仅 ≥24px 图形图标 |
| `#DBC98A` on `#FFFFFF` | 1.65:1 | ❌ 禁止用于文字/细线，仅做填充 |
| `#2E2B29` on `#DBC98A` | 8.53:1 | ✅ 沙金按钮/VIP 卡上的文字 |
| `#FFFFFF` on `#B04A3F` | 5.39:1 | ✅ 未读角标、删除按钮 |
| `#C9B47A` on `#2A2724`（深色） | 7.28:1 | ✅ 深色品牌色 |

> **关键决策 / 需要 team-lead 知悉**：源码里 tabBar 选中色写死 `#DBC98A`，对白底只有 1.65:1，10px 标签不可读。本规范改为「选中图形 `#B38A5A` + 选中文字 `#82683A`」，沙金仍大量出现在 VIP 卡、CTA、图标底、评分星、section 指示条上，品牌识别不丢失。若必须保留原色，则选中标签需升至 17px 且加粗——不建议。

### 2.5 产品线四色使用规则
金刚区/订单宫格/分类图标底统一用 `color-mix(in srgb, var(--line-*) 18%, var(--bg-grouped-2))` 得到浅底；图标本身用该色的 600 档加深版本（Care→`#82683A`、Gear→`#5F7465`、Play→`#8A6238`、Home→`#9A6B67`）。**同一屏最多出现 2 种产品线色**，避免调色盘失控。

---

## 3. 字体系统

### 3.1 字体栈

```css
:root {
  --font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
               "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei",
               "Helvetica Neue", Arial, sans-serif;
  --font-num:  "SF Pro Display", -apple-system, "PingFang SC", sans-serif;
}
body { font-family: var(--font-sans); -webkit-font-smoothing: antialiased; }
.price, .count, .points, .time { font-family: var(--font-num); font-variant-numeric: tabular-nums; }
```

> 全站**只允许 400 / 600 / 700 三档字重**。禁止 300/500/800 混用；禁止给中文加 `letter-spacing > 1px`；禁止 `-webkit-text-stroke`。

### 3.2 iOS 文本样式阶梯（393pt 视口，1pt=1px）

| 样式 | 字号 | 字重 | 行高 | 字距 | 典型用途 |
|---|---:|---:|---:|---:|---|
| **Large Title** | 34 | 700 | 41 | -0.4 | 社区/消息页大标题 |
| **Title 1** | 28 | 700 | 34 | -0.35 | VIP Points、钱包数字 |
| **Title 2** | 22 | 700 | 28 | -0.3 | 我的页昵称、Banner 标题 |
| **Title 3** | 20 | 600 | 25 | -0.25 | 区块大标题「我的订单」 |
| **Headline** | 17 | 600 | 22 | -0.2 | 卡片主标题、section 标题 |
| **Body** | 17 | 400 | 22 | -0.15 | 帖子正文、会话摘要 |
| **Callout** | 16 | 400 | 21 | -0.15 | 商品名、功能行 |
| **Subhead** | 15 | 400 | 20 | -0.1 | tab 文字、搜索占位 |
| **Footnote** | 13 | 400 | 18 | 0 | 时间戳、描述、标签 |
| **Caption 1** | 12 | 400 | 16 | 0.1 | 角标、辅助说明、wallet 标签 |
| **Caption 2** | 11 | 400 | 13 | 0.1 | tabBar 标签、最小提示 |

### 3.3 旧 token → 新 px 迁移表

| 旧变量（rpx） | 新值（px） | 对应样式 |
|---|---:|---|
| `--font-size-xs: 22rpx` | 11 | Caption 2 |
| `--font-size-sm: 26rpx` | 13 | Footnote |
| `--font-size-base: 28rpx` | 14 | Callout |
| `--font-size-md: 30rpx` | 15 | Subhead |
| `--font-size-lg: 32rpx` | 16 | Callout+ |
| `--font-size-xl: 36rpx` | 18 | Headline+ |
| `--font-size-2xl: 44rpx` | 22 | Title 2 |
| `--font-size-3xl: 56rpx` | 28 | Title 1 |

> 原型直接用 px 写死上述阶梯；正式代码迁移时建议保留旧变量名做 alias，避免一次性改动全站。

### 3.4 排版规则
- 单行文本超出：`text-overflow: ellipsis; white-space: nowrap; overflow: hidden`
- 商品名最多 2 行、帖子正文列表态最多 3 行（`-webkit-line-clamp`），详情页不截断
- 中英混排：数字与英文前后留 0.25em 自然间距，禁止手工插空格
- 价格 `$1,280` 必须 `tabular-nums`，避免切 tab 时数字跳动

---

## 4. 布局栅格与间距

### 4.1 视口与安全区

```
┌─────────────────────────────┐ 393px
│  status bar / safe-top  59px │  灵动岛机型；刘海机型 47px
│  nav bar                44px │
├─────────────────────────────┤
│                             │
│  content  (可滚动)           │  ← 左右 page margin 16px
│                             │
├─────────────────────────────┤
│  tabBar  49px + safe-bottom │  34px（Home Indicator）
└─────────────────────────────┘
```
- 原型固定 `safe-top: 59px`、`safe-bottom: 34px`；真机用 `env(safe-area-inset-top/bottom)`，二者取 `max()`。
- 内容滚动区可穿透到 nav/tabBar 底下（毛玻璃才有意义），但**最后一个卡片的底部需留 `tabBar 高度 + 24px` 的 `padding-bottom`**，防止被遮挡。
- 页面切换时保留各 tab 的独立滚动位置。

### 4.2 栅格与卡片宽度

| 项 | 值 |
|---|---|
| 页面左右边距 | 16px |
| 卡片宽度（inset grouped） | 393 − 32 = **361px** |
| 2 列商品卡 | gap 12px，单卡 **174.5px** |
| 4 宫格（金刚区/钱包/订单/通知/我的社交） | 4 等分，列宽 **82.25px**，中心间距 90.25px |
| 会话头像列 | 48px + 12px gap，正文从 x=76px 起 |

### 4.3 圆角标尺（iOS continuous corner）

| 对象 | 圆角 |
|---|---:|
| inset grouped 卡片 / 列表行容器 | **14px** |
| 列表行内图片、商品图 | 12px |
| Banner 卡 / 帖子大图容器 | 18px |
| VIP 会员卡 / 弹层 sheet 顶部 | **20px** |
| 金刚区图标方块 | 16px |
| chip / 胶囊搜索框 / 角标 | 9999px |
| FAB / 圆形头像 | 50% |
| 小图标底（功能列表） | 8px |

### 4.4 间距刻度（8pt 基准，兼容旧 rpx）

| token | px | 旧 rpx | 用途 |
|---|---:|---:|---|
| space-1 | 4 | 8 | 图标与角文字、chip 内微调 |
| space-2 | 8 | 16 | 行内元素间距、话题 chip 间距 |
| space-3 | 12 | 24 | 卡片内上下 padding、宫格间隙 |
| space-4 | 16 | 32 | **页面边距**、列表行左右 padding |
| space-5 | 20 | 40 | 卡片之间垂直间距（ compact ） |
| space-6 | 24 | 48 | **section 之间**、卡片之间（常规） |
| space-8 | 32 | 64 | 大区块呼吸、页脚上间距 |

### 4.5 列表行尺寸
- 标准行高 **52px**（单行 + chevron），双行 **64px**，带头像 **76px**
- 分隔线 0.5px，**左 inset 与文字起点对齐**（有图标时 = 16+28+12 = 56px；纯文字 = 16px），右侧通到卡片边
- 每组最后一行**不画分隔线**
- section header：上 16 / 下 8，左右 16；section footer：上 8，12px `--label-3`

---

## 5. 组件视觉规范

### 5.1 顶部导航栏（Large Title 折叠）

**两种形态**
- **Large 形态**（社区、消息）：`safe-top 59px` + 44px 工具条 + 大标题区 52px。标题 `34/700/41/-0.4`，左对齐 x=16，颜色 `--label`。右侧 3 个 44×44 点击区、图标 22px `--label`（铃铛红点/number badge）。
- **Compact 形态**（首页）：44px 工具条内放搜索胶囊（h36、radius 18、bg `--fill-2`、放大镜 16px `--label-3`、占位 15px `--label-3`、可点击区撑满），右侧扫码 + 铃铛各 44×44、图标 22px。
- **透明形态**（我的）：nav 底透明叠在个人 header 上，仅滚动后浮出毛玻璃 + 46px 居中标题。

**折叠动画（滚动驱动，需可暂停在任意进度）**
```
p = clamp(scrollY / 52, 0, 1)
nav-veil opacity   = p                       // background: var(--nav-veil); backdrop-filter: var(--veil-blur)
bottom hairline    = p * 0.5px  --separator
large title        opacity = 1 - clamp((p - 0.35) / 0.5, 0, 1); translateY = -12 * p
compact title      opacity = clamp((p - 0.55) / 0.35, 0, 1); 17/600 居中
```
- 工具条图标**不参与折叠**，始终可见；大标题消失后再出现居中标题，两者不重叠。
- 首页搜索胶囊同理：`p > 0.7` 时缩到 32px 高并淡出 0，居中显示「首页」小标题（可选，交给体验侧定）。
- 禁止：滚动时 nav 高度变化、标题跳变、毛玻璃闪烁。

### 5.2 分组圆角卡片 / 列表行（inset grouped）

```css
.group-card {
  margin: 0 16px 24px;           /* 组间距 24px */
  background: var(--bg-grouped-2);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04);
  overflow: hidden;
}
.row {
  min-height: 52px;
  padding: 12px 16px;
  display: flex; align-items: center; gap: 12px;
  position: relative;
}
.row:active { background: var(--fill-2); transition: none; }  /* 按下即变色，松开 200ms 淡出 */
.row + .row::before {
  content:""; position:absolute; left:56px; right:0; top:0;
  height:.5px; background:var(--separator);
}
.chevron { width:13px; height:13px; color:var(--label-4); }
```
- 卡片在浅色底上**不用描边也能靠 0.5px hairline 分界**；深色下 hairline 必须提亮到 `rgba(255,255,255,.12)`，否则卡片消失。
- 圆角裁切必须 `overflow:hidden`，杜绝子元素溢出直角。

### 5.3 首页

**① 金刚区（4 宫格）**
- 容器：左右 16，上 8 下 16，4 等分；cell 高 84px，点击区整格
- 图标：52×52，radius 16，底色 = 对应产品线色 18% 混白；居中线性 glyph 24px（描边 1.8，产品线深色档）
- 标签：12px/400 `--label`，图标下 8px
- 按下：`transform: scale(.96)` 120ms；**不加阴影**

**② Banner 轮播**
- 尺寸：宽 361 / 高 203（16:9），radius 18，margin 0 16 16
- 图片 `object-fit: cover`；兜底：`linear-gradient(135deg, #E8DDB5 0%, #DBC98A 48%, #B38A5A 100%)` + 居中 64px 低透明度爪印
- 底部遮罩：`linear-gradient(to top, rgba(46,43,41,.58) 0%, rgba(46,43,41,0) 55%)`
- tag：pill h20、padding 0 8、bg `rgba(255,255,255,.92)`、text 11/600 `--brand-ink`，位于标题上方 6px
- 标题：20/700 白，左下 16/底 20，最多 1 行
- 指示点：底部居中 y=底 12；未选中 4px 圆点 `rgba(255,255,255,.45)`，选中 12×4 胶囊白；间距 6
- 自动播放 3000ms，切换 500ms ease-in-out，手势跟手无回弹

**③ 推荐区 3 tab**
- sticky 在 nav 之下（`top: calc(59px + 44px)`），h44，底 0.5px `--separator`，背景 `--tab-veil` + `--veil-blur`
- 标签 15px，未选 `--label-3`、选中 17/600 `--brand-ink`；三段等分或按文字宽度居中分布（推荐等分，更 iOS）
- 指示条：宽 20 / 高 3 / radius 2 / `--gold`，位于标签下 4px，滑动 250ms
- 区块标题「猜你喜欢」放在 tab 上方还是商品上方由体验侧定；若保留，用 Title3 20/600，左右 16，上 16 下 8

**④ 2 列商品卡**
- 宽 174.5，白底 radius 14，overflow hidden，`box-shadow: 0 1px 3px rgba(46,43,41,.05)`
- 图：1:1（174.5×174.5），radius 12（外卡 14，内缩 0 时用上圆角 14），`object-fit: cover`，占位 `--fill-2` + 24px 爪印
- body padding 10px 12px 12px
- 名称：14/600 `--label`，2 行 clamp，行高 19
- 描述：12/400 `--label-3`，1 行 clamp，上 4
- 价格行：上 8；现价 17/700 `--label` + 货币符 13/600；原价 12 `--label-3` 删除线，左 6
- 评分行：上 6；星 12px `--gold` 实心 + 12/400 `--label-2` 数字 + `·` + 12 `--label-3` 评价数
- 卡片间：横向 gap 12，纵向 gap 16；按下 `scale(.985)` + shadow 略增，150ms

### 5.4 社区

**顶部 / Tab**
- Large 标题「社区」+ 右搜索、铃铛（未读红点 8px 带 1.5px 白环）
- 分段 tab h44：推荐 / 关注 / 话题；选中 15/600 `--label`，未选 15/400 `--label-3`
- 下划线：宽 24 / 高 2 / radius 1 / `--brand-ink`，居中于标签，切换 220ms 位移

**搜索条 & 话题 chip**
- 搜索条：margin 8px 16px，h36，radius 18，bg `--fill-2`，同首页胶囊规格
- 话题 chip 横滚：左右 padding 16，gap 8，隐藏滚动条；chip h30 / radius 15 / padding 0 12 / bg `--fill-2` / 13px `--label-2`；选中 bg `--brand-tint`、text `--brand-ink`、border .5px `rgba(130,104,58,.25)`

**帖子卡片**
- 卡片：margin 0 16px 16px，白底 radius 14，overflow hidden
- 作者行 h56，padding 0 12；头像 36 圆；昵称 15/600 `--label`；时间 12 `--label-3`；右侧更多 `⋯` 22px hit 44×44
- 图片：单图宽 329（卡宽−24）radius 10，最大高 240，`object-fit: cover`；2–9 图九宫格 gap 4、单元 ~107px 方、radius 4，2 图时两列、4 图时 2×2
- 正文：padding 12 12 8，15/400/21 `--label`，列表态 3 行 clamp，话题词 `--brand-ink`
- 互动行：h44，上 0.5px `--separator`，三等分；图标 20px 描边 1.8 `--label-2`，计数 12 `--label-3`，图标与计数 gap 6
- 点赞态：心形填充 `--like`，计数 `--red-fg`，触发弹簧缩放（见 §7）

**FAB**
- 56×56 圆形，right 20，bottom `calc(34px + 20px)` = 距屏底 54px
- 背景 `--label`（浅色深炭 / 深色浅亚麻），图标 24 白色/深色反相，`box-shadow: 0 8px 24px rgba(46,43,41,.18)`
- 按下 `scale(.92)`；下滑时 200ms 淡出至 0，上滑回显

### 5.5 消息

**通知四宫格**
- 白卡 361×88，radius 14，4 等分，每格垂直居中
- 圆形图标底 40px（产品线/语义 tint 18%），glyph 20px 对应 fg 色；标签 12 `--label-2`，图标下 6
- 红点：8px `--red-solid`，位于图标右上 (top 0 / right 0)，2px `--bg-grouped-2` 描边环

**区块标题**
- h32，margin 24px 16px 8px；左侧 15/600 `--label` + 未读 pill（h18 / min-w18 / padding 0 5 / 11/600 白字 / `--red-solid`）；右侧「发起私信」14 `--brand-ink`

**会话行 + 左滑删除**
- 行高 76，padding 0 16；头像 48 圆；右上名称 16/600 `--label`（置顶图标 12 `--label-3` 前置，gap 4）；时间 12 `--label-3`；下方摘要 14 `--label-2` 单行；右下未读角标
- 客服会话头像：`--blue-tint` 底 + `--blue-fg` 耳机图标；客服会话**不提供删除**
- 分隔线左 inset 76px（对齐文字起点）
- 左滑：露出宽 80px，底层 `--red-solid`，白字 14「删除」；阈值 40px 触发吸附；超出 80px 阻尼 `80 + (delta-80)*0.2`；松手 spring 250ms 归位或吸满；行打开时点击行先关闭，不进会话
- 长按：iOS 动作面板（见 §7），含「置顶 / 删除」

**未读角标统一规格**
| 位置 | 尺寸 | 样式 |
|---|---|---|
| tabBar | 7px 点 / 数字 h16 min-w16 | `--red-solid`，1.5px `--tab-veil` 环 |
| nav 铃铛 | h16 min-w16 / 点 8px | 同上，数字 >99 显示 `99+` |
| 会话行 | h18 min-w18 / padding 0 5 | 11/600 白色 `tabular-nums` |
| 通知入口 | 8px 点 | 2px 卡片色环 |

### 5.6 我的

**头像区**
- header 高约 200px（含 safe-top 59），背景 `--bg-grouped`，无分隔线
- 头像 72 圆，3px 白环 + `0 2px 8px rgba(46,43,41,.08)`；右侧昵称 22/700 `--label`，邮箱 13 `--label-2`，会员等级 pill（h22 / bg `--brand-tint` / 12/600 `--brand-ink` / 前置小皇冠 12）
- 右侧 chevron 13 `--label-4`；未登录态：头像 + 「登录/注册」22/700 + 副标题 13 + 右侧 pill 按钮（沙金填充 + 深色文字 14/600）

**VIP 卡**
- margin 16px 16px 24px，h132，radius 20，overflow hidden
- 背景 `linear-gradient(135deg, #E8DDB5 0%, #DBC98A 46%, #B38A5A 100%)`；叠加一层 `radial-gradient(circle at 85% 15%, rgba(255,255,255,.35), transparent 45%)` 与右下 120px 描边圆装饰（`rgba(46,43,41,.12)`）
- 文字全部 `--on-gold`；标题 18/700，Points `28/700/34` `tabular-nums`，tip 12/400 opacity .75；右侧 chevron 16
- 阴影 `0 8px 24px rgba(179,138,90,.22)`；深色模式下渐变改为 `#A8964F → #C9B47A → #E3CD95`、文字 `#1A1816`
- 按下 `scale(.985)` 150ms

**钱包 / 社交宫格**
- 卡片 radius 14，白底；4 等分，每格 min-h 76；数字 20/700 `tabular-nums` `--label`，标签 12 `--label-2`，间距 4
- 社交宫格额外在数字上方放 20px 图标（`--brass`），形成「图标 / 数字 / 标签」三段，总高 92

**订单宫格**
- 卡片头部 h48，padding 0 16：左「我的订单」17/600，右「查看全部 ›」14 `--brand-ink`
- 下 0.5px `--separator`（inset 16）
- 4 图标格 h80：图标底 44 圆角 14（`--fill-2`），glyph 24 `--brass`；标签 12 `--label-2`；角标右上 8px 红点或 h16 数字 pill

**功能列表（11 项）**
- 若保持单组：一张 inset card 11 行，每行 52px；左 28×28 圆角 8 的 tint 图标底 + 18px glyph，文字 15 `--label`，右 chevron
- **建议体验侧拆为 3 组**（常用 / 账户与服务 / 关于），组间 24px，section header 12/600 `--label-3`，字距 0.5；视觉规范两种都支持
- 页脚：居中 padding 32px 16px 24px，品牌字 14/600 `--brand-ink`，slogan 12 `--label-3`

### 5.7 底部 tabBar

- **5 tab 结构保留**：宠物 / 首页 / 社区 / 消息 / 我的（宠物位提供占位图标，功能不在本次范围）
- 总高 49 + safe-bottom 34 = 83px；每 item 78.6px 宽
- 背景 `var(--tab-veil)` + `backdrop-filter: var(--veil-blur)`；顶边 0.5px `--separator`
- 图标 24px 描边 1.8；标签 10/400；图标与标签间距 2；未选中 `--label-3`
- 选中：图标 `--brass`（≥24px 图形满足 3:1）、标签 `--brand-ink`（4.7:1）；**不使用沙金 `#DBC98A` 做标签色**
- 角标：红点 7px 或数字 h16，定位在图标右上 (+9px, −4px)，1.5px `--tab-veil` 环
- 按下：整项 `opacity: .6`，无缩放、无背景块（不做成 Material ripple）
- 切换内容：见 §7

---

## 6. 阴影与层次

### 6.1 iOS 极轻阴影 token

```css
:root {
  --shadow-0: none;                                                      /* 平面：列表行 */
  --shadow-1: 0 1px 3px rgba(46,43,41,.05), 0 0 0 .5px rgba(46,43,41,.04); /* 卡片 */
  --shadow-2: 0 2px 8px rgba(46,43,41,.07);                              /* sticky nav / 吸顶 tab */
  --shadow-3: 0 8px 24px rgba(46,43,41,.12), 0 2px 6px rgba(46,43,41,.05);/* sheet / FAB */
  --shadow-4: 0 12px 40px rgba(46,43,41,.14);                            /* 模态弹层 */
}
.theme-dark {
  --shadow-1: 0 1px 3px rgba(0,0,0,.35), 0 0 0 .5px rgba(255,255,255,.06);
  --shadow-2: 0 2px 8px rgba(0,0,0,.45);
  --shadow-3: 0 8px 24px rgba(0,0,0,.55), 0 2px 6px rgba(0,0,0,.35);
  --shadow-4: 0 12px 40px rgba(0,0,0,.6);
}
```

### 6.2 层次建立顺序（优先级从高到低）
1. **分组留白**（24px section gap）—— 最重要
2. **发丝分隔线**（0.5px `--separator`，文字起点对齐）
3. **底色差**（亚麻底 `#F6F2EE` vs 白卡 `#FFFFFF`）
4. 毛玻璃（仅 nav / tabBar / sticky tab / 弹层遮罩）
5. 阴影（最后手段，最大 alpha ≤ .14）

### 6.3 明确禁止
- ❌ blur > 24px 或 alpha > .18 的投影；❌ 彩色投影（如金色光晕）
- ❌ 给每张列表行单独加阴影（应在容器层加一次）
- ❌ `box-shadow: inset` 做「高级质感」——用 1px 内描边或渐变即可
- ❌ 卡片悬浮上浮动画（iOS 列表不 hover 抬升）

---

## 7. 动效基调

### 7.1 曲线常量

```css
:root {
  --ease-ios-out:    cubic-bezier(0.2, 0.9, 0.25, 1);      /* 通用 UI 退出/进入 */
  --ease-ios-sheet:  cubic-bezier(0.32, 0.72, 0, 1);       /* iOS 模态/动作面板 */
  --ease-ios-inout:  cubic-bezier(0.4, 0, 0.2, 1);         /* 指示条、进度 */
  --spring-like:     linear(                                /* 线性近似弹簧，点赞用 */
    0, 0 8%, 1.28 32%, 0.92 66%, 1 100%);
}
```

> CSS `linear()` 在旧内核不支持时，降级为 `cubic-bezier(0.34, 1.56, 0.64, 1)` + keyframes 三段实现。

### 7.2 具体参数

| 交互 | 参数 |
|---|---|
| **tab 切换** | 内容交叉淡入 + `translateY(8px→0)`，180ms `--ease-ios-out`；图标/标签颜色 220ms；**无滑动页面动画**（iOS tabBar 是瞬切） |
| **大标题折叠** | 滚动逐帧驱动，无 CSS transition；松手惯性由滚动接管 |
| **点赞** | 心形 `scale: 1 → 1.28(100ms) → .92(200ms) → 1(320ms)`，填充色交叉 120ms；计数数字 120ms 淡入 |
| **弹层 / 动作面板** | 面板 `translateY(100%→0)` 420ms `--ease-ios-sheet`；遮罩 `opacity 0→1` 200ms linear；关闭 300ms 同曲线 |
| **左滑删除** | 跟手无 transition（`transition: none`）；松手吸附 250ms `--ease-ios-out`；删除后行高收拢 250ms + 相邻行上移 |
| **Banner 轮播** | 500ms ease-in-out 淡入淡出；自动 3000ms；手动滑动跟手 |
| **分段 tab 指示条** | 220ms `--ease-ios-inout`，位移 + 宽度同时过渡 |
| **按钮按下** | `transform: scale(.97)` 80ms；抬起 160ms `--ease-ios-out`；列表行仅变色不缩放 |
| **下拉刷新** | 阻尼系数 0.5，阈值 60px，释放后回弹 300ms 弹簧 |

### 7.3 可访问性
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
}
```

---

## 8. 工程落地检查清单（Phase 3 自检）

- [ ] 所有颜色引用 CSS 变量，无写死 `#DBC98A` 当文字色
- [ ] 提供 `.theme-dark` 切换，`html.theme-dark` 一键生效；毛玻璃底 **不是** 纯白写死
- [ ] 安全区：上 59 / 下 34，内容底部 padding = 83 + 24
- [ ] 卡片圆角 14、VIP 20、Banner 18；列表行分隔线左 inset 与文字对齐
- [ ] 字号只出现 34/28/22/20/17/16/15/13/12/11 十档，字重只 400/600/700
- [ ] 价格/计数/时间 `tabular-nums`
- [ ] 阴影最大 alpha ≤ .14，无彩色投影
- [ ] tabBar 选中标签为 `#82683A`（不是 `#DBC98A`）
- [ ] 左滑删除有 40px 阈值与阻尼，删除按钮白字对比度 ≥ 4.5
- [ ] `prefers-reduced-motion` 生效
- [ ] 无外部 CDN 依赖，图标为内联 SVG / CSS 绘制
- [ ] 离线打开 index.html 四个 tab 均可切换并有点击反馈

---

## 9. 与上下游对齐说明

1. **给体验设计师（Phase 2）**
   - 本规范建议保留 5 个 tabBar 位（宠物占位），但 iOS 惯例是「首页」居首。若决定重排为 `首页 / 社区 / 宠物 / 消息 / 我的`，本组件规范完全适用，仅需同步改默认选中项与原型顺序。**请在 UX_SPEC 中明确结论**，视觉侧不阻塞任一方案。
   - 我的页 11 项功能列表：视觉上支持「单组 11 行」与「3 组分栏」两种，建议采用后者以降低长列表疲劳；最终以 UX_SPEC 为准。
   - 首页推荐区是否需要独立 section 标题、社区 tab 是否保留「话题」独立 tab，视觉侧两种都支持。
2. **给前端工程师（Phase 3）**
   - §2.2 / §2.3 的 CSS 变量块可直接整体复制进 `index.html` 的 `:root` 与 `.theme-dark`。
   - §7.1 曲线常量可直接复制；`linear()` 有降级方案。
   - 原型静态数据请使用真实功能文案方向（分类/会员/领券/限时、猜你喜欢/今日爆款/口碑好评、推荐/关注/话题、全部/互动/订单/系统、我的订单四态等），不要用 Lorem ipsum。
   - 图标建议内联 SF-Symbol 风格 SVG：24×24 viewBox、stroke-width 1.8、`stroke-linecap: round`，选中态用 fill 变体。
3. **遗留冲突处理**：`variables.scss` 中 `--font-size-*`（rpx）、tab 选中色 `#DBC98A`、阴影 `--shadow-lg` alpha .1 —— 与本文档冲突处，**原型阶段以本文档为准**；正式代码迁移另行排期。
