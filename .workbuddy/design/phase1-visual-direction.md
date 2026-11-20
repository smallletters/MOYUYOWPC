# MOYUYO TabBar 视觉设计规范 · Phase 1

> **产出角色**：视觉设计架构师（frontend-design-architect）
> **范围**：5 个 tabBar 主页的视觉方向 + 共享设计系统升级 + 逐页布局规范
> **不含**：交互流程、信息架构调整、代码实现（由 Phase 2 / 前端工程师承接）
> **基准**：uni-app 750rpx 基准；对应代码 `moyuyo-app/src/styles/{variables,common}.scss`、`src/pages/tabbar/*.vue`
> **文档版本**：v1.12（**终版** · §6.16.6 修正 `selectedText` 提法：已核实 `pages.json` 5 项 `text` 与 `selectedText` **完全相同** → 补一个等于 `text` 的 `selectedText` **是无效改动**（原 v1.11 写「补上只是更完整」有误）；§16.4 补 **`selectedText` 取值策略** + 单测断言；§6.13.6 标注为 **Phase 3 硬性交付项**（team-lead 已确认））
>
> **变更记录**
> | 版本 | 变更 |
> |---|---|
> | v1.0 | 初版：品牌方向 + 设计系统 + 4 页规范 + 实现约束 + 反面清单 |
> | v1.1 | §6 约束 6 重写为「字体方案 A 定案」+ 体积控制 + fallback 链 + 验收清单；§6 约束 4 新增 `user.vue` 滚动甲/乙双方案并列规范；新增 **§6.13 原型实现映射**；§0/§4.7 同步 |
> | v1.2 | **§4.4** 新增产品线色使用规则（产品线色浅色模式永不做文字）；**§4.1/§5.4.2** 会员卡渐变改暗古铜（修复白字 2.17:1 硬伤）；**§4.7** 新增 `--color-danger-strong`；**§4.8** `--font-size-4xl` 用途修正、Hero 标题统一 44rpx；**§4.10** 补 9 行实测；**§5.1.4/§5.2.2⑦/§5.3.2②b/§5.4.2/§5.4.5** 同步；**§7** 新增 X8–X10 |
> | **v1.3** | **§4.6** 补 `--skeleton-gradient` 定义（原被 §5.1.4/§5.2.4/§5.3.4 引用却未定义 → Phase 3 会拿到 undefined）；**§4.7** 新增动效 / 层级 / 禁用态 token 组；**§3** 展示体位置收敛为 **3 类 Latin 短文本**；**§5.6** 状态矩阵扩至 **65 态**并标注社区 2 个隐性 bug；**§6** 新增约束 13（深色值双块写入） |
> | **v1.4** | **§5.4.2** 会员卡按 team-lead 最终裁定改回「**56rpx = 等级名（英文）+ 24rpx 中文副标 + 刻度条右端 22rpx 精确成长值**」，新增**等级名文案表**（L5 = `Black` 黑卡，非 `DIAMOND`）；**§5.4.3/§4.7/§4.8/§4.9/§3/§8** 同步；**§5.2.2⑦** 社区 chip 明确标注「⛔ 待后端 `brand_ip_id` 字段，原型不得假数据硬编」；**§5.2.2③** 新增**话题栏右缘渐隐遮罩**规格；**§6.13.6** Spec 面板新增「**数据依赖**」字段 + 12 行数据依赖速查表；**§6 约束 8** 补 `mission-center.vue:455` 第二处蓝色泄漏；**§6 新增约束 14**（H5 原生 page 标题 / tabBar 文案走系统字体栈）；**§1** 新增 F15–F18 四条现状事实（据 baseline-capture 实测） |
> | **v1.5** | **后端源码实证回填（ux-designer 提供，visual-architect 已逐条独立验证）**：**§4.4.2 新增「产品线 ID 唯一真值源」**（DB 权威 1=MILO→GEAR / 2=LUNA→CARE / 3=ATLAS→PLAY / 4=OLIVE→HOME）+ **B1** `goods/detail.vue:370` 1/2 相反 + **B2** `tag-milo` 类名不存在导致徽标无色；**§4.4.1** 撤销"社区 chip 待后端字段"结论（`ip_code` **已在库有索引**，仅 VO 未透出，**是真数据**）+ `.tag-*` 需覆盖 IP 别名与语义别名两套；**§5.2.2⑦ / §6.13.6** 同步；**§5.2.2③** 明确 Topic Rail 的**发现入口 / 筛选器**双重定位 + 话题 tab 选择器态；**§5.6** 新增 **B3**（话题 tab 未真正过滤 → `C-TOPIC-EMPTY` 是死代码 + `emptyTopic` 文案无指针）与 **B4**（产品线 ID 冲突）、**状态数口径说明**（65 含 5 个交互动作态 / 60 数据态）；**§6.6.7 新增**中文 slogan 伪斜体裁定（全 App 取消斜体，正体 + 字距）；**§6 约束 8** 补 `goods/detail.vue:370` 与 `common.scss` 缺 `tag-milo` 类名；**§6.13.3** 补 sticky + 原生下拉冲突、APP 端无 `document.fonts`；**§4.1** 更正 `pages.json` 已配 `enablePullDownRefresh` |
> | **v1.6** | **DB `color_hint` 权威性裁定（team-lead 提出，visual-architect 核实后采纳）**：**§4.4.2** 表新增「DB `color_hint`（仅供参考，已错位）」列并显式写出 3 处错位；新增 **E1–E4 四条「`color_hint` 非权威」证据**（建表注释示例值 = LUNA 实际值 / ATLAS 填了 Charcoal 正文色 / 独缺 HOME 且顺序错位 / 无任何运行时消费点）；**裁定色条取色一律以语义线（`variables.scss`）为准**；**诚实性修正**：IP→语义线映射本身**也是推断**（DB 无此列），已标注「待产品侧确认」并给出正确取色链路（三层不可跳步）；**新增** DB 里更可靠的辅助维度 `species`（DOG/CAT 交替）；**§6 约束 8** 补 B1 的"与 `goods/list.vue` `resolveIpName` 是两套东西、不要统一"说明 + `pet-hub-3d.vue` 第三套 IP 配色（iOS 系统色，判定为合理差异化但需 token 化）；**§1** 新增 **F19 / F20** 两条现状事实 |
> | **v1.7** | **新增平台约束 15**：`switchTab` **路径后不能带参数**（`@dcloudio/types/.../uni.d.ts:5391` 原文核实）→ tabBar 页登录回跳必须走 **storage + action**（`ts` 时间戳 + ≤5min 有效期 + 消费即删 + action 白名单），并给出与 `redirectTo` 的分工表；**§5.6** 补 `C-TOPIC-SELECTED` 并对齐 Phase 2 v1.2 的 **67 态**（状态 ID 零差异，脚本 diff 校验）；**§6.13.3** 补「原型自绘 tabBar 带 query 可行、真机静默丢参」陷阱；**§6.13.6** 速查表补「tabBar 页登录回跳传参」行；**§7** 新增 **X11**；**§1** 新增 **F21** |
> | **v1.12（终版）** | **§16.6 修正 `selectedText` 表述** —— 已核实 `pages.json` 的 5 项 `text` 与 `selectedText` **完全相同**（用 `selectedText` 区分选中态是「两侧一致的有意设计」，靠 `color` 而非字形区分）；且 `theme.js:268` 已有 `ic.selectedText \|\| ic.text` 兜底 → **给 fallback 补一个等于 `text` 的 `selectedText` 是无效改动**（v1.11 原文「补上只是更完整」有误，已改为「不必做，做了也无效」）；**§16.4** 补 **`selectedText` 取值策略**：常量表如实反映 `selectedText === text`，**单测要断言这一点** → 将来 pages.json 出现 `text ≠ selectedText` 时测试失败并提醒同步；**§6.13.6** 开头标注 🔒 **Phase 3 硬性交付项**（team-lead 已确认），并说明「Phase 3 直接照此实现，**无需在 Phase 2 另立接口**」 |
> | **v1.11** | **§6 约束 16 整体改写** —— 从「同步负担」升级为「**现存顺序错位 Bug + 架构收敛**」：新增 16.1（逐位核对表 + 触发链路 + 4 条后果 + 图标类名核对）、16.2（4 处副本表）、16.3（为何不能运行时读 `pages.json`）、16.4（唯一真值源 `src/constants/tabBar.js` / `TAB_BAR_ITEMS` 结构）、16.5（静态校验单测 + 共享常量总门禁）、16.6（**P0 止血 / P1 收敛**分离）、16.7（与约束 15 / §3 锚点 / §4.4.2 的耦合）；**§4.11** 共享常量清单扩到 **3 项** + `src/constants/` 总门禁建议；**§6.13.6** 数据依赖速查表补「tabBar 元数据」行（标注现存 Bug + 真实实现方式）；**§8.1** 冲突 A/B 更新为最终裁决措辞 + 附带裁定（P0 先行）；**§8** 新增「**O13 优先级高于 O12**」及其理由（品种不一致比色条不一致更易被用户发现） |
> | **v1.10** | **并入 team-lead 2026-11-19 裁决**：① **§4.4.2** —— `color_hint` 描述由「仅供参考，已错位」改为「**已按语义线修正**」+ 补 **DB 修正动作（4 行 UPDATE SQL，零代码引用点迁移）** + 补 team-lead 提供并已复核的**修正必要性实测**（4 值作 chip 文字色 on `#F0ECE6` = 2.66/1.40/11.95/1.74 三条不达标；ATLAS 原值作色条 on 深底 `#1A1816` 仅 **1.26:1 物理不可见**；修正后 4 值在深底 8.66/10.74/5.65/9.36 全部达标）；② **§5.5 + §6 约束 4** —— 补 **O7 pet `enable-flex`** 缓解项（横向部分现状已具备，**验收必须在 Android 真机**）；③ **§5.4.2** —— 功能清单**去掉 8 个锁图标**（O8），改为点击弹 **L2 modal「登录后即可查看{该功能}」**，**「设置」里的主题/语言绝不拦截**；④ 共享常量文件名统一为 `src/constants/tabPages.js`（team-lead 指定）—— ⚠️ **v1.11 已改回 `src/constants/tabBar.js`**（需同时承载 codepoint + icon class），**以 §6 约束 16.4 为准**；⑤ **§8.1 保留并标注已裁决**（记录决策过程与回退点，供后人追溯） |
> | **v1.9** | **§6 新增约束 16**（tabBar 元数据 **3 份真相**：`pages.json` `tabBar.list` / `home.vue:146` `TAB_BAR_PAGES` / `theme.js:75` `TAB_BAR_ICON_FALLBACK`（注释写「顺序必须与 pages.json 一致」），+ `category.vue:148` 第 4 处路径数组 → 统一收敛为 `src/constants/tabBar.js` **唯一真值源**（v1.11 最终命名） + **一致性单测**（说明为何不能用运行时读 `pages.json`：APP 端 `__uniConfig` 不保证挂 globalThis，`theme.js` 已有先例注释））；**§5.6.1 新增**发布页「草稿图片丢失」提示视觉登记（沿用社区页 inline 错误条形态，以 Phase 2 §3.5.4 为准）；**§4.11 新增「共享常量清单」** —— 把「产品线映射」「tabBar 元数据」2 处同类问题合并为「抽共享常量 + 一致性校验」统一解法 |
> | **v1.8** | **§4.4.2** 补 **E5 证据**（admin 后台无 `mo_brand_ip` 管理页 / 无 `color_hint` 配置入口；该列无 CHECK、无索引 → **"运营在后台配的就是权威"前提不成立**）+ 补「即使以 DB 为准也必须先改 2 条数据（HOME 无色 / ATLAS 是正文色不可见）」+ 补「**修数据，不迁就数据**」建议；**§8 新增 8.1 规范冲突待裁记录**（冲突 A 色条权威源 / 冲突 B slogan 斜体，含两方论据与回退成本）；§5.6 对齐 Phase 2 v1.3 的 **67 态**（与 ux-designer 双向 diff 复核零差异） |

---

## 0. 结论摘要（TL;DR）

| 项 | 结论 |
|---|---|
| 视觉系统命名 | **Linen & Brass（亚麻与黄铜）** |
| 视觉一句话 | 暖亚麻画布上的「编辑部橱窗」：大面积留白 + 细金线 + 一张主视觉 + 编辑式排印 |
| 5 页视觉人格 | home = 橱窗陈列 / community = 杂志信息流 / messages = 收件箱 / user = 账户仪表盘 / pet = 沉浸场景（不改，仅对齐 token） |
| 每页唯一焦点 | home = Hero 主视觉；community = 首条帖子封面；messages = 未读状态；user = 会员卡；pet = 宠物形象 |
| 必修 Bug | user.vue 会员卡的写死 iOS 蓝渐变 + 蓝色阴影 → 换**暗古铜渐变**（v1.2：亮黄铜配白字仅 2.17:1，已改暗古铜）；community FAB 蓝色阴影 → 换品牌阴影；messages `#ff4d4f` ×4 → `--color-danger`、左滑删除按钮 ×1 → `--color-danger-strong`；messages `var(--color-primary, #18b367)` 绿色兜底 → `#dbc98a` |
| 新增 token | 5 组共 **48 个**（品牌强调梯度 / 分级表面 / 图片占位与遮罩 / chip / 分割线两级 / 骨架屏 等），全部沿用 `--color-*` `--radius-*` `--space-*` `--shadow-*` 命名族，不推翻现有体系 |
| 排印前提 | `Playfair Display` **方案 A 已定案**（打进 `src/static/fonts/` + `font-display: swap` + 完整 fallback 链 + woff2 子集化 ≤120KB），规范见 §6 约束 6 |

---

## 1. 现状诊断（基于代码事实）

### 1.1 已确认的事实清单

| # | 事实 | 代码位置 | 视觉后果 |
|---|---|---|---|
| F1 | 5 页全部为「白底卡片 + 1rpx 边框 + 24rpx 圆角」同质堆叠 | `common.scss` `.card`；`user.vue` `.card`；`community.vue` `.post-card`；`messages.vue` `.notice-card`/`.conv-list` | 页面之间没有性格，像同一个模板换内容 |
| F2 | 首页视觉重心在中间 banner，金刚区被夹在中间，首尾无收尾 | `home.vue` `.navbar` → `.kingkong` → `.banner` → `.recommend` | 缺少品牌氛围（顶部）与收尾（底部） |
| F3 | 社区正文 `font-weight: 600`、30rpx 全粗 | `community.vue` `.post-content` | 正文没有层级，一屏全是重音 |
| F4 | 社区 tab 指示器为 48rpx 下划线短线 | `community.vue` `.tab-indicator` | 指示器弱，"当前期号"不明确 |
| F5 | 消息页「通知 4 宫格」与「私信列表」是两个割裂白卡 | `messages.vue` `.notice-card` / `.conv-list` | 无整体容器感，像两个模块拼贴 |
| F6 | 会员卡写死 iOS 蓝 | `user.vue` `.vip-bg` `linear-gradient(135deg, var(--color-primary, #007aff) 0%, #004fad 100%)` + `box-shadow: 0 8rpx 32rpx rgba(0,122,255, 0.3)` | 与 Sand Gold 品牌色正面冲突，是明显 bug |
| F7 | 金色被当作按钮底 + 白字 | `community.vue` `.fab`（`color:#fff`）、`messages.vue` `.login-btn`（`#fff`）、`messages.vue` `.conv-avatar` | 白字在 `#dbc98a` 上对比度仅 **1.65:1**，远低于 4.5:1 |
| F8 | `#ff4d4f` 硬编码 5 处 | `messages.vue` `.navbar-badge` `.notice-dot` `.section-badge` `.conv-unread` `.conv-swipe-btn` | 红点把"高端感"拉回社交 App 语汇，且不随主题 |
| F9 | `var(--color-primary, #18b367)` 绿色兜底 | `messages.vue` `.login-btn` `.section-action` `.empty-btn` `.conv-avatar` `.empty-btn-text` | 变量缺失时会闪出**绿色**，与品牌完全无关 |
| F10 | 骨架屏渐变写死浅灰 | `common.scss` `.skeleton` `#f0f0f0 → #f8f8f8` | 深色模式下持续闪白 |
| F11 | 订单图标是 emoji，但模板用 `$luc()` 渲染 | `user.vue` `data.orderTypes[].icon = '💳' '🛒' '📦' '✅'` | 类名变成 `luc-💳`，**图标渲染不出来** |
| F12 | 社区图片占位用 `data:image/svg+xml` | `community.vue` `resolveImageUrl` / `onImageError` | APP 端 SVG data-uri 不可靠，常显空白 |
| F13 | `Playfair Display` 未打包 | `common.scss` 仅 font-family 引用，全项目无 `@font-face` / woff2 / 字体 CDN | "编辑式排印"目前是**空头承诺**，实际渲染 fallback serif → **已定案按方案 A 修复，见 §6 约束 6** |
| F14 | pet 页自建本地 token 旁路 | `pet.vue` `--hub-card:#ffffff` `--hub-line:#e5e5ea` `--hub-ok:#34c759` `--hub-err:#ff3b30` | 不随主题，破坏 5 页一致性 |
| **F15** | **会员卡未登录时整块 DOM 不渲染** | `user.vue:38` `v-if="userStore.isLoggedIn"` | ⚠️ **重要修正（v1.4，据 baseline 截图实测）**：iOS 蓝会员卡在**未登录的基线截图里根本不存在** —— 它不能作为"现状观感"的证据。真正的问题是**更严重的那个**：未登录态下**会员体系完全不可感知**（页面上只剩功能清单里一条 `Membership` 文字项）。→ §5.4.2 未登录态规格已强化 |
| **F16** | **iOS 蓝 token 的第二处泄漏** | `src/pages/user/mission-center.vue:455` | 同款 `var(--color-primary, #007aff)` 蓝色渐变。只改 `user.vue` 会漏 → §6 约束 8 已补入清单 |
| **F17** | **H5 的 `page` 标题与 tabBar 文案走系统字体栈** | uni-app H5 运行时渲染 | **不受 CSS `font-family` token 影响** —— 改 `--font-display` / `--font-sans` 不会改变这两处 → §6 新增约束 14 |
| **F18** | **社区话题栏第 4 个 chip 被右边缘硬裁** | `community.vue` `.topic-bar` 横向 scroll-view | 裁切处无渐隐提示，视觉上像**渲染错误**而非"可横滑"暗示 → §5.2.2 ③ 已补右侧渐隐遮罩规格 |
| **F19** | **DB `mo_brand_ip.color_hint` 与语义产品线错位 3/4** | `migration-combined-final.sql:3435` 建表注释 `主色提示：#DBC98A`（示例值 = LUNA 实际值）；INSERT 四值 `#B38A5A`/`#DBC98A`/`#2E2B29`/`#ABB9AD`，**独缺 HOME `#D9B4B0`**；ATLAS 填了 Charcoal 正文色 | ⚠️ 该列**无任何运行时消费点**（Java + admin 前端 grep 均无命中）→ 不构成线上 bug，但会误导选色 → §4.4.2 已加「仅供参考」列 + 4 条非权威证据 |
| **F21** | **`switchTab` 不能带 query → tabBar 页登录回跳不能用 URL 传参** | `@dcloudio/types/.../uni.d.ts:5391`「需要跳转的 tabBar 页面的路径，路径后不能带参数」 | 平台硬限制（现状 `uni.switchTab` 调用点**暂无违规**，但 Phase 4 做登录回跳时会踩）→ §6 新增约束 15（storage + action 方案） |
| **F20** | **IP 配色存在三套体系 + IP 元数据两套** | ① DB `color_hint` ② 前端 4 条产品线色 ③ `pet-hub-3d.vue` 的 iOS 系统色（`#007aff`/`#34c759`/`#ff9500`/`#af52de`）；且 IP 品种在 DB（灵缇犬/英短金渐层/杜宾犬/缅因猫）与前端（金毛/布偶/哈士奇/柯基）**完全不符** | 3D 场景用差异化色是合理需求（4 角色需可区分），但必须 token 化 + IP 元数据收敛 → §6 约束 8 已补 |

### 1.2 诊断结论

问题不是「某个控件不好看」，而是**缺少一个能同时约束 5 页的视觉语法**。现有 token（Sand Gold / Linen / Charcoal + 4 产品线色 + 4 档圆角 + 6 档间距 + 10 档阴影）本身是好的、可延展的；缺的是三样东西：

1. **表面层级语法的缺席** —— 只有「卡片」一种表面，没有「浮起 / 下沉 / 通栏 / 反色」的分级，所以所有区块只能用同一种白卡表达。
2. **排印语法的缺席** —— 只有「一个 Inter 字号阶」，没有「展示体 / 正文体 / 小型大写标签」三层角色，所以每页都在用字号+粗细硬分层级。
3. **状态语法的缺席** —— 空 / 加载 / 错误 / 未登录四态没有任何规范，5 页统一退化成「一句灰字」。

本规范的 C/D 两节就是围绕这三条补齐。

---

## 2. A. 品牌视觉方向

### 2.1 一句话定位

> **MOYUYO 是一间「把宠物用品当作生活方式策展」的线上买手店橱窗。**
> 视觉上它像一本暖色调的家居生活杂志的线上版：大面积亚麻底色、克制的黄铜金线、一张能撑住呼吸感的主视觉、以及像图注一样轻盈的文字。

### 2.2 为什么这个方向适配「高端宠物生活方式品牌」（论证）

1. **宠物用品本身是低差异品类。** 同款猫窝/宠物花瓶在电商上价格带高度趋同，靠"更多装饰"无法建立高端感，只能靠**减法与秩序**建立信任——这正是 editorial（编辑式）视觉的语言：留白当构图、字距当层级、细线当分隔。已有色板本身已在暗示这件事：Sand Gold 是**低彩度、带灰的哑金**（#dbc98a，H≈48°，S≈35%），不是金属反光的金箔；Linen 是纸感暖白，不是冷调 #fff。**这套色板天生属于"印刷品"而不是"数码 UI"**，所以正确的方向是让它更像出版物，而不是更像 App。

2. **暖中性而非纯白，是材质隐喻。** `#f6f2ee` 对比 `#ffffff` 只差 3 个明度级，但传达的是"纸/亚麻/陶土"而非"屏幕/塑料"。对宠物品牌尤其有效：宠物本身是柔软的、有体温的，界面越接近生活材质、越远离冷白卡片流，感知越贵。pet tab 已经在用 `rgba(255,255,255,.2)` 的玻璃拟态叠加场景图，**整个 App 应当统一为"材质叠加"的语言**：亚麻底 + 玻璃层 + 黄铜细节。

3. **"策展"需要焦点稀缺。** 高端感的核心是**减法**：一屏只有一个主角、一条主线，其余全部退让。目前首页把 banner 夹在中间、金刚区在旁、社区一屏全是 600 字重粗体——所有元素都在喊，等于没有人听到。editorial 的答案是明确的层级：**一张大图 + 一个标题 + 一行 kicker + 大量留白**。这与"高端宠物生活方式品牌"的营销逻辑一致（讲故事、给品味、不促销轰炸）刚好同构。

4. **黄铜金必须被"用得少"。** 现状把金色当按钮底 + 白字，是最廉价的一种用法。正确用法是把金色当**金属细节**：1rpx 的分隔线、40rpx 的指示器、会员卡的一整块渐变、章节号。**金色的稀缺性就是它的高级感来源**（与 Hermes 的橙、Celine 的金同理）。因此本规范明确："金色只允许出现在 5 种位置"（见 §2.3 原则 2）。

5. **留白节奏 = 呼吸感 = 溢价感。** 高端宠物品牌卖的不只是产品，还有"这个空间值得待着"的感受。界面留白 = 品牌给用户心理上的空间。这条论证也直接解释了为什么本规范要求**用留白与 hairline 替代边框**——边框是"把东西框起来"（防御性），留白是"把东西摆开"（策展性）。

### 2.3 设计原则（5 条，可作为实现自查清单）

| # | 原则 | 具体判定标准 | 对应解决的现状问题 |
|---|---|---|---|
| **P1** | **一屏一主角** | 每页有且仅有 1 个高强度元素（大图 / 金色大面积 / 44rpx+ 展示体数字）。其余全部降到二级（≤32rpx、hairline、无阴影）。当两个元素都想当主角时，**删掉一个**。 | F1 F3 F7 |
| **P2** | **金色点到为止** | 金色只允许出现在 5 处：① 唯一主 CTA 的底色 ② 细金线（1–2rpx）③ tab / 分区指示器（≤40rpx）④ 会员卡整块渐变 ⑤ 徽章 / 计数点。**禁止**金色正文、**禁止**金色大色块（会员卡除外）。 | F6 F7 |
| **P3** | **层级靠排印，不靠边框** | 分区手段优先级：留白 > hairline 分隔线 > 小型大写 kicker > 边框 > 阴影。**禁止**用「白卡 + 1rpx 边框 + 阴影」作为默认容器。 | F1 F4 |
| **P4** | **图片是主角，文字是图注** | 商品卡/帖子卡中，图区面积 ≥ 文字区面积的 2 倍；图片容器比例只在 **1:1 / 4:3 / 3:4 / 16:9** 四档中选；图片未加载时用暖灰渐变占位，**不用冷灰**、**不用 SVG data-uri**。 | F12 |
| **P5** | **状态是一等公民** | 空 / 加载 / 错误 / 未登录四态必须在每页存在，且与常态共用同一套 token；空态必须有「图形 + 一句解释 + 一个动作」三件套，**禁止**只有一句灰字。 | 5 页通用缺口 |

### 2.4 视觉家族命名

**Linen & Brass（亚麻与黄铜）** — 对应品牌色板里的 Linen `#f6f2ee`（画布）与 Antique Brass `#b38a5a` / Sand Gold `#dbc98a`（细节金属）。后续新增页面与组件统一以此家族命名（token 前缀 `--brand-*` / `--chip-*` / `--surface-*`），便于未来整体换肤。

---

## 3. B. 五个页面的「视觉人格」

| Tab | 页面 | 气质关键词 | 视觉人格（2–3 句） | **唯一视觉主角** |
|---|---|---|---|---|
| 1 | `pet` 宠物 | 沉浸 / 陪伴 / 剧场 | 唯一以「场景」而非「列表」存在的页面：宠物形象站在自己的小空间里，上浮层是玻璃质感的信息牌。它是整个 App 的情绪高点，也是其他 4 页的"品牌封面"。**本次不改结构**，只做 token 收敛。 | 宠物形象（3D / 装扮图） |
| 2 | `home` 首页 | 橱窗 / 陈列 / 呼吸 | 像精品店入口的橱窗陈列：一件大主视觉占住首屏，下面是分区的"展签"（kicker + 标题 + 看全部），商品像展品一样安静地排在留白里。不做促销轰炸，不做密集信息流。 | **Hero 主视觉**（16:9 大图 + 展示体标题） |
| 3 | `community` 社区 | 社交墙 / 杂志 / UGC | 编辑式社交墙：首条帖子是"头条"（大图 + 标题式图注），其余是"碎片"（左图右文）。正文回到 400 字重 + 1.7 行高，读起来像读杂志短文而不是刷快讯。 | **首条帖子的封面大图** |
| 4 | `messages` 消息 | 收件箱 / 效率 / 扫描 | 工具页。信息密度最高、装饰最少，一屏要能被"扫读"完。整页只有一件事在喊：**未读**。通知与私信合并在一个容器里，像一个真的收件箱。 | **未读状态**（金色竖条 + 加粗标题） |
| 5 | `user` 我的 | 账户 / 仪表盘 / 身份 | 数据优先的账户面板。头部是身份，中间是会员身份卡（暗古铜渐变，唯一主角），下面依次是三组数据带（钱包 / 社交 / 订单）与分组后的功能清单。结构感强于装饰感。 | **会员卡**（暗古铜渐变 + 展示体**等级名**） |

**跨页一致性锚点（保证 5 页仍是一个产品）**：

| 锚点 | 规则 |
|---|---|
| 顶栏 | 5 页统一「sticky + veil 磨砂 + hairline 底边」，高度 96rpx + 状态栏安全区。**标题用 `--font-sans` + 500/600 字重**（v1.3 修正 —— Playfair Display **不含中文字形**，5 页顶栏标题多为中文，用展示体会 fallback serif，不如统一用无衬线） |
| kicker | 每页首个区块标题上方 12rpx 小型大写标签（`--tracking-kicker`），全 App 统一 |
| **展示体** | **只有 3 类「Latin 短文本」位置用展示体**（v1.3/v1.4 收敛，原为 4 类含中文顶栏标题）：① **品牌 wordmark `MOYUYO`**（28rpx，纯拉丁，安全）② **Hero 标题**（44rpx）③ **会员卡等级名·英文**（56rpx，如 `Platinum` / `Black`）。**所有中文标题（页面标题 / 区块标题 / 卡片标题）一律走 `--font-sans` + 500/600** —— 理由：Playfair Display **不含中文字形**，中文用展示体必然 fallback serif，层级反而不如无衬线稳定 |
| 金色 | 每页最多 2 处金色（P2） |
| 主 CTA | 每页最多 1 个金色实心按钮，且不在底栏 |

---

## 4. C. 共享设计系统升级

> **原则**：只新增、不推翻。全部沿用现有 `--color-*` / `--radius-*` / `--space-*` / `--shadow-*` / `--font-size-*` 命名族与取值习惯。
> **落地位置**：新增变量写入 `src/styles/variables.scss` 的 `:root` 块；深色值同时写入 `@media (prefers-color-scheme: dark) :root:not(.theme-light)` 与 `.theme-dark` 两个块（与现有文件结构一致）。

### 4.1 品牌强调色梯度（新增 8 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--brand-gradient-vip` | `linear-gradient(135deg, #7E6B36 0%, #6B5F3A 50%, #554A26 100%)` | `linear-gradient(135deg, #4A432C 0%, #3A341F 55%, #2A2619 100%)` | **会员卡主视觉**（替代 iOS 蓝）。**暗古铜 + 米白字**，全段白字对比度 ≥4.66:1（见 §4.10 与 §5.4.2 修正说明）；深色下换成暗黄铜，避免在黑底上形成亮斑 |
| `--brand-gradient-hero` | `linear-gradient(135deg, #E6D59A 0%, #C9B47A 100%)` | `linear-gradient(135deg, #5A5030 0%, #3A3423 100%)` | 无图兜底 / 空态品牌色块（不依赖外链） |
| `--brand-gradient-ink` | `linear-gradient(180deg, rgba(46,43,41,0) 0%, rgba(46,43,41,0.18) 42%, rgba(46,43,41,0.62) 100%)` | `linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.30) 42%, rgba(0,0,0,0.72) 100%)` | 图片底部渐变遮罩（替代现在的黑色半透明方块） |
| `--brand-scrim-flat` | `rgba(46,43,41,0.52)` | `rgba(0,0,0,0.62)` | 图片上平铺文字遮罩 |
| `--accent-soft` | `rgba(219,201,138,0.18)` | `rgba(201,180,122,0.16)` | 金色弱填充：chip 底、图标底圆（`social-grid` 现有硬编码 `rgba(219,201,138,0.18)` 归位到此） |
| `--accent-strong` | `rgba(219,201,138,0.34)` | `rgba(201,180,122,0.28)` | 金色选中态填充 |
| `--text-on-accent` | `#2E2B29` | `#1A1816` | 金色底 / 浅色底上的文字色（**替代全站 `#fff` on 金色**） |
| `--text-inverse` | `#F6F2EE` | `#F6F2EE` | 深色面 / 图片上的文字 |

### 4.2 分级表面（新增 4 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--surface-elevated` | `#FFFFFF` | `#322E2A` | **浮起**表面：sticky 顶栏、消息会话容器、浮层 sheet。深色下比页面底（`#1A1816`）亮 2 级，用明度差而非阴影分层 |
| `--surface-sunken` | `#F0ECE6` | `#211E1B` | **下沉**表面：图片占位底、进度轨道、骨架屏底、区块槽 |
| `--surface-mute` | `#FAF8F5` | `#262320` | 极弱表面：hover 底、区块背景微差（比 `--color-fill` 更弱） |
| `--surface-inverse` | `#2E2B29` | `#F6F2EE` | 反色表面：会员卡深端、深色 hero 文字块 |

> **用法决策**：`--color-surface`（白 / `#2A2724`）保留为**默认卡片面**；`--surface-elevated` 只给"需要浮起语义"的容器；`--surface-sunken` 只给"洞"。这样浅色下的三级差（白 → 亚麻 → 更暗亚麻）刚好成立。

### 4.3 图片占位与渐变遮罩（新增 7 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--img-placeholder` | `linear-gradient(135deg, #ECE6DD 0%, #E3DCD1 100%)` | `linear-gradient(135deg, #2C2926 0%, #232120 100%)` | 图片未加载 / 无图兜底的**暖灰渐变**（禁止冷灰 `#f2f2f7`） |
| `--img-placeholder-flat` | `#ECE6DD` | `#2C2926` | 单色占位（头像、商品图底） |
| `--img-ratio-square` | `100%` | `100%` | 1:1 —— 用 `padding-top: 100%` 撑高 |
| `--img-ratio-landscape` | `75%` | `75%` | 4:3 —— `padding-top: 75%` |
| `--img-ratio-portrait` | `133%` | `133%` | 3:4 —— `padding-top: 133%` |
| `--img-ratio-banner` | `56.25%` | `56.25%` | 16:9 —— `padding-top: 56.25%` 或 `calc((100vw - 64rpx) * 0.5625)` |
| `--brand-gradient-ink` | 见 4.1 | 见 4.1 | 复用：图片底部文字渐变 |

### 4.4 标签 chip（新增 6 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--chip-bg` | `#F0ECE6` | `#322E2A` | 中性 chip 底：搜索框、话题标签、图标底圆、分类标签 |
| `--chip-bg-strong` | `#E6E0D6` | `#3B3630` | 强 chip 底：选中态、强调标签 |
| `--chip-text` | `#6E6962` | `#B8B0A5` | chip 文字（= `--color-text-secondary`） |
| `--chip-text-strong` | `#2E2B29` | `#F6F2EE` | chip 强文字 |
| `--chip-accent-bg` | `#F5EFDD` | `#383122` | 品牌 chip 底（黄金调暖底，不是半透明叠白） |
| `--chip-accent-text` | `#6B5F3A` | `#D9C68F` | 品牌 chip 文字（浅色端用 `#6B5F3A` 保证 5.50:1，**不用 `#B8A66B`**） |

#### 4.4.1 产品线色（Care / Gear / Play / Home）的使用规则（**v1.2 裁定**）

**实测结论**（15% 产品线色叠 Linen `#f6f2ee` 作底，产品线色作文字）：

| chip | 文字色 | 实底色 | 对比度 | 判定 |
|---|---|---|---|---|
| `.tag-care` | `#dbc98a` | `#f2ecdf` | **1.40:1** | ❌ 严重不达标 |
| `.tag-gear` | `#abb9ad` | `#ebe9e4` | **1.68:1** | ❌ |
| `.tag-play` | `#b38a5a` | `#ece2d8` | **2.45:1** | ❌ |
| `.tag-home` | `#d9b4b0` | `#f2e9e5` | **1.58:1** | ❌ |

**根本结论**：这不是"某个色值调错了"，而是**产品线色的角色用错了**。Care/Gear/Play/Home 四色都是**低彩度淡色**（HSL 饱和度 14–32%，明度 60–67%），它们是**物体色 / 品牌标记色**，不是**文字色**。无论怎么调深，都会在"4 个低彩度色 + 暖亚麻底"的组合里失去品牌识别度。

> **裁定（产品线色三用法则）**
> | 场景 | 产品线色可否使用 | 说明 |
> |---|---|---|
> | **浅色模式 · 文字** | ❌ **永不可用** | 4 色实测 1.40–2.45:1，全部不达标；且违反 §4.10 硬规则 1 |
> | **浅色模式 · 图形**（色条 / 色块 / 图标底 / 描边） | ✅ 可用 | 作为实体色块，靠**面积**而非对比度被感知 |
> | **深色模式 · 文字** | ✅ **可用** | 实测 on `#2A2724`：care **9.00:1** / gear **7.27:1** / play **4.74:1** / home **7.85:1** —— 全部 ≥4.5:1 |
> | **深色模式 · 图形** | ✅ 可用 | 同浅色 |

**产品线 chip 最终规格（v1.2 替代 `.tag-*` 现状）**

| 项 | 规格 |
|---|---|
| 底色 | `--chip-bg`（#F0ECE6 / #322E2A）—— **不再是"产品线色 15% 叠加"** |
| 文字 | `--chip-text-strong`（#2E2B29 / #F6F2EE），对比度 **11.95:1**（浅）/ **12.09:1**（深） |
| 产品线识别 | **左侧 8rpx 宽圆角色条**（满高，圆角 `--radius-xs` 6rpx）+ 产品线**名称文字**（如 `CARE 洗护`） |
| 色条颜色 | `var(--color-care)` / `var(--color-gear)` / `var(--color-play)` / `var(--color-home)` 原色（图形用途，不受文字对比度约束） |
| 色条宽度 | **8rpx**（⚠️ 不是 4rpx —— 在 40rpx 高的 chip 上 4rpx 实心色条几乎不可见，等于没有色条） |
| chip 高度 / 字号 | 40rpx / 20rpx；文字间距：色条↔文字 8rpx |
| **WCAG 1.4.1 豁免依据** | chip 内**同时含产品线名称文字**，颜色不是唯一传达手段 → 色条的弱对比不构成违规（1.4.11 非文本对比度针对的是"识别所必需的视觉信息"，此处信息已由文字承载） |
| **深色模式** | 底 `--chip-bg`(#322E2A) + 字 `--chip-text-strong`(#F6F2EE) + 色条用**产品线色原色**（深底上 4.74–9.00:1，可见度优于浅色端） |

**`.tag-care` / `.tag-gear` / `.tag-play` / `.tag-home` 的改造方式**（`common.scss`）

> **`.tag-*` 类名现状（v1.5 源码核实）** —— 这里有一个**必须先解决的命名断层**，否则改 `.tag-care` 系列**修不到实际生效的类名**：
>
> | 位置 | 写法 | 实际生成的类名 | 与 `common.scss` 是否匹配 |
> |---|---|---|---|
> | `tabbar/category.vue:79` | `:class="`tag-${p.ip.toLowerCase()}`"` | **`tag-milo` / `tag-luna` / `tag-atlas` / `tag-olive`** | ❌ **`common.scss` 无这 4 个类名 → 分类页商品卡的产品线徽标当前完全没有任何配色**（只剩 `.product-ip` 自己的边框 + tertiary 字） |
> | `goods/detail.vue` 等 | `.tag-care` / `.tag-gear` / `.tag-play` / `.tag-home` | 语义类名 | ✅ 匹配（但映射错，见 §4.4.2） |
>
> **因此 v1.5 裁定：`.tag-*` 改造要同时覆盖两套别名** —— `common.scss` 里把 `tag-milo/luna/atlas/olive` 与 `tag-care/gear/play/home` **都**定义成同一套 chip 规格（共用一个 mixin 或 `@extend`）。这样：
> - 分类页的 `tag-milo` **立刻获得配色**（修掉"徽标无色"这个既有 bug）；
> - 现有引用 `.tag-care` 系列的地方**不受影响**（不破坏既有引用）；
> - 两套别名指向同一视觉规格，不产生第二套真相。
>
> **注意**：`.tag-*` 的**语义类名（care/gear/play/home）与 IP 类名（milo/luna/atlas/olive）不是一一对应的字面关系** —— 权威映射见 §4.4.2。**色条颜色必须按语义（Care/Gear/Play/Home）取，不能按 IP 名取。**

**实现依赖（v1.5 源码核实后更新）**

| 使用位置 | 数据来源 | 状态 |
|---|---|---|
| 商品卡 / 商品详情 | `product.brandIpId`（数字 1–4，映射见 §4.4.2）—— `goods/detail.vue` 的 `brandLine` computed 已在用 | ✅ **已有，可落地**（但映射表有 bug，见 §4.4.2） |
| 首页产品线索引 | 直接写死 4 条映射（与 §4.4.2 同源） | ✅ **已有，可落地** |
| 分类页 | 分类接口按产品线分组时复用同一映射 | ✅ 可落地（**注意 `tag-milo` 类名缺失问题**） |
| **社区帖子 chip** | `mo_community_post.ip_code`（**VARCHAR(32)**，存 `'MILO'/'LUNA'/'ATLAS'/'OLIVE'`，**不是数字 ID**） | ⚠️ **v1.5 更正（推翻 v1.4 结论）**：字段**已在库且有索引**（`migration-combined-final.sql:3409` + `KEY idx_ip_code`），`CommunityPostEntity.java:36` 有 `private String ipCode;`，发布时由 `CommunityServiceImpl.java:192` **自动从宠物档案继承** `preferredIp_code` → **是真数据，不是假数据**。唯一缺口：`CommunityPostVO`（17 字段）**未透出 `ipCode`** → **后端 1 行改动**即可 |

> **v1.5 结论修正**：v1.4 写的「社区帖子无产品线字段，需后端新增字段，社区页不出现该 chip」**不成立** —— 字段名是 `ip_code`（不是 `brand_ip_id`），且早已在库。正确表述是：**后端只需在 `CommunityPostVO` 补 1 个字段透出 `ipCode`，Phase 3 原型即可呈现社区产品线 chip（用真实 IP 数据映射到语义色条），不是假数据。**
> ⚠️ 但**字段名与语义映射是两回事**：库里存的是 IP 名（`MILO`），chip 色条要的是语义色（Care/Gear/Play/Home）→ 必须经 §4.4.2 的映射表转换。

#### 4.4.2 产品线 ID 映射的**唯一真值源**（v1.5 新增 · 解决双命名冲突）

**三层对照表**（`id ↔ ip_code` 的数字/名称对应来自 DB + `goods/list.vue` 三处一致；**语义产品线为推断**，见下方说明）：

| `brandIpId`（数字） | `ip_code`（字符串） | **语义产品线**（推断，待产品侧确认 ⚠️） | **色条 / token** | 中文名 | DB `color_hint` 原值 | DB `color_hint` **修正后** |
|---|---|---|---|---|---|---|
| **1** | `MILO` | **GEAR** | `--color-gear` #abb9ad | 出行 | `#B38A5A` ❌ = `--color-play` 的值 | **`#ABB9AD`** ✅ |
| **2** | `LUNA` | **CARE** | `--color-care` #dbc98a | 洗护 | `#DBC98A` ✅ 唯一吻合的一条 | **`#DBC98A`** ✅ 不变 |
| **3** | `ATLAS` | **PLAY** | `--color-play` #b38a5a | 玩具 | `#2E2B29` ❌ = Charcoal 正文色 | **`#B38A5A`** ✅ |
| **4** | `OLIVE` | **HOME** | `--color-home` #d9b4b0 | 家居 | `#ABB9AD` ❌ = `--color-gear` 的值 | **`#D9B4B0`** ✅ |

> ✅ **已裁决（team-lead 2026-11-19，采纳方案 ③）**：取色链路保持不变（前端 token 权威），**DB 侧把 `color_hint` 修正为与语义线一致** —— 见下方「DB 修正动作」。修正后 4 个值作色条在**深色底 `#1A1816`** 上分别为 **8.66 / 10.74 / 5.65 / 9.36 : 1，全部达标**。

> ⚠️ **注意 1 与 2 的顺序**：**1 = MILO = GEAR**，**2 = LUNA = CARE**。

**`color_hint` 为何不是权威（v1.6 核实结论 · 四条证据）**

| # | 证据 | 推论 |
|---|---|---|
| **E1** | 建表注释写的是 `color_hint VARCHAR(16) NULL COMMENT '主色提示：#DBC98A'` —— **注释里的示例值恰好等于 LUNA(2) 的实际值** | 这列是「填了个示例值」的自由填充列，**注释从未声明它对应语义产品线** |
| **E2** | ATLAS(3) 填 `#2E2B29` = **Charcoal = `--color-text`（正文色）** | **正文色不可能是"品牌主色"** —— 这条直接证明该列是随手填的 |
| **E3** | 4 个值取自前端色板的 `play` / `care` / `text` / `gear`，**独缺 `--color-home` #d9b4b0**，且顺序与语义线错位 3/4 | 若是按语义线填的，HOME 必须出现且顺序不会错 |
| **E4** | `color_hint` / `colorHint` 在 **Java 后端与 admin 前端均 grep 无命中** | 纯数据字段，**无任何运行时消费点** → 不构成线上 bug，但会误导 Phase 4 选色 |
| **E5** | **admin 后台没有 `mo_brand_ip` 管理页，也没有 `color_hint` 的任何配置入口** —— `find moyuyo-admin/src -iname "*BrandIp*"` 无输出；`grep colorHint\|color_hint moyuyo-admin/src` 无输出（`brandIp` 仅有 `views/ProductEdit.vue` 的商品表外键）。且该列为 `VARCHAR(16) NULL`、**无 CHECK 约束、无索引**（该表只有 `PRIMARY KEY(id)` + `UNIQUE KEY uk_code(code)`） | **从未被约束、从未被维护、从未被使用**。`color_hint` 只有迁移脚本 `migration-combined-final.sql:3476` 的 4 行 INSERT 初始值，建库后再无任何流程改写 → **"运营在后台配的就是权威"这一前提不成立** |

> **裁定（已由 team-lead 2026-11-19 裁决确认）：`color_hint` 不是权威，色条取色一律以语义线（前端 `variables.scss` 的 4 条产品线色）为准；DB 侧按下方「DB 修正动作」把该列改对。**

**即使决定「以 DB 为准」，DB 侧也必须先改这 2 条数据才可用**（否则该方案不成立）：

| IP | DB 现值 | 必须先改的原因 |
|---|---|---|
| **OLIVE (HOME)** | `#ABB9AD` | **HOME 在 DB 里根本取不到色** —— 前端 `--color-home: #d9b4b0`（Dusty Rose）在 DB 的 4 条 `color_hint` 里**不存在** |
| **ATLAS (PLAY)** | `#2E2B29` | Charcoal = 正文色，作 chip 色条在浅底上对比度 **1.05:1**，**物理上不可见** |

→ 即「以 DB 为准」的第一步仍是改数据，改完即等于「语义线为准」。**推荐做法是「修数据，不迁就数据」**：取色链路保持不变（前端 token 权威），把 `color_hint` 的 4 行改成与语义线一致 —— 破坏性变更仅 **1 张表 4 行数据**，**零代码引用点迁移**（因该列无消费点）。

**✅ DB 修正动作（已裁决落地，4 行 UPDATE）**

```sql
-- mo_brand_ip.color_hint 修正为与语义线一致（Phase 4 后端协作项）
UPDATE mo_brand_ip SET color_hint = '#ABB9AD' WHERE code = 'MILO';   -- 原 #B38A5A（play 的值）
UPDATE mo_brand_ip SET color_hint = '#DBC98A' WHERE code = 'LUNA';   -- 原值本就正确，不变
UPDATE mo_brand_ip SET color_hint = '#B38A5A' WHERE code = 'ATLAS';  -- 原 #2E2B29（Charcoal 正文色）
UPDATE mo_brand_ip SET color_hint = '#D9B4B0' WHERE code = 'OLIVE';   -- 原 #ABB9AD（gear 的值）
```

| 项 | 值 |
|---|---|
| 破坏性变更范围 | **1 张表 4 行数据** |
| **代码引用点迁移** | **0 处**（该列无任何消费点，见 E4 / E5） |
| 前端改动 | **0 处**（取色链路不变，仍读前端 token） |
| 验收 | 4 个 IP 的色条颜色：MILO→灰绿(gear)、LUNA→金黄(care)、ATLAS→棕金(play)、OLIVE→粉(home)；**若发现 CARE/GEAR 互换即 B1 未修** |

**修正必要性补验（team-lead 提供，已复核）**

| 校验 | 结果 |
|---|---|
| 4 个 `color_hint` 原值作 **chip 文字色** on 浅色 chip 底 `#F0ECE6` | MILO **2.66** ❌ / LUNA **1.40** ❌ / ATLAS 11.95 ✅ / OLIVE **1.74** ❌ —— **三条不达标** |
| ATLAS 原值 `#2E2B29` 作**色条** on 浅底 | 12.62 ✅ 可见 |
| **ATLAS 原值作色条 on 深色底 `#1A1816`** | **1.26:1 ❌ 物理上不可见** |
| ATLAS **修正后** `#B38A5A` on 深底 `#1A1816` / on `--surface-elevated` `#322E2A` | **5.65:1** ✅ / **4.30:1** ✅ |
| 修正后 4 值作色条 on 深底 `#1A1816` | MILO **8.66** / LUNA **10.74** / ATLAS **5.65** / OLIVE **9.36** —— **全部达标** |

> → ATLAS 那条**无论作文字还是作色条，在深色模式下都是死的** —— 无论选哪条路，这 2 行数据都必须改。

**⚠️ 但必须同时标注「语义线映射本身也不是权威」（诚实性修正）**

| 项 | 权威性 | 说明 |
|---|---|---|
| `brandIpId` ↔ `ip_code` 的数字/名称对应（1↔MILO …） | ✅ **权威** | DB `sort`(10/20/30/40) + `goods/list.vue` 两处 map + DB 注释三处一致 |
| 4 条产品线的**色值**（`--color-care` 等） | ✅ **权威** | `variables.scss` token 定义，注释明确写了 Care/Gear/Play/Home |
| **`ip_code` → 语义产品线** 的对应 | ⚠️ **推断，待产品侧确认** | **DB 里根本没有「IP ↔ 产品线」这一列**。§4.4.2 的映射是基于 **IP 人设 + tagline** 推断的：MILO 探险家「带宠看更大的世界」→ 出行 GEAR；LUNA 策展家「洗护也是一种仪式感」→ 洗护 CARE；ATLAS 守护者「陪伴是最长的告白」→ 玩具 PLAY；OLIVE 艺术家「生活要有自己的美学」→ 家居 HOME。**这是合理推断，但不是既定事实** |

> **Phase 4 实施前置条件**：语义线映射需**产品侧书面确认**。在确认前，Phase 3 原型按 team-lead 指示**暂用本表的推断值**（MILO 灰绿 gear / LUNA 金黄 care / ATLAS 棕金 play / OLIVE 粉 home）—— 与 §4.4.2 验收行一致。

**正确的取色链路（三层，不可跳步）**

```
ip_code ('MILO')  →  [共享常量 ipToLine]  →  语义线 ('gear')  →  CSS token (--color-gear)
     ↑                    ↑                        ↑
  DB/接口给          唯一真值源              前端 variables.scss
  （原样透传）      （待产品侧确认）          （色值权威）
```

- **不得**从 `ip_code` 字符串直接推色（会退化成 IP 名当颜色）。
- **不得**读 DB `color_hint` 取色（见 E1–E4）。

**已确认的 2 处 bug（必须一并修，否则 chip 会挂错色）**

| # | 位置 | 现状 | 后果 | 修法 |
|---|---|---|---|---|
| **B1** | `goods/detail.vue:370` `brandLine` map | `{ 1: 'MOYUYO CARE', 2: 'MOYUYO GEAR', 3: 'MOYUYO PLAY', 4: 'MOYUYO HOME' }` | ❌ **1/2 与 DB 权威相反** → MILO(1) 的商品被标成 CARE、LUNA(2) 被标成 GEAR | 按上表改正：`{ 1: 'MOYUYO GEAR', 2: 'MOYUYO CARE', 3: 'MOYUYO PLAY', 4: 'MOYUYO HOME' }` |
| **B2** | `tabbar/category.vue:79` | `:class="`tag-${p.ip.toLowerCase()}`"` → `tag-milo` 系列 | ❌ `common.scss` 无此类 → **分类页商品卡徽标无任何配色** | §4.4.1：`.tag-*` 同时定义 IP 别名与语义别名 |

**视觉规范要求（Phase 3 必须遵守）**

| 项 | 规格 |
|---|---|
| 映射表唯一位置 | **前端建一份共享常量**（如 `src/constants/brandIp.js`），导出 `idToIp` / `ipToId` / `ipToLine` 三个函数；**禁止在页面里各写一份 map** |
| chip 色条取色 | 按 **语义产品线**取（`ipToLine('MILO') → 'gear'` → `--color-gear`），**不按 IP 名取**、**不读 DB `color_hint`** |
| chip 文字 | 语义产品线名（如 `GEAR 出行`），由 `ipToLine` 派生，不直接显示 `MILO` |
| i18n | 4 条产品线名需入 i18n（中英两份），沿用 `goodsDetail.specProductLine` 所在字典组 |
| 验收 | **逐个核对 4 个 IP 的色条颜色**：MILO→灰绿(gear)、LUNA→金黄(care)、ATLAS→棕金(play)、OLIVE→粉(home)。若发现 CARE/GEAR 互换，即 B1 未修 |

> **DB 里更可靠的 IP 辅助维度（若 Phase 4 需要）**：`mo_brand_ip.species` / `species_label` —— `MILO=DOG` / `LUNA=CAT` / `ATLAS=DOG` / `OLIVE=CAT`（**犬猫交替**）。它是 `NOT NULL` + 注释明确 + 有结构，比 `color_hint` 可靠得多。**若要给 IP 加一个区分维度，优先用 species，而不是 color_hint。**



### 4.5 分割线强弱两级（新增 2 个 + 1 个补充）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--color-divider`（已有，保持） | `#EAE5DD` | `#3A3530` | **标准级**：区块之间 |
| `--divider-weak` | `#F0ECE6` | `#2F2B27` | **弱级**：列表行之间、卡片内部细分隔（多数场合用这一级） |
| `--divider-strong` | `#E2DCD3` | `#453F39` | **强级**：吸顶顶栏底边、页脚分隔、明显分区 |

### 4.6 骨架屏（新增 4 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--skeleton-base` | `#EFE9E1` | `#2E2A27` | 骨架基色（**修复现有写死 `#f0f0f0` 在深色下闪白的问题**） |
| `--skeleton-highlight` | `#F7F3ED` | `#3C3732` | 扫光高亮（深色下亮度不能超过背景太多，否则刺眼） |
| `--skeleton-radius` | `8rpx` | `8rpx` | 骨架圆角（对齐 `--radius-sm`） |
| `--skeleton-duration` | `1.4s` | `1.4s` | 扫光周期 |
| **`--skeleton-gradient`** | `linear-gradient(90deg, var(--skeleton-base) 25%, var(--skeleton-highlight) 50%, var(--skeleton-base) 75%)` | 同左（浅深共用，随 base/highlight 自动适配） | **扫光渐变**。⚠️ **v1.3 补定义**：§5.1.4 / §5.2.4 / §5.3.4 三处骨架屏规格都引用了它，但 v1.2 的 §4.6 未定义 → Phase 3 照实现会拿到 `undefined`（扫光失效或整块透明）。**必须配 `background-size: 200% 100%` + `animation: skeleton-loading var(--skeleton-duration) ease infinite`** |

### 4.7 文字 / 交互 / 圆角 / 阴影 / 字体 / 布局（新增 17 个）

| 变量名 | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| `--text-secondary-strong` | `#55504A` | `#CFC7BB` | 正文与三级文字之间的第 4 档（社区摘要、订单标签） |
| `--text-link` | `#8A7844` | `#C9B47A` | 可点击文字（浅色下深金保证对比度） |
| `--text-muted-inverse` | `rgba(246,242,238,0.72)` | `rgba(246,242,238,0.72)` | 图片 / 深色面上的次要文字 |
| `--state-press` | `rgba(46,43,41,0.06)` | `rgba(255,255,255,0.08)` | 按压态底色 |
| `--state-hover` | `rgba(46,43,41,0.03)` | `rgba(255,255,255,0.05)` | hover 底色 |
| `--focus-ring` | `rgba(138,120,68,0.45)` | `rgba(201,180,122,0.55)` | 键盘 / 无障碍聚焦环 |
| `--color-danger-strong` | `#B3574B` | `#D9836F` | **承载白字的危险表面**（删除按钮、危险操作按钮）。浅色端 `#B3574B` + 白字 = 4.79:1 ✅；深色端 `#D9836F` **必须配 Charcoal 字**（4.97:1 ✅），白字仅 2.83:1 ❌。与 `--color-danger` 的分工：`--color-danger` 只作图标 / 小圆点 / 描边，`--color-danger-strong` 才可承载文字 |
| `--radius-xs` | `6rpx` | `6rpx` | 小标签 / 角标圆角 |
| `--radius-xl` | `32rpx` | `32rpx` | 大容器（会员卡、Hero 卡） |
| `--radius-2xl` | `40rpx` | `40rpx` | 底部 sheet / 全屏卡片 |
| `--radius-circle` | `50%` | `50%` | 头像、圆形图标底 |
| `--shadow-brand` | `0 8rpx 24rpx rgba(138,120,68,0.26)` | `0 8rpx 24rpx rgba(0,0,0,0.55)` | 品牌金色光晕（会员卡 / FAB，**替代 `rgba(0,122,255,0.3)`**） |
| `--shadow-brand-sm` | `0 4rpx 12rpx rgba(138,120,68,0.20)` | `0 4rpx 12rpx rgba(0,0,0,0.45)` | 品牌光晕弱档（主 CTA） |
| `--shadow-none` | `none` | `none` | 深色下用明度差代替阴影时显式关闭 |
| `--font-display` | `'Playfair Display','Georgia','Songti SC','STSong','Noto Serif CJK SC',serif` | 同左 | 展示体 —— ⚠️ **v1.3/v1.4 收敛为 3 类「Latin 短文本」位置**：① 品牌 wordmark `MOYUYO`（28rpx）② Hero 标题（44rpx）③ **会员卡等级名（英文，56rpx）**。**所有中文标题一律走 `--font-sans`**（Playfair Display 不含中文字形，中文会 fallback serif → 改用 `--font-sans` + 500/600 字重，见 §3 锚点表）。字体方案 A 见 §6 约束 6（`font-display: swap` + 完整 fallback 链 + woff2 子集化） |
| `--font-sans` | `'Inter',-apple-system,'PingFang SC',sans-serif` | 同左 | 正文体（现有 `page` 字体，抽成变量）。**v1.3 起，顶栏中文标题也归此列**（500 / 600 字重） |
| **动效时长** | `--duration-fast` | `120ms` | `120ms` | 快捷反馈：点赞心跳、图标状态切换、chip 选中 |
| | `--duration-base` | `200ms` | `200ms` | 常规过渡：按压态、展开/收起、骨架淡入 |
| | `--duration-slow` | `320ms` | `320ms` | 大件过渡：弹窗进出、sheet 滑入、页面转场 |
| **缓动** | `--ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` | 同左 | 进入 / 退出动效（元素进场、展开） |
| | `--ease-inout` | `ease-in-out` | 同左 | 位置往复（进度条、脉冲点、加载） |
| **层级 z-index** | `--z-sticky` | `20` | `20` | 吸顶顶栏 |
| | `--z-fab` | `60` | `60` | 悬浮操作按钮（社区 FAB） |
| | `--z-sheet` | `80` | `80` | 底部弹层 / 抽屉 |
| | `--z-modal` | `100` | `100` | 模态框 / Action Sheet |
| **禁用态** | `--state-disabled-bg` | `#D1D1D6` | `#4E4944` | 禁用按钮底 / 不可用控件底 |
| | `--state-disabled-text` | `#9A948C` | `#807A72` | 禁用按钮文字 / 不可用控件文字 |
| `--tracking-kicker` | `0.18em` | `0.18em` | 小型大写标签字距 |
| `--tracking-tight` | `-0.01em` | `-0.01em` | 展示体大标题字距 |
| `--gutter` | `32rpx` | `32rpx` | 页面左右统一边距（替代现有 24/20/16rpx 混用） |
| `--section-gap` | `48rpx` | `48rpx` | 区块纵向间距 |
| `--icon-xs / -sm / -md / -lg / -xl` | `24 / 32 / 40 / 48 / 56 rpx` | 同左 | 图标尺寸 5 档 |

### 4.8 字号阶梯（扩展现有阶）

| Token | rpx | ≈px @375 | 字体 | 字重 | 用途 | 行高 |
|---|---|---|---|---|---|---|
| `--font-size-2xs`（新增） | 20 | 10 | sans | 400 | 版权、角标（**非关键信息**） | 1.4 |
| `--font-size-xs` | 22 | 11 | sans | 400 | 时间、计数、元信息 | 1.4 |
| `--font-size-sm` | 26 | 13 | sans | 400/500 | chip、标签、次要说明 | 1.5 |
| `--font-size-base` | 28 | 14 | sans | 400 | 正文 | 1.6 |
| `--font-size-md` | 30 | 15 | sans | **400** | 社区正文、对话预览 | **1.7** |
| `--font-size-lg` | 32 | 16 | sans | 500 | 卡片标题、会话名 | 1.4 |
| `--font-size-xl` | 36 | 18 | **display** | 400 | 区块标题、卡片主标题 | 1.3 |
| `--font-size-2xl` | 44 | 22 | **display** | 400/500 | **Hero 标题**（展示体的两个使用场景之一） | 1.2 |
| `--font-size-3xl` | 56 | 28 | **display** | 500 | **会员卡等级名（英文）** —— 全页唯一 56rpx 展示体（v1.4 定案） | 1.1 |
| `--font-size-4xl` | 68 | 34 | **display** | 500 | ⚠️ **预留 · Hero 大字变体（专题 / 活动页）—— 当前 5 个 tab 均不使用**，Phase 3 不得误用（v1.2 修正：原文误写为「Hero 展示标题」，与 §5.1.2 的 44rpx 冲突，现以 §5.1.2 为准） | 1.1 |

**字重使用纪律**

| 字重 | 允许用途 | 禁止用途 |
|---|---|---|
| 300 / 400 | 全部正文、摘要、标签、价格 | — |
| 500 | 卡片标题、会话名、按钮、chip | 大段正文 |
| 600 | **仅**：未读会话标题、商品名（单行）、数字强调 | **正文**（F3 即此问题） |
| 700 | 会员卡主数字、Hero 标题 | 标签、元信息 |

### 4.9 间距与栅格标尺

| 项 | 值 | 说明 |
|---|---|---|
| 页面左右边距 `--gutter` | **32rpx** | 5 页统一（现有 home 24 / community 16 / messages 24 / user 24 混用 → 统一 32） |
| 区块之间 `--section-gap` | 48rpx | 「上一区块底 → 下一区块顶」 |
| 区块内元素 `--stack-gap` | 24rpx | 标题组内、卡片内区块间 |
| 卡片内边距 | 24rpx（紧凑）/ 32rpx（舒展） | 对应 `--space-md` / `--space-lg` |
| 4 列网格 | 列宽 ≈ 153rpx，列间距 24rpx | `(750 - 2*32 - 3*24) / 4` |
| 2 列网格 | 列宽 ≈ 331rpx，列间距 24rpx | `(750 - 2*32 - 24) / 2` |
| 12 栅格 | 单列 ≈ 35rpx，列间距 24rpx | 用于图注式排版对齐 |
| 圆角档位 | 6 / 8 / 16 / 24 / 32 / 40 / pill / 50% | 对应 `--radius-xs/sm/md/lg/xl/2xl/pill/circle` |
| 阴影档位 | none / sm / md / lg / card-sm / card-md / card-lg / pop / brand / brand-sm | 浅色下只允许 ≤ `card-md`；`pop`/`brand` 每页 ≤ 1 处 |

**响应式断点**

| 断点 | 宽度 | 调整 |
|---|---|---|
| 小屏 | ≤ 320px | `--gutter` → 24rpx；**Hero 标题 44 → 36rpx**；**会员卡等级名 56 → 48rpx**；2 列商品图维持 1:1 |
| 基准 | 375–414px | 全部规格按本文 |
| 大屏 | ≥ 430px | 顶部留白 +32rpx；`--gutter` 不变 |
| 折叠屏 / 平板 | ≥ 600px | 内容区最大宽 750rpx 居中，两侧留白；商品网格升 3 列 |
| 横屏 | — | Hero 高度改 40vw；sticky 顶栏保留 |

### 4.10 对比度校验（WCAG）

| 前景 / 背景 | 对比度 | 判定 | 处置 |
|---|---|---|---|
| `#2E2B29` on `#F6F2EE` | **12.6:1** | ✅ AAA | 正文主色 |
| `#6E6962` on `#F6F2EE` | **4.83:1** | ✅ AA | 次要文字 |
| `#9A948C` on `#F6F2EE` | **2.70:1** | ❌ AA 不达标 | 三级文字**仅**用于非关键元信息（时间/计数）；关键信息改用 secondary |
| `#2E2B29` on `#DBC98A` | **8.53:1** | ✅ AAA | **金色按钮上的文字用 Charcoal，不用白色**（修复 F7） |
| `#FFFFFF` on `#DBC98A` | **1.65:1** | ❌ 严重 | 禁止（现 FAB / login-btn / conv-avatar 的问题） |
| `#B8A66B` on `#FFFFFF` | **2.41:1** | ❌ | 价格**不得**用 `--color-primary-dark`；改 Charcoal + 600，或改用 `--chip-accent-text #6B5F3A` on `#F5EFDD`（**5.50:1** ✅） |
| `#B8A66B` on `#DBC98A` | 1.46:1 | ❌ | 金色底上禁用金色字 |
| `#C9B47A` on `#2A2724` | **7.28:1** | ✅ AAA | 深色模式下金色**可**做标题文字 |
| `#F6F2EE` on `#1A1816` | **15.89:1** | ✅ AAA | 深色正文 |
| `#B8B0A5` on `#1A1816` | **8.25:1** | ✅ AAA | 深色次要文字 |
| `#807A72` on `#1A1816` | **4.17:1** | ⚠️ 临界 | 深色三级文字建议提到 `#8C857C`（**4.86:1**） |
| `#6B5F3A` on `#F5EFDD` | **5.50:1** | ✅ AA | 品牌 chip 文字 |
| `#B8B0A5` on `#322E2A` | **6.28:1** | ✅ AA | 深色会话容器上的次要文字 |
| `#2E2B29` on `#F0ECE6` | **11.95:1** | ✅ AAA | 产品线 chip 文字（`--chip-text-strong` on `--chip-bg`） |
| `#F6F2EE` on `#322E2A` | **12.09:1** | ✅ AAA | 深色 chip 文字 |
| `#FFFFFF` on `#C96E5F` | **3.57:1** | ❌ | 危险色承载白字 → 改用 `--color-danger-strong` |
| `#FFFFFF` on `#B3574B` | **4.79:1** | ✅ AA | `--color-danger-strong` 浅色端 + 白字 |
| `#2E2B29` on `#D9836F` | **4.97:1** | ✅ AA | `--color-danger-strong` 深色端 + **Charcoal 字** |
| `#FFFFFF` on `#D9836F` | **2.83:1** | ❌ | 深色端**不可**用白字 |
| `#FFFFFF` on `#E6D59A` | **1.46:1** | ❌ 严重 | 兜底 Hero 金色底 → 改用 `--text-on-accent` |
| `#2E2B29` on `#E6D59A` | **9.61:1** | ✅ AAA | 兜底 Hero 文字（`--text-on-accent`） |
| `#F6F2EE` on `#7E6B36` | **4.66:1** | ✅ AA | **会员卡渐变浅端 + 米白字（v1.2 修正后）** |
| `#F6F2EE` on `#6B5F3A` | **5.67:1** | ✅ AAA | 会员卡渐变中段 |
| `#F6F2EE` on `#B8A66B` | **2.17:1** | ❌ 严重 | **会员卡渐变旧方案浅端 —— v1.2 已改为 `#7E6B36` 起** |
| `#E8DDB5` on `#4A432C` | **7.25:1** | ✅ AAA | 深色会员卡渐变浅端 |
| `#E8DDB5` on `#2A2619` | **11.54:1** | ✅ AAA | 深色会员卡渐变深端 |
| `#D9836F` on `#2A2724` | **5.25:1** | ✅ AA | 深色模式 danger 点 / 描边 |
| `#C96E5F` on `#F6F2EE` | **3.21:1** | ⚠️ | 浅色 danger 小圆点（图形，非文本，可接受）；**不可**承载白字 |

**产品线色作为文字的实测（v1.2 新增）**

| 产品线 | 色值 | on `#F6F2EE`（浅） | on `#2A2724`（深） | 裁定 |
|---|---|---|---|---|
| Care | `#dbc98a` | **1.40:1** ❌ | **9.00:1** ✅ | 浅色仅可做图形 |
| Gear | `#abb9ad` | **1.68:1** ❌ | **7.27:1** ✅ | 浅色仅可做图形 |
| Play | `#b38a5a` | **2.45:1** ❌ | **4.74:1** ✅ | 浅色仅可做图形 |
| Home | `#d9b4b0` | **1.58:1** ❌ | **7.85:1** ✅ | 浅色仅可做图形 |

> 4 个产品线色**在浅色模式下全部不可做文字**（1.40–2.45:1）。这不是调色失误，而是低彩度淡色作为文字色的固有问题 —— 详见 §4.4.1 的「产品线色三用法则」。

**由此得出的三条硬规则**

1. **浅色模式金色不可做文字**（1.65:1）。金色在浅色下的合法身份只有：填充底（配 Charcoal 字）、1rpx 装饰线、图形元素（指示器/图标）。
2. **深色模式金色可做标题**（≥7:1）。同一套组件在深浅两端的金色用法可以不同，这是允许的。
3. **产品线色（Care/Gear/Play/Home）在浅色模式永不做文字**，只做图形（色条 / 色块 / 图标底）；深色模式可做文字。详见 §4.4.1。
4. **三级文字 `--color-text-tertiary`（2.70:1）只允许用于时间、计数等非解释性元信息**；一切**解释性文字**（空态副标、错误提示、表单说明、卡片描述）必须用 `--color-text-secondary`（4.88:1）。

### 4.11 新 token → 解决现有问题 对照表

| 现状问题 | 解决手段 | 具体 token |
|---|---|---|
| F1 同质化白卡 | 分级表面 + hairline 语法 | `--surface-elevated` `--surface-sunken` `--divider-weak` `--divider-strong` |
| F3 正文全粗 | 字号阶 + 字重纪律 | `--font-weight-regular` + `--font-size-md` 行高 1.7 |
| F4 tab 指示器弱 | 指示器规格（满宽金线 / 3 段短线） | `--color-primary`（深色自动切 `#C9B47A`） |
| F5 消息页割裂 | 单一容器 + 内部分段 | `--surface-elevated` + `--divider-weak` |
| F6 iOS 蓝会员卡 | 暗古铜渐变 + 品牌阴影 | `--brand-gradient-vip` `--shadow-brand` |
| F7 白字压浅金 | 金色上的文字改 Charcoal | `--text-on-accent` |
| F8 硬编码红 | 语义色归位 + 深浅两套 | `--color-danger`（深色可覆盖为 `#D9836F`） |
| F9 绿色兜底 | 兜底值改为品牌色 | `--color-primary` 兜底 `#DBC98A` |
| F10 骨架闪白 | 骨架色 token 化 | `--skeleton-base` `--skeleton-highlight` |
| F12 SVG 占位失效 | 暖灰渐变占位 + lucide 图标 | `--img-placeholder` `--img-placeholder-flat` |
| F13 排印空头 | 展示体 3 类 Latin 场景 + fallback 链 + `font-display:swap` | `--font-display` `--tracking-kicker` `--font-sans` |
| F14 pet 旁路 | pet 页本地 token 收敛到共享 token | `--surface-elevated` `--divider-weak` `--color-success` `--color-danger` |
| **同类问题 · 共享常量清单** | 现状有 **3 处「同一语义、多处硬编码」**，统一用「抽共享常量 + 加一致性校验」解决（详见 §6 约束 16） | | |
| ↳ 产品线映射 | `goods/list.vue:285` 与 `goods/detail.vue:370` 各一份 map，**且互相矛盾**（1/2 相反） | 新建 `src/constants/brandIp.js`（`idToIp`/`ipToId`/`ipToLine`）+ 一致性单测（§4.4.2） |
| ↳ tabBar 元数据 | **4 处副本**（`pages.json` 权威 / `home.vue:146` / `theme.js:75` / `category.vue:148`），其中 **2 处当前顺序已错位 → APP 端底栏图标互换（现存 Bug）** | 新建 **`src/constants/tabBar.js`**（`TAB_BAR_ITEMS`：path + labelKey + iconClass + iconfont）+ 一致性单测（§6 约束 16） |
| ↳ 共享常量总门禁 | —— | 建议 **`src/constants/` 目录** + **统一单测文件**（`src/constants/__tests__/constants.spec.js`）覆盖全部常量的「与静态配置一致」校验 |
| 5 页空态缺口 | 四态规范（§5 各页第 4 小节） | 全部由上述 token 组合，不新增 |

---

## 5. D. 逐页布局规范

> 阅读约定：**骨架**= 从上到下的区块顺序与职责；**规格**= 圆角/阴影/内边距/字号字重/图标尺寸/图文间距；**焦点**= 视觉主角如何建立。

---

### 5.1 HOME · 首页 —— 橱窗 / 陈列

#### 5.1.1 页面骨架

| # | 区块 | 定位 | 职责 |
|---|---|---|---|
| 1 | **Brand Header** | sticky 顶栏 | 品牌识别 + 全局动作（相机、铃铛）。**不放搜索框**——搜索降级到第 2 区块，避免顶栏与检索行互相竞争 |
| 2 | **Search Hairline** | 紧贴顶栏下 | 一行无填充检索入口（仅 hairline + 放大镜），点击进搜索页 |
| 3 | **Hero Poster** | 首屏主体 | 唯一视觉焦点：主视觉海报 + 展示体标题 + kicker 标签 |
| 4 | **Quick Rail（金刚区）** | Hero 下方 | 4 个运营入口，降级为细窄功能条，**去掉白卡外壳** |
| 5 | **Curated Shelf（商品陈列区）** | 主体下半 | 3 个 tab + 2 列商品陈列。区块头用 kicker + 标题（sans 600）+ "看全部" |
| 6 | **Brand Footer** | 页面末尾 | 品牌收尾：细金线 + **正体** slogan（英文用展示体 / 中文用 sans 300，**均不用斜体**）+ 版本/社交 |

#### 5.1.2 区块视觉规格

**① Brand Header**

| 项 | 规格 |
|---|---|
| 结构 | 左：wordmark「MOYUYO」；右：2 个图标按钮（luc-camera、luc-bell） |
| 高度 | 96rpx + `max(var(--status-bar-height,0px), env(safe-area-inset-top,0px))` |
| 背景 | `--color-surface-veil-95` + `backdrop-filter: blur(20rpx)`；底边 `--divider-weak` 1rpx |
| wordmark | `--font-display`，28rpx，字重 500，`--tracking-kicker` 0.18em，色 `--color-text`（**纯拉丁文本，展示体安全** —— 见 §3 锚点表 v1.3 说明） |
| 图标按钮 | 触控区 72rpx × 72rpx，图标 40rpx，色 `--color-text`；无独立底色（hover 才出 `--state-hover` 圆角底） |
| 铃铛角标 | 24rpx 实心圆 `--color-danger` + 白字 20rpx；**最多 1 个**（不再叠加数字） |
| 圆角/阴影 | 无圆角、无阴影（贴顶全宽） |

**② Search Hairline**

| 项 | 规格 |
|---|---|
| 结构 | 单行：luc-search 32rpx + placeholder 26rpx `--text-tertiary` + 右侧「搜索」动作字 24rpx（可省） |
| 高度 | 72rpx；**底边** 1rpx `--divider-weak`；**无填充底**（这是与现状最大的差别：现状是 `--color-background` 胶囊，与页面底同色但圆角，视觉上仍像一个"输入框") |
| 边距 | 左右 `--gutter` 32rpx |
| 间距 | 图标与文字 12rpx |

**③ Hero Poster**

| 项 | 规格 |
|---|---|
| 结构 | swiper（每帧 = 图片 + 底部渐变遮罩 + 左侧 kicker + 展示体标题 + 可选副标） |
| 比例 | 16:9，高度 `calc((100vw - 64rpx) * 0.5625)`（**不要固定 320rpx**，宽屏会裁切） |
| 圆角 | `--radius-lg` 24rpx |
| 阴影 | `--shadow-pop`（本页唯一允许的 pop 级阴影） |
| 边距 | 左右 32rpx；上下留白：上方 24rpx（接 search），下方 40rpx（接 quick rail） |
| 图片 | `mode="aspectFill"`，容器底 `--img-placeholder`（无图时显示渐变占位） |
| 文字遮罩 | **删除**现有的 `rgba(0,0,0,0.35)` 半透明方块 → 改 `--brand-gradient-ink` 满幅渐变 |
| kicker | 12rpx，`--tracking-kicker`，白 78%（`--text-muted-inverse`） |
| 标题 | `--font-display`，**44rpx**（`--font-size-2xl`），白，行高 1.15，最多 2 行。⚠️ **v1.3 提示**：展示体只对**拉丁字符**有效 —— 若 CMS 配置的是中文标题，Playfair 无中文字形会 fallback serif。此时改用 `--font-sans` 字重 600，**视觉规格（44rpx / 行高 / 白色 / 位置）不变**，只换字族 |
| 副标 | 26rpx sans，白 72%，最多 1 行 |
| 指示器 | **自绘**，放在 swiper 外部左下角：3 段 32rpx × 4rpx 圆角短线，间距 8rpx；选中段 `--color-primary`，未选 `rgba(46,43,41,0.18)`；深色 `rgba(255,255,255,0.22)`。**若仍用原生 `indicator-dots`，必须写字面量 hex**（见 §6 约束 2） |

**④ Quick Rail（金刚区）**

| 项 | 规格 |
|---|---|
| 结构 | 4 列 grid，`gap: 24rpx`；每项 = 56rpx 圆角方图标底 + 12rpx 间距 + 26rpx 标签 |
| **外壳** | **无卡片、无阴影、无边框**（现状是 `.kingkong` 白色卡片 → 删除） |
| 图标底 | `--chip-bg`，圆角 `--radius-sm` 16rpx，尺寸 96rpx × 96rpx |
| 图标 | lucide 44rpx，`--color-text-secondary` |
| 「分类」项（主行动） | 图标底 `--accent-soft`，图标 `--color-primary-dark`，标签 `--color-text`（字重 500）—— 金色出现在此，**仅此一处** |
| 标签 | 26rpx，`--font-size-sm`，`--color-text-secondary`，`--tracking-tight` |
| 边距 | 左右 32rpx；上下 8rpx（作为独立条带，与 Hero 间距 40rpx，与 Shelf 间距 48rpx） |

**⑤ Curated Shelf（商品陈列区）**

| 项 | 规格 |
|---|---|
| 区块头 | kicker 20rpx `--tracking-kicker` `--text-tertiary` + 标题 `--font-display` 36rpx `--font-size-xl` + 右侧「查看全部 ›」26rpx `--color-text-secondary` + luc-chevron-right 24rpx；区块头与内容间距 24rpx |
| 3-tab | 文字切换器：未选 26rpx `--text-tertiary`；选中 26rpx `--color-primary-dark` 字重 600 + 下方 4rpx × 40rpx 金色短线（`--color-primary`）。**不加分隔线**，不要边框；tab 条高度 72rpx，与内容间距 24rpx |
| 商品网格 | 2 列，`gap: 24rpx`（现状 16rpx → 24rpx，留白更舒展） |
| 商品卡 | **无白卡、无边框、无阴影**（删除现状 `background: var(--color-surface)` + `border` + `radius-md`）；结构 = 1:1 图 + 12rpx 间距 + 图注组 |
| 图片 | 1:1，`padding-top: 100%` 撑高；圆角 `--radius-md` 16rpx；`mode="aspectFill"`；底 `--img-placeholder-flat` |
| 商品名 | 26rpx，字重 **400**，`--color-text`，行高 1.4，**单行省略** |
| 描述 | 22rpx，`--color-text-tertiary`，单行省略，与商品名间距 6rpx |
| 价格 | **30rpx，字重 600，`--color-text`**（不用金色，理由见 §4.10）+ 货币符号 22rpx；原价 22rpx `--text-tertiary` 删除线，间距 8rpx |
| 评分 | luc-star 22rpx `--color-warning` + 数字 22rpx `--color-text-secondary` + 评分数 22rpx tertiary |
| 图文间距 | 图 → 商品名 12rpx；商品名 → 描述 6rpx；描述 → 价格 12rpx；价格 → 评分 6rpx |

**⑥ Brand Footer**

| 项 | 规格 |
|---|---|
| 结构 | 上方 1rpx `--divider-strong` 横线（左右各 48rpx 留白）+ 居中 slogan + 下方一行版权 |
| slogan | **26rpx 正体，不使用斜体**（v1.5 裁定）：英文 locale → `--font-display`；中文 locale → `--font-sans` 字重 300；色 `--color-text-secondary`；文案随 i18n。**编辑感由字距与细分割线表达，不靠倾斜**（见 §6.6.7） |
| 版权 | 20rpx，`--text-tertiary` |
| 底部留白 | `padding-bottom: 40rpx` + `env(safe-area-inset-bottom)` |

#### 5.1.3 视觉焦点如何建立

- Hero 是本页**唯一**同时具备「大图 + 展示体 44rpx 标题 + `--shadow-pop` 阴影 + 圆角」的元素 → 眼睛第一下必然落在它上面。
- 其余区块主动降级：Quick Rail 去卡片（与页面底同色）、Shelf 去卡片（无边框无阴影）、tab 指示器从 64rpx 缩短到 40rpx。
- 色彩控制：整页金色出现 **2 次**（分类入口图标底、tab 指示器）+ Hero 标题区 1 次（遮罩上的白字不算金色）。
- 视线动线：Hero（亮、大、有阴影）→ 区块 kicker（细金线感）→ 商品图（网格节奏）→ Footer（静）。

#### 5.1.4 空态 / 加载态 / 错误态

| 状态 | 视觉处理 |
|---|---|
| **加载（首屏）** | Hero 区：`--img-placeholder` 渐变块 + `--skeleton-gradient` 扫光，占 16:9 同尺寸（避免布局跳动）；Quick Rail：4 个 96rpx `--skeleton-base` 圆角方块 + 下方 4 条 120rpx 宽骨架条；Shelf：2 列骨架卡（1:1 图占位 + 2 条文字条，宽度 100%/60%）。**全程不出现「loading...」文字** |
| **空（无推荐商品）** | Shelf 区块头保留（说明"这里本该有东西"），下方居中：120rpx 圆 `--chip-bg` 底 + luc-paw-print 56rpx `--color-text-tertiary` → 间距 24rpx → 标题 28rpx `--color-text`「暂时还没有为你挑好」→ 副标 24rpx **`--color-text-secondary`**「换换口味，去分类看看」→ 24rpx 后 → ghost 按钮「去分类逛逛」（描边 1rpx `--divider-strong`，文字 `--text-link`，高 72rpx，pill） |
| **错误（banner 接口失败）** | 沿用兜底 Hero：显示 `--brand-gradient-hero` 品牌渐变块 + 居中 kicker 文字，**不弹 toast**（现状行为正确，保留）。⚠️ **v1.2 修正**：兜底块上的文字**必须**用 `--text-on-accent`（#2E2B29，on `#E6D59A` = 9.61:1 ✅）；**禁止白字**（白字仅 1.46:1 ❌）。深色模式下兜底块用 `--brand-gradient-hero` 深色值，文字仍为 `--text-on-accent`（#1A1816） |
| **错误（推荐接口失败且无缓存）** | Shelf 位置替换为 inline 错误条：`--state-error-surface` 底 + luc-alert-triangle 28rpx `--color-danger` + 文本 26rpx「加载失败」+ 文字按钮「重试」`--text-link`；高度自适应，**不遮挡上方已渲染内容** |
| **下拉刷新** | 原生 page 级 `onPullDownRefresh`，不做自定义刷新头 |

#### 5.1.5 暗色模式差异（非自动反色）

| 元素 | 浅色 | 暗色特殊处理 |
|---|---|---|
| Hero 底部遮罩 | `--brand-gradient-ink`（末端 0.62 黑） | 末端提到 **0.72**，否则浅色遮罩在深底上等于没有，标题读不清 |
| Hero 阴影 | `--shadow-pop` | 改为 `0 10rpx 28rpx rgba(0,0,0,0.55)`，深色下靠阴影分离边界 |
| 骨架屏 | base `#EFE9E1` / highlight `#F7F3ED` | base `#2E2A27` / highlight `#3C3732`（**highlight 绝不能超过 #3C3732，否则深底上扫光刺眼**） |
| 金色文字 | 浅色下金色**不可**做文字，改用 `--color-text` | 深色下金色可做标题文字（7.28:1），区块 kicker 可用 `--color-primary` |
| 商品名 / 描述 | `--color-text` / tertiary | 深色下 tertiary 提到 `#8C857C`（4.86:1） |
| 商品卡 | 无底色，靠留白分隔 | 深色下商品图与背景都暗 → 给图片加 1rpx `rgba(255,255,255,0.06)` 描边，否则图块"融进"背景 |
| Quick Rail 图标底 | `--chip-bg` | 深色下 chip 底需与背景拉开 2 级明度，`#322E2A` 即可 |
| 原生 swiper 指示器 | 写字面量 `rgba(46,43,41,0.18)` | 若保留原生 dots，深色必须换成 `rgba(255,255,255,0.22)` / `#C9B47A` —— **CSS 变量无效**，需在 `onShow` 里按 `useThemeStore().darkMode` 重新绑定 prop |

---

### 5.2 COMMUNITY · 社区 —— 杂志 / 社交墙

#### 5.2.1 页面骨架

| # | 区块 | 定位 | 职责 |
|---|---|---|---|
| 1 | **Masthead（刊头）** | sticky 顶栏 | 页面身份（中文标题 sans 600 + 拉丁 kicker）+ 搜索、铃铛入口 |
| 2 | **Issue Nav（期号导航）** | 顶栏下 | 3 tab：推荐 / 关注 / 话题。**强化"当前期号"** |
| 3 | **Topic Rail（话题条）** | 仅推荐 tab | 横向话题 chip |
| 4 | **Lead Post（头条帖）** | 信息流第 1 条 | **本页视觉焦点**：大图 + 标题式正文 |
| 5 | **Feed（碎片流）** | 其余帖子 | 左图右文的杂志碎片版式 |
| 6 | **FAB（发布）** | 右下固定 | 发布入口，圆形金色 |

#### 5.2.2 区块视觉规格

**① Masthead**

| 项 | 规格 |
|---|---|
| 结构 | 左：页面标题「社区」**36rpx `--font-sans` 字重 600**（v1.3：中文标题不用展示体）+ 下方 8rpx 间距 kicker「COMMUNITY · 宠物生活志」20rpx `--tracking-kicker` tertiary；右：luc-search 40rpx、luc-bell 40rpx（带 12rpx 红点） |
| 高度 | 96rpx + 状态栏安全区 |
| 背景 | `--color-surface-veil-95` + blur(20rpx)；底边 `--divider-weak` 1rpx |

**② Issue Nav（3 tab）**

| 项 | 规格 |
|---|---|
| 结构 | 3 等分 flex；每项 = 文字 + 底部指示器 |
| 高度 | 96rpx（现状 80rpx → 加高，让"刊头感"成立） |
| 未选文字 | 28rpx，`--color-text-tertiary`，字重 400 |
| 选中文字 | 28rpx，`--font-display`，`--color-primary-dark`，字重 500（**浅色下不用金色字**，1.65:1；深色下用 `--color-primary`） |
| 指示器 | **满宽 3rpx 圆角实线**（宽度 = tab 宽度 - 48rpx，两端内缩 24rpx），色 `--color-primary`；**取消现状 48rpx 中心短线** |
| 底边 | 整条 1rpx `--divider-weak`（与指示器区分层次） |

**③ Topic Rail**

| 项 | 规格 |
|---|---|
| 结构 | 横向 `scroll-view scroll-x`；chip = pill 999rpx |
| chip 尺寸 | 高 56rpx，左右内边距 24rpx，间距 12rpx |
| chip 默认 | 底 `--chip-bg`，字 24rpx `--chip-text` |
| chip 选中/点击态 | 底 `--chip-accent-bg`，字 `--chip-accent-text`，字重 500 |
| 边距 | 左右 32rpx；上下 20rpx |
| **右缘渐隐遮罩**（v1.4 新增） | ⚠️ **必做**：现状第 4 个 chip（`#Pet H…`）被右边缘**硬裁**，视觉上像渲染错误而非"可横滑"暗示。在 `scroll-view` 右侧叠一条 **48rpx 宽的右侧渐隐**（`linear-gradient(90deg, rgba(246,242,238,0), var(--color-background) 100%)`），把裁切变成"内容还在延伸"的暗示。⚠️ 渐隐底色必须用 `--color-background`（**不能用 `--color-surface`**，否则在页面底色上会出现一条色差带）；深色模式用深色背景值 |
| 渐隐的层级 | 渐隐层**在 scroll-view 之上**（`z-index` 1），且**不拦截触摸**（`pointer-events: none`），否则会挡住最后一个 chip 的点击 |
| 右侧留白 | scroll-view 内容末端补 48rpx padding，让渐隐遮罩有"覆盖空间"而不是贴在最后一个 chip 上 |
| **定位澄清（v1.5）** | Topic Rail 有**两个用途**，是两处独立 UI，不是同一个：<br>① **推荐 tab** = 话题**发现入口**（点 chip → 话题页 `/pages/user/community-topic`）<br>② **话题 tab** = 话题**筛选器**（点 chip → 就地按话题过滤 feed）<br>两者**共用同一套 chip 规格**（§4.4.1）与同一数据源 `GET /api/v1/community/topics` |
| **话题 tab 的选择器态** | 话题 tab 默认态 = **未选话题** → 显示「选择一个话题开始浏览」+ chip 选择器网格（复用 chip 规格，`chip-bg` 底 / 选中态 `chip-accent-bg`）；**不显示** §5.2.4 的 `C-TOPIC-EMPTY` 空态（那是"已选话题但该话题无帖子"） |

**④ Lead Post（头条帖 · 视觉焦点）**

| 项 | 规格 |
|---|---|
| 结构 | 头像行 → **大图（4:3）** → 标题式正文（最多 3 行） → 互动行 |
| 头像行 | 头像 64rpx 圆（描边 1rpx `--divider-weak`）+ 昵称 26rpx 字重 500 + 时间 22rpx tertiary，间距 8rpx；右侧 luc-more-horizontal 32rpx tertiary |
| 大图 | 4:3，`padding-top: 75%`；圆角 `--radius-lg` 24rpx；`aspectFill`；底 `--img-placeholder` |
| 正文 | **30rpx / 字重 400 / 行高 1.7**（修复 F3）；色 `--color-text`；最多 3 行（`-webkit-line-clamp:3`） |
| 互动行 | 位于正文下方，**左对齐**（与图片左边缘对齐，保持版面轴线）；图标 30rpx + 数字 24rpx 字重 **400**；未选 `--color-text-tertiary`，点赞态图标 `--color-danger`（深色 `#D9836F`） |
| 间距 | 头像行 → 图 16rpx；图 → 正文 16rpx；正文 → 互动行 16rpx |
| 与下条间距 | 48rpx（**不是** 16rpx 的卡片堆叠） |

**⑤ Feed（碎片帖）**

| 项 | 规格 |
|---|---|
| 结构 | 左图右文：3:4 缩略图 + 右侧文字组 |
| 缩略图 | 3:4，`padding-top: 133%`；圆角 `--radius-md` 16rpx；宽度 ≈ 200rpx |
| 右侧文字组 | 昵称 26rpx 字重 500 + 正文 28rpx/400/1.6 最多 3 行 + 互动行 22rpx |
| 卡片外壳 | **无白底、无边框、无阴影**；仅用 `--divider-weak` 1rpx 作为条与条之间的分隔 |
| 深色模式差异 | **浅色下"透明卡片"靠页面底色成立；深色下必须给 `--surface-elevated`(#322E2A) 底 + 24rpx 圆角**，否则图片与文字会与背景糊在一起（这是全 App 最需要特殊处理的暗色差异） |
| 加载更多 | 底部 3 点脉冲 `--color-primary`（复用 `empty-state.vue` 已有写法） |

**⑥ FAB**

| 项 | 规格 |
|---|---|
| 尺寸 | 104rpx 圆（现状 112rpx 略大） |
| 底色 | `--color-primary`；图标 **luc-camera 44rpx，色 `--text-on-accent`**（现状白字 1.65:1 → 修复） |
| 阴影 | `--shadow-brand`（**删除现状 `0 8rpx 24rpx rgba(0,122,255,0.35)`**） |
| 位置 | 右 32rpx，底 `calc(112rpx + env(safe-area-inset-bottom))` |

**⑦ 内容与商品的连接（新增，低成本高回报）**

帖子头部下方加一枚**产品线 chip**（Care / Gear / Play / Home）。⚠️ **v1.2 改写**：chip 规格按 §4.4.1 统一（底 `--chip-bg` + 字 `--chip-text-strong` + **左侧 8rpx 产品线色圆角色条** + 产品线名称文字），**不再使用**现状 `.tag-*` 的「产品线色 15% 底 + 产品线色文字」写法（实测 1.40–2.45:1 全部不达标）。

| 项 | 规格 |
|---|---|
| chip 尺寸 | 高 40rpx，圆角 `--radius-xs` 6rpx，字号 20rpx，内边距 0 16rpx |
| 底 / 字 | `--chip-bg` / `--chip-text-strong`（11.95:1 ✅） |
| 色条 | 左侧 8rpx 宽圆角色条（满高），色 = 对应 `--color-care/gear/play/home` 原色 |
| 文字内容 | 产品线名称（i18n，如 `CARE 洗护`）—— **名称是必需的**，颜色只是强化（WCAG 1.4.1） |
| 位置 | 帖子头部（头像行）下方、正文上方，间距 12rpx |
| **数据依赖** | ⚠️ **v1.5 更正（推翻 v1.4 结论，依据后端源码）** —— 社区帖子**已有**产品线字段：`mo_community_post.ip_code` VARCHAR(32) NOT NULL + `KEY idx_ip_code`，`CommunityPostEntity.java:36` 有 `private String ipCode;`，发布时由 `CommunityServiceImpl.java:192` **自动从宠物档案继承** `preferred_ip_code` → **是真数据，不是假数据**。唯一缺口：`CommunityPostVO`（17 字段）**未透出 `ipCode`** → **后端 1 行改动**。<br>→ **商品侧立即落地**（首页产品线索引行 / 商品卡 / 分类页 `?line=` 高亮）；<br>→ **社区侧**：VO 透出后即可呈现，Phase 3 原型**用真实 IP 数据映射到语义色条**即可，**不再是假数据**。<br>⚠️ **字段名 ≠ 语义**：库里存 IP 名（`MILO`），chip 色条要语义色（Care/Gear/Play/Home）→ 必须经 §4.4.2 映射表转换，且**必须先修 B1（`goods/detail.vue` 1/2 相反）**，否则 chip 会挂错色 |

#### 5.2.3 视觉焦点如何建立

- **头条帖 = 唯一一个"图大于文"的版式**（图 4:3 满宽，占卡片 60%+ 面积），其余全部缩为 3:4 小图 → 面积差 = 注意力差。
- 头条帖的标题式正文（30rpx / 400 / 1.7 / 3 行）与碎片的 28rpx 摘要形成"主次"对比，不靠粗细（因为粗细已被禁用）。
- 顶栏刊头（36rpx sans 600 + 拉丁 kicker）+ 满宽金线指示器，把"当前期号"锚死。
- 全页金色出现 2 次：选中 tab 文字 + 指示线；**其余全部中性**。

#### 5.2.4 空态 / 加载态 / 错误态

| 状态 | 视觉处理 |
|---|---|
| **加载（首屏）** | 3 个骨架帖：头像 64rpx 圆 + 2 条文字条（100% / 55%）+ 1 个 4:3 图占位；扫光用 `--skeleton-gradient`；**不出现文字态** |
| **加载（翻页）** | 底部 3 点脉冲；不整页替换 |
| **空（推荐流无内容）** | 居中：120rpx 圆 `--chip-bg` + luc-sparkles 56rpx tertiary → 24rpx → 标题 28rpx「这里还很安静」→ 副标 24rpx **`--color-text-secondary`**「发布第一条，让别人听见你的宠物故事」→ 32rpx → 主按钮「发布第一条」（`--color-primary` 底 + `--text-on-accent` 字，高 80rpx，pill） |
| **空（关注 tab 未登录）** | **不是空态**，是登录引导卡：96rpx 圆 `--chip-bg` + luc-user 48rpx → 标题 28rpx「登录后查看你关注的人」→ 24rpx tertiary → ghost 按钮「去登录」 |
| **空（话题 tab 无内容）** | 复用通用空态，图标换 luc-tag |
| **错误** | 顶部 inline 错误条（**不弹 toast、不清空已有内容**）：`--state-error-surface` 底 + 圆角 `--radius-md` + luc-alert-triangle 28rpx `--color-danger` + 26rpx「内容加载失败」+ 右端文字按钮「重试」`--text-link`；margin 左右 32rpx |
| **无更多** | 底部居中 24rpx tertiary「— 已经到底了 —」+ 上方 1rpx `--divider-weak` 居中 120rpx 宽 |

#### 5.2.5 暗色模式差异

| 元素 | 暗色特殊处理 |
|---|---|
| 帖子容器 | **必须**换成 `--surface-elevated` (#322E2A) + 24rpx 圆角；否则透明卡片在深底上完全消失 |
| 分隔线 | 弱级 `--divider-weak` 在深色下太弱 → 改用 `--color-divider`(#3A3530) |
| 选中 tab 文字 | 浅色用 `--color-primary-dark`(#B8A66B, 2.41:1 不足) → **浅色也改用 `--font-display` + `--color-text` 承重，金色只留给指示线**；深色可用 `--color-primary` 做文字 |
| 正文 | `--color-text`(#F6F2EE)，行高仍 1.7 |
| 点赞态 | `--color-danger` 深色下改为 `#D9836F`（原 `#C96E5F` 在深底偏暗） |
| 图片描边 | 图片与卡片底同暗 → 1rpx `rgba(255,255,255,0.06)` 描边 |
| FAB | 金色底在深色下偏亮 → 加 `--shadow-brand` 保持浮起；图标仍用 `--text-on-accent`(#1A1816) |
| 图片加载失败 | **不用 SVG data-uri**（现状 `resolveImageUrl` 的 `data:image/svg+xml` 在 APP 端不可靠）→ 改为图片容器显示 `--img-placeholder` 渐变 + 居中 luc-image 40rpx |

---

### 5.3 MESSAGES · 消息 —— 收件箱 / 效率

#### 5.3.1 页面骨架

| # | 区块 | 定位 | 职责 |
|---|---|---|---|
| 1 | **Inbox Header** | sticky 顶栏 | 页面身份 + 通知入口 + 发起群聊 / 私信 |
| 2 | **Inbox Container（单一容器）** | 主体 | **把「通知聚合 4 宫格」与「私信会话列表」合并到一个 `--surface-elevated` 容器内**，中间用 `--divider-weak` 分隔（修复 F5） |
| 2a | └ Notice Row | 容器上半 | 4 个通知类型入口（全部 / 互动 / 订单 / 系统），**降级为一行 chip 式摘要** |
| 2b | └ Conversation List | 容器下半 | 会话行；本容器底圆角 24rpx，行间 `--divider-weak` |
| 3 | **Load More** | 容器下方 | 「加载更多」文字按钮（24rpx tertiary），不在容器内 |
| 4 | **Empty / Login** | 替换 2a2b | 空态与未登录态，**与登录后共用同一容器骨架** |

#### 5.3.2 区块视觉规格

**① Inbox Header**

| 项 | 规格 |
|---|---|
| 结构 | 左：页面标题「消息」**36rpx `--font-sans` 字重 600**（v1.3：中文标题不用展示体）+ 下方 kicker「INBOX · 收件箱」20rpx `--tracking-kicker` tertiary；右：luc-bell 40rpx（带角标）、luc-users 40rpx（群聊）、luc-user-plus 40rpx（发起私信） |
| 高度 | 96rpx + 状态栏安全区；背景 veil-95 + blur；底边 `--divider-weak` |

**② Inbox Container**

| 项 | 规格 |
|---|---|
| 外壳 | `--surface-elevated`，圆角 `--radius-lg` 24rpx，**无阴影**（浅色下靠白底与亚麻底的差分离；深色下靠明度差），margin 左右 32rpx |
| 内部分割 | Notice Row 与 Conversation List 之间：1rpx `--divider-weak`，**左右各内缩 32rpx**（不贴边，更像"栏"） |

**②a Notice Row（通知聚合）**

| 项 | 规格 |
|---|---|
| 结构 | 4 等分 grid；每项 = 56rpx 圆角方图标底 + 10rpx 间距 + 22rpx 标签 + 可选未读点 |
| 图标底 | 56rpx × 56rpx，圆角 `--radius-sm` 16rpx（**改圆 → 方**，更"工具"，减少与头像圆的撞形）；底 `--chip-bg` |
| 图标 | lucide 32rpx，`--color-text-secondary`（luc-inbox / luc-sparkles / luc-package / luc-bell） |
| 「全部」项（主入口） | 图标底 `--accent-soft`，图标 `--color-primary-dark`，标签 `--color-text` 字重 500 |
| 未读点 | 12rpx 实心圆 `--color-danger`，贴在图标底右上角（**替代现状的"文字 + 数字"**，更安静） |
| 标签 | 22rpx，`--color-text-tertiary` |
| 内边距 | 上 28rpx / 下 24rpx |

**②b Conversation List（会话列表）**

| 项 | 规格 |
|---|---|
| 行高 | 最小 112rpx（头像 88rpx + 上下 24rpx） |
| 头像 | 88rpx 圆；图片 `aspectFill`；无头像时 fallback = 文字首字母 32rpx 字重 600，底 `--chip-bg`，字 `--color-text-secondary`；客服会话用 luc-headphones 40rpx，底 `--chip-bg`（现状是金色底 + 白字 → 改） |
| **未读标识** | **整行左侧 6rpx 宽金色竖条**（`--color-primary`，通高，两端内缩 20rpx）→ 这是全页唯一的强色彩，视觉主角由此确立 |
| 会话名 | 28rpx；未读 字重 600 / 已读 400；`--color-text`；单行省略 |
| 置顶标记 | luc-bookmark 24rpx `--color-primary`（在名字前） |
| 摘要 | 24rpx `--color-text-tertiary`，单行省略；图片/卡片/订单类消息用 i18n 占位文案 |
| 时间 | 22rpx `--color-text-tertiary`，右对齐，不换行 |
| 未读数字 | **不再用红底白字气泡** → 改为 `--chip-bg-strong` 底 + `--chip-text-strong` 字，24rpx，pill，高 32rpx（"数字是信息，不是警报"） |
| 行分隔 | 1rpx `--divider-weak`，最后一行无 |
| 左滑删除 | 按钮底 **`--color-danger-strong`**（浅 `#B3574B` 白字 4.79:1 ✅；深 `#D9836F` **必须配 Charcoal 字** 4.97:1 ✅，白字仅 2.83:1 ❌），宽度 140rpx；文字 26rpx；**保留"必须有不透明底"的技术要求**。⚠️ v1.2 修正：原写 `--color-danger`(#C96E5F) 配白字仅 3.57:1 |

**③ Load More**

| 项 | 规格 |
|---|---|
| 样式 | 居中 24rpx `--color-text-tertiary`「加载更多」；点击区域高 72rpx；加载中显示 3 点脉冲 |

**④ Empty / Login**

| 项 | 规格 |
|---|---|
| 结构 | 容器内居中：120rpx 圆 `--chip-bg` + luc-message-square 56rpx tertiary → 24rpx → 标题 28rpx → 副标 24rpx **`--color-text-secondary`** → 32rpx → 主按钮 |
| 空态文案 | 标题「还没有会话」/ 副标「发起一段对话，或等朋友先开口」/ 按钮「发起私信」 |
| 未登录 | **结构与空态完全一致**（同一 token 体系）：96rpx 圆 `--chip-bg` + luc-user 48rpx + 标题「登录后查看消息」+ 副标 + 按钮「去登录」 |

#### 5.3.3 视觉焦点如何建立

- 整页只有一种高饱和色：**金色未读竖条**。其余全是中性灰阶 + 一个 6rpx 的金色。
- 未读的第二个信号是**字重差**（600 vs 400）与**摘要行数差**（未读截断更短），三重信号叠加但**不靠颜色噪音**。
- 容器合并后，页面在视觉上是一个"整体"，眼睛先看到容器上沿的通知摘要（一行小 chip），再顺读到第一条未读会话的金色竖条。

#### 5.3.4 空态 / 加载态 / 错误态

| 状态 | 视觉处理 |
|---|---|
| **加载（首屏）** | Notice Row：4 个 56rpx `--skeleton-base` 方块 + 下方 4 条 80rpx 骨架条；会话区：4 行骨架（88rpx 圆 + 2 条文字条 100%/50%）；扫光 `--skeleton-gradient` |
| **空** | 见 ③ / ④ |
| **错误（会话接口失败）** | **只在会话区内部**显示 inline 错误条（`--state-error-surface` + luc-alert-triangle 28rpx + 26rpx「会话加载失败」+ 文字按钮「重试」），**通知聚合区照常显示**（部分失败不整体崩） |
| **错误（未登录 401）** | 切换到「未登录引导卡」，不弹 toast（现状 `request.js` 会弹登录提示 → 保持） |
| **WebSocket 断开** | 不做全屏错误；在容器下方加一行 22rpx tertiary + luc-wifi 20rpx「实时连接已断开，刷新以同步」 |
| **下拉刷新** | page 级 `onPullDownRefresh`，`uni.stopPullDownRefresh()` 收尾（现状已有） |

#### 5.3.5 暗色模式差异

| 元素 | 暗色特殊处理 |
|---|---|
| 容器 | `--surface-elevated`(#322E2A) + **关闭阴影**（改 `var(--shadow-none)`）+ 1rpx `rgba(255,255,255,0.05)` 描边；深色下靠明度差分层，不靠阴影 |
| 未读竖条 | `--color-primary`(#C9B47A)；宽度保持 6rpx（深底上不需要加粗） |
| 红点 / 角标 | `--color-danger` 深色改 `#D9836F`；未读数字 chip 用 `--chip-bg-strong`(#3B3630) + `--chip-text-strong` |
| 头像 fallback | 浅色 `--chip-bg` + secondary 字；深色 `--chip-bg`(#322E2A) 与容器(#322E2A) 撞色 → 改用 `--color-fill`(#35312D) 稍亮一档 |
| 分割线 | 容器内 `--divider-weak` 在深色下过弱 → 改 `--color-divider`(#3A3530) |
| 删除按钮 | `#ff4d4f` → `--color-danger-strong`（浅 `#B3574B` 白字 4.79:1 ✅ / 深 `#D9836F` **必须配 Charcoal 字** 4.97:1 ✅，白字仅 2.83:1 ❌）—— 保留"危险操作"的语义红，但进入品牌色系且达标 |
| 铃铛角标 | 现状 `#ff4d4f` 写死 → `--color-danger` |
| 磨砂顶栏 | `--color-surface-veil-95`（深色已有 `rgba(42,39,36,0.95)`），**不额外加深**，避免与容器色断层 |

---

### 5.4 USER · 我的 —— 账户 / 仪表盘

#### 5.4.1 页面骨架

| 项 | 规格 |
|---|---|
| 结构 | 头部账户条 → 会员卡 → 钱包带 → 社交带 → 订单带 → 功能清单（分 2 组）→ 品牌页脚 |
| 容器策略 | **通栏优先**：账户条与会员卡之间靠留白 + hairline 分组；会员卡、钱包带、订单带各自成块 |
| 外边距 | 统一 `--gutter` 32rpx（现状 24rpx → 32rpx，与其它页对齐） |
| 区块间距 | `--section-gap` 48rpx |

#### 5.4.2 区块视觉规格

**① Account Header（账户条）**

| 项 | 规格 |
|---|---|
| 结构 | 头像 112rpx 圆 + 右侧（昵称 **32rpx `--font-sans` 字重 600**（v1.3：中文昵称不用展示体）+ email 24rpx tertiary + 会员等级 chip 22rpx）+ 右端 luc-chevron-right 32rpx tertiary |
| 外壳 | **通栏**（无左右 margin、无圆角、无阴影），底边 1rpx `--divider-weak`；内边距 `48rpx 32rpx` |
| 头像 | 112rpx 圆，描边 2rpx `--divider-strong`（深色改 `rgba(201,180,122,0.30)` 金色描边，保持身份感）；空头像用 `--img-placeholder-flat` + 居中 luc-user 44rpx |
| 昵称 | `--font-display`，**32rpx**，字重 500，`--color-text` |
| email | 24rpx，`--color-text-tertiary`，单行省略 |
| 会员等级 chip | 高 40rpx，pill，底 `--chip-accent-bg`，字 `--chip-accent-text`，22rpx 字重 500 |
| 未登录态 | 同一位置换成：默认头像 + 「登录 / 注册」**32rpx `--font-sans` 字重 600** + 副标 24rpx **`--color-text-secondary`** + 金色主按钮「登录」（`--color-primary` 底 + `--text-on-accent` 字，高 72rpx，pill）。⚠️ **v1.4 补强（据 F15）**：现状未登录时**会员卡整块 DOM 不渲染**，页面上只剩功能清单里一条 `Membership` 文字项 → **会员权益在未登录态完全不可感知**。本规范要求：未登录态在账户条下方**必须保留一个会员入口的视觉暗示**（如账户条内的「登录解锁会员权益 · 查看等级」22rpx 文字链接 `--text-link`），让用户知道「登录后有会员体系」，而不是让会员卡彻底消失 |

**② Membership Card（会员卡 · 视觉主角）**

> **v1.4 语义裁定（team-lead 最终裁定：56rpx = 等级名）**
> 会员卡 = **身份象征（主）+ 积分入口（次）**，金色主角地位保留。三档信息各归其位：
>
> | 档位 | 承载 | 为什么是它 |
> |---|---|---|
> | **56rpx 展示体** | **等级名（英文）** | 全页最大的展示体数字必须承载**身份**（"我是谁"），而不是会变的**过程量**。`PLATINUM` 读起来像会员卡的烫金印字；`68%` 读起来像后台仪表盘，与「编辑式橱窗」的调性冲突 |
> | **22rpx 小字（刻度条右端）** | **精确成长值**（如 `1,240 / 8,000`） | 刻度条只给视觉进度、没给数字；这个数字回答"离下一级还有多远"，但 22rpx 远低于 32rpx 的二级上限，不违反 P1 |
> | **4rpx 刻度条** | **成长值进度** | 连续量、可扫读、零文案成本 —— 刻度条本就是进度的正确隐喻，不需要让巨型数字兼两职 |
>
> **积分余额完全移出卡面** —— 已在钱包带第 2 格（36rpx 展示体），同页重复即噪音（违反 P1）。
> **视觉规格（渐变 / 圆角 / 阴影 / 56rpx 展示体 / 4rpx 刻度 / crown / chevron 位置）全部不变。**
>
> ⚠️ **实现提示**：等级名**英文走展示体**（Playfair 有拉丁字形）、**中文副标走 sans** —— 汉字在 Playfair 里没有字形会 fallback 衬线宋体，与英文并排明显割裂。

**等级名文案表（v1.4 定案 · 沿用 `membership.vue` 现有 `levels` 数组，后端枚举 `NORMAL/SILVER/GOLD/PLATINUM/DIAMOND` → 前端展示名）**

| 等级 | 后端枚举 | 前端展示名（英文 · 56rpx 展示体） | 中文副标（24rpx sans） | 成长值门槛 |
|---|---|---|---|---|
| L1 | `NORMAL` | `Member` | 会员 | 0 |
| L2 | `SILVER` | `Silver` | 白银 | 500 |
| L3 | `GOLD` | `Gold` | 黄金 | 2,000 |
| L4 | `PLATINUM` | `Platinum` | 铂金 | 8,000 |
| L5 | `DIAMOND` | **`Black`** | **黑卡** | 25,000 |

> ⚠️ **L5 展示名是 `Black`（黑卡），不是 `DIAMOND`（钻石）**。后端枚举确实是 `DIAMOND`，但 `membership.vue` 的 `mapLevelCode()` 把 DIAMOND 映射为 L5，而 L5 的**用户可见名是 `Black`** —— 这是用户已经在会员中心见过的文案，**沿用它，不要引入新表述**。Phase 2 §2.2.2 写的 `DIAMOND 铂金` 是笔误，以本表为准。

| 项 | 规格 |
|---|---|
| 结构 | 卡内：左上 kicker「MEMBERSHIP」+ **等级名（56rpx 展示体）** + 中文副标（24rpx sans）+ **刻度条右端 22rpx 精确成长值** + 动态 tip + 底部进度刻度 + 右下 chevron + 右上 crown |
| 尺寸 | 高 **224rpx**（现状 200rpx → 加高容纳进度刻度）；margin 左右 32rpx |
| 圆角 | `--radius-xl` 32rpx |
| **背景** | `--brand-gradient-vip`：`linear-gradient(135deg,#7E6B36 0%,#6B5F3A 50%,#554A26 100%)` —— **彻底替换现状 iOS 蓝 `linear-gradient(135deg,var(--color-primary,#007aff) 0%,#004fad 100%)`**。⚠️ **v1.2 修正**：原拟 `#B8A66B → #8A7844 → #6B5F3A`（亮黄铜 + 白字）实测**浅端白字仅 2.17:1 ❌**；改为**暗古铜 + 米白字**（全段 ≥4.66:1 ✅），既达标又比大面积亮金更贵（亮金 + 白字是典型的廉价金融感） |
| **阴影** | `--shadow-brand`：`0 8rpx 24rpx rgba(138,120,68,0.26)` —— **替换 `0 8rpx 32rpx rgba(0,122,255,0.3)`** |
| kicker | 20rpx，`--tracking-kicker` 0.2em，`--text-muted-inverse`（白 72%）；恒定英文，不随语言切换 |
| **等级名（56rpx · 页面主角）** | `--font-display`，**56rpx**（`--font-size-3xl`），字重 500，`--color-text-inverse`(#F6F2EE)；内容 = **英文等级名**（`Platinum` / `Black` …，见上表）。**≤320px 时 56 → 48rpx**。这是全页唯一的 56rpx 展示体 |
| **中文副标** | 24rpx `--font-sans` 字重 500，`--text-muted-inverse`（白 72%）；紧贴等级名下方，间距 4rpx（如 `Platinum` / `铂金`） |
| **精确成长值** | 22rpx `--font-sans`，`--text-muted-inverse`（白 72%）；置于**刻度条右端上方**，如 `1,240 / 8,000`；满级时显示 `MAX` |
| tip | 22rpx，`--text-muted-inverse`，**动态文案**：`再消费约 ¥{amount} 升级至 {nextName}` / 满级 `已达最高等级`；最多 1 行 |
| crown | luc-crown 40rpx，右上，白 38% 不透明度 |
| chevron | luc-chevron-right 28rpx，右下，白 55%（**移到右下，crown 移到右上**——现状一个箭头兼两职，语义不清） |
| 进度刻度 | 卡内底部通栏 4rpx 细线：轨道 `rgba(255,255,255,0.25)` + 进度段 `--color-primary-light`；上下留白 24rpx；**进度 = 成长值进度**，与右端精确数字**同源**（不同源会读出两个进度） |
| **积分余额** | **不在卡上出现**。余额唯一出处 = 钱包带第 2 格（36rpx 展示体，`--color-text`）；积分入口 = 整卡可点 → `/pages/user/membership` |
| **数据来源** | 等级 / 成长值 / 门槛 / 进度算法**全部复用 `membership.vue` L264-283 的 `progressPercent` / `progressHint`**（`levels` 数组 + `mapLevelCode()` + `pointsRate`），**不要另立规则**。tip 的 `amount` 折算率（成长值 → 金额）需业务确认；**Phase 3 原型用占位文案即可**，Phase 4 落码前不要硬编折算率 |
| 深色模式 | 渐变换为 `linear-gradient(135deg,#4A432C 0%,#3A341F 55%,#2A2619 100%)`（暗黄铜）；文字 `#E8DDB5`（比 #F6F2EE 更贴金属，on `#4A432C` = 7.25:1 ✅）；⚠️ **v1.2 修正**：阴影改为 `0 8rpx 24rpx rgba(138,120,68,0.22)` + `0 0 0 1rpx rgba(201,180,122,0.22)`（**金色描边**）—— 深底上黑色阴影无分离作用，必须靠金色描边把卡从背景里"提"出来 |

**③ Wallet Band（钱包带）**

| 项 | 规格 |
|---|---|
| 结构 | 4 等分 grid；每项 = 数字 + 标签 |
| 数字 | `--font-display`，**36rpx**，字重 500，`--color-text`（**不用金色**，见 §4.10） |
| 标签 | 22rpx，`--color-text-tertiary` |
| 分隔 | 列间 1rpx `--divider-weak` 竖线（**不是** 4 个独立小卡） |
| 外壳 | `--surface-elevated`，圆角 `--radius-lg` 24rpx，内边距 28rpx 16rpx |

**④ Social Band（社交带 · social-grid 组件）**

| 项 | 规格 |
|---|---|
| 结构 | 4 等分 grid；每项 = 图标 + 数字 + 标签（**删除 56rpx 圆角方图标底**） |
| 图标 | lucide **32rpx**，`--color-primary-dark`（浅）/ `--color-primary`（深）；无底色 |
| 数字 | `--font-display` 32rpx 字重 500 `--color-text` |
| 标签 | 22rpx tertiary |
| 底色 | **取消**组件里的 `defaultBg: 'rgba(219,201,138,0.18)'` 硬编码 → 改为无底；避免 4 个彩色方块与会员卡抢焦点 |
| 分隔 | 列间 1rpx `--divider-weak` 竖线，top/bottom 内缩 25% |

**⑤ Order Band（订单带）**

| 项 | 规格 |
|---|---|
| 结构 | 4 等分 grid；每项 = 图标 + 标签 + 角标 |
| **图标** | luc-credit-card / luc-shopping-cart / luc-package / luc-check-circle，**44rpx**，`--color-text-secondary`。**禁止 emoji**（现状 `'💳' '🛒' '📦' '✅'` 经 `$luc()` 会变成 `luc-💳` 渲染失败） |
| 标签 | 22rpx，`--color-text-secondary` |
| 角标 | 20rpx `--color-danger` 圆点（8rpx 实心点）+ 或数字 pill，贴在图标右上角，**不抢焦点** |
| 外壳 | `--surface-elevated`，圆角 24rpx，内边距 28rpx 16rpx |

**⑥ Feature List（功能清单）**

| 项 | 规格 |
|---|---|
| 结构 | 2 组：「我的服务」8 项 + 「设置与关于」3 项；每组 = 组标题（**28rpx `--font-sans` 字重 600**，v1.3 中文标题不用展示体）+ 1rpx `--divider-weak` + 行列表 |
| 行高 | 96rpx；图标 36rpx 放在 56rpx 圆角方 `--chip-bg` 底中（与订单带的裸图标区分层级）；文字 28rpx `--color-text`；右端 luc-chevron-right 28rpx tertiary |
| **锁图标** | ❌ **v1.10 已去掉**（O8 裁定）—— 8 个锁图标让功能清单看起来"一半不可用"，负面情绪大于收益。**改为点击时拦截**，见下 |
| **登录拦截（O8）** | 需登录的项**不做视觉标记**，点击该行 → 弹 **L2 modal**：「**登录后即可查看{该功能}**」+「去登录」/「再看看」；**确认后跳登录页并带 redirect**（回跳规则见 §6 约束 15） |
| **不拦截项** | ⚠️ **「设置」里的主题 / 语言绝不拦截** —— 设备偏好与账号无关，拦截会造成"这个 App 连主题都改不了"的负面体感 |
| 分隔 | 行间 1rpx `--divider-weak`，左缩进 76rpx（对齐文字起点） |
| 组间距 | 48rpx |

**⑦ Brand Footer**

| 项 | 规格 |
|---|---|
| 结构 | 上下各 1rpx `--divider-strong`（左右内缩 48rpx）+ 居中 slogan 26rpx **正体**（英文 `--font-display` / 中文 `--font-sans` 300，v1.5 取消斜体）+ 版本号 20rpx tertiary |
| 底部留白 | 40rpx + `env(safe-area-inset-bottom)` |

#### 5.4.3 视觉焦点如何建立

- **会员卡是全页唯一大面积金色 + 唯一 `--shadow-brand` + 唯一 56rpx 展示体（等级名）** → 三重独占，无歧义。
- 会员卡紧跟账户条（第二块），保证首屏可见（112+224+96 ≈ 432rpx 内）。
- 其余四个带（钱包 / 社交 / 订单）全部降到 22–36rpx 文字 + 无阴影 + hairline 分隔，成为"数据面板"而不是"卡片堆"。
- 金色在本页出现 3 处：会员卡渐变、会员等级 chip、社交带图标 —— 同属"身份"语义。
- **积分余额不在会员卡出现**：56rpx 位给身份（等级名），余额只在钱包带第 2 格用 36rpx 展示体 —— 避免同页两个"数字主角"（P1）。**三档信息各归其位**：56rpx 等级名（身份）→ 4rpx 刻度条（视觉进度）→ 刻度条右端 22rpx 精确值（`1,240 / 8,000`）。

#### 5.4.4 空态 / 加载态 / 错误态

| 状态 | 视觉处理 |
|---|---|
| **加载** | 会员卡位置：`--img-placeholder` 渐变块占 224rpx（同尺寸，避免跳版），**不先渲染渐变卡再变**；钱包/社交数字：36rpx 高的 `--skeleton-base` 条，宽度 80rpx；订单图标：44rpx 方块骨架；清单图标：56rpx 方块骨架 |
| **未登录** | **不是"空数据"**：账户条位置换成登录引导（头像 + 标题 + 副标 + 金色登录按钮），下方**隐藏**会员卡 / 钱包 / 订单带（避免 0 值误导），功能清单保留但登录相关项加锁标记（luc-lock 24rpx tertiary） |
| **空（数据为 0）** | 正常显示「0」，**不显示骨架**（骨架只用于"未知"）；订单带不显示角标；**会员卡仍完整显示**（等级名 + 刻度条 0% + 动态 tip），不隐藏整卡 |
| **错误（会员接口失败）** | 会员卡位置替换 inline 错误条：`--state-error-surface` 底 + 圆角 24rpx + luc-alert-triangle 28rpx `--color-danger` + 26rpx「会员信息加载失败」+ 右端文字按钮「重试」`--text-link`；**其余区块正常显示旧数据或 0** |
| **错误（列表接口失败）** | 仅对应区块（订单带 / 社交带）内显示小型错误文案 24rpx **`--color-text-secondary`**「加载失败，点击重试」 |

#### 5.4.5 暗色模式差异

| 元素 | 暗色特殊处理 |
|---|---|
| **会员卡渐变** | **必须换**：`linear-gradient(135deg,#4A432C 0%,#3A341F 55%,#2A2619 100%)`；若沿用浅色黄铜渐变，在深底上会变成一块刺眼亮斑，直接违反 P1。⚠️ v1.2：浅色端也从 `#B8A66B` 改为 `#7E6B36`（暗古铜），浅深两套统一"暗底 + 浅字" |
| 会员卡文字 | `#E8DDB5`（暖米金），比 #F6F2EE 更贴金属调；进度轨道 `rgba(255,255,255,0.14)` |
| 会员卡阴影 | ⚠️ **v1.2 修正**：深底上黑色阴影无分离作用 → 改为 `0 8rpx 24rpx rgba(138,120,68,0.22)` + `0 0 0 1rpx rgba(201,180,122,0.22)`（**金色描边**）把卡从背景里"提"出来 |
| 账户条 | **浅色下通栏（与页面底同色）→ 深色下必须给 `--surface-elevated`(#322E2A) 底**，否则头像与昵称浮在深底上缺少聚合感 |
| 头像描边 | 浅色 `--divider-strong` 2rpx → 深色 `rgba(201,180,122,0.30)` 2rpx（金色描边 = 身份标识） |
| 钱包 / 社交数字 | `--color-text`(#F6F2EE)；展示体在深底上对比充足 |
| 社交图标 | `--color-primary`(#C9B47A) 替代浅色的 `--color-primary-dark` |
| 清单图标底 | `--chip-bg`(#322E2A)；需与 `--surface-elevated`(#322E2A) 区分 → **改用 `--color-fill`(#35312D)** 或给图标底 1rpx `rgba(255,255,255,0.06)` 描边 |
| 分割线 | 通栏条与带之间的 `--divider-weak` 在深色下用 `--color-divider`(#3A3530) |
| 页面整体 | 保持 `--color-background`(#1A1816) 为最深层；带状容器用 `#322E2A`；形成三级明度层次（1A1816 → 262320 → 322E2A） |

---

### 5.5 PET · 宠物（本次不改结构，仅做 token 收敛约束）

| 项 | 约束 |
|---|---|
| 保留 | 3D 场景 + 玻璃拟态浮层 + 护理卡片 结构不变 |
| 必须修正 | 删除 `pet.vue` 里的本地旁路 token（`--hub-card:#ffffff`、`--hub-line:#e5e5ea`、`--hub-muted:#8e8e93`、`--hub-ok:#34c759`、`--hub-err:#ff3b30`、`--hub-ok-bg`、`--hub-err-bg`），全部改为共享 token：`--surface-elevated` / `--color-divider` / `--color-text-tertiary` / `--color-success` + `--state-success-surface` / `--color-danger` + `--state-error-surface` |
| 卡片样式 | `.care-card` 的 32rpx 圆角 + 1rpx 边框 + `--shadow-sm` → 统一到 `--radius-lg` 24rpx + 无边框 + `--shadow-card-sm`，与 5 页一致 |
| 玻璃层 | `.glass` 的 `rgba(255,255,255,.2)` + `blur(20rpx)` 保留（这是全 App 唯一允许的模糊层），但需补 `border: 1rpx solid rgba(255,255,255,0.16)` 与不透明兜底（见 §6 约束 12） |
| 排印 | 场景内浮层标题改用 `--font-sans` + 600 字重（与 5 页顶栏统一；中文标题不用展示体，见 §3 锚点表），是"从场景切到信息页"的品牌延续点 |
| **Phase 4 低成本实施项（O7）** | **纵向 scroll-view 加 `enable-flex`** —— `pet.vue` 的 `.page-scroll`（L913）当前无 `enable-flex`；横向的 `.pets-scroll`（L884 `white-space:nowrap`）+ `.pets-inner`（L888 `display:inline-flex`）**现状已具备**（ux-designer 核实）。<br>⚠️ **验收必须在 Android 真机** —— H5 通过不代表 APP 通过（见 §6 约束 4 的 touch-slop 说明）。若真机仍有手势冲突，按 §6 约束 4.1 降级为"该滚动区不吸顶" |

#### 5.6 §5 与 Phase 3 原型的对接说明

§5 的全部规格是 **Phase 3 原型的唯一视觉真值源**，落地前必须先读：

| Phase 3 需要知道 | 去哪读 |
|---|---|
| 原型里哪些能 1:1 还原、哪些必须换写法 | **§6.13.2 / §6.13.3** |
| 原型 px → 实现 rpx 的换算 | **§6.13.5** |
| 每个区块该在 Spec 面板里登记什么 | **§6.13.6** |
| 原型交付前的自查红线 | **§6.13.7** |
| 展示体字体在原型里怎么模拟（字体缺失时不塌） | **§6 约束 6.6.4 / 6.6.5** |
| `user.vue` 滚动容器与吸顶行为 | **§6 约束 4.1** |

**Phase 3 原型必须覆盖的状态矩阵（v1.3：与 Phase 2 §4 状态机对齐，共 65 态）**

> Phase 2 §4（v1.2）定义 **67** 个状态（本节表格以 Phase 2 状态机的**状态 ID** 为准，与 Phase 2 文档一一对应）。
>
> **状态数口径说明（v1.5）**：Phase 2 文档报的是 **60**，本表按状态机 ID 清单数是 **65**，差 5 —— 差异来自「**交互动作态**」是否计入：
> | 口径 | 数量 | 说明 |
> |---|---|---|
> | 含交互动作态（**本表口径**） | **67** | 多出的 5 个是 `C-POST-ACTION`（点赞/删除请求在途）、`C-REPORT-SHEET`（举报 Action Sheet）、`C-REPORT-DONE`（举报成功 toast）、`M-SWIPED`（左滑展开）、`M-PINNED`（已置顶）—— 它们是**用户动作的中间态**，不是"数据状态的分支" |
> | 只计数据状态分支（Phase 2 口径） | **62** | 原型只需保证这 62 个的视觉正确 |
>
> **两口径都有效**：Phase 3 原型至少覆盖 62 个；5 个动作态在原型里表现为**触发后的样式变化**（如点赞变红、左滑露出删除按钮），不需要独立页面。
>
> **v1.7 对齐**：本表补入 `C-TOPIC-SELECTED`（话题 tab 已选话题态），与 Phase 2 v1.2 的 67 态**完全对齐**，双方状态 ID 零差异（已用脚本 diff 校验）。
> Phase 1 只负责**每个状态的视觉表达**（用哪个 token、什么形态）；状态之间的转移条件与用户动作由 Phase 2 定义。

**HOME · 14 态**

| 状态 ID | 视觉表达（Phase 1 token） |
|---|---|
| `H-INIT` | 页面底 `--color-background`，无内容 |
| `H-HERO-LOADING` | Hero 区 `--img-placeholder` 渐变块（16:9 等高）+ `--skeleton-gradient` 扫光 |
| `H-HERO-READY` | 16:9 swiper + `--brand-gradient-ink` 遮罩 + 恒定品牌 kicker + **展示体 44rpx 标题** |
| `H-HERO-FALLBACK` | `--brand-gradient-hero` 渐变块 + 同一句 kicker + 居中标题（**`--text-on-accent`**，9.61:1 ✅） |
| `H-SHELF-LOADING` | 2 列骨架卡：1:1 `--img-placeholder` + 2 条文字条（100% / 60%），`--skeleton-gradient` |
| `H-SHELF-LOADING-KEEP` | **保留旧数据** + 新 tab 下区块内 3 点脉冲（`--color-primary`） |
| `H-SHELF-READY` | 2 列商品卡（无白卡 / 无边框 / 无阴影）：1:1 图 + 26rpx 名 + 22rpx 描述 + 30rpx 价格（600，`--color-text`）+ 22rpx 评分 |
| `H-SHELF-EMPTY` | 120rpx 圆 `--chip-bg` + `luc-paw-print` 56rpx → 24rpx → 28rpx 标题 → **24rpx 副标 `--color-text-secondary`** → 24rpx → ghost 按钮 |
| `H-SHELF-ERROR` | Shelf 位置 inline 错误条：`--state-error-surface` + `luc-alert-triangle` 28rpx + 26rpx「加载失败」+ `--text-link`「重试」；**不遮挡 Hero** |
| `H-SHELF-ERROR-KEEP` | 旧商品保留 + 顶部错误条 |
| `H-REFRESHING` | 旧内容保留 + 顶部 2rpx 品牌进度线 |
| `H-OFFLINE` | 顶部网络条（`--state-error-surface` + `luc-wifi` 20rpx + 「当前无网络连接」+ 「重试」） |
| `H-SCAN-FAIL` | toast（现状已有） |
| `H-BANNER-BLOCKED` | toast（现状 `webview-guard.js` 已有） |

> ⚠️ **home 无未登录态** —— 游客可完整浏览；收藏 / 加购在商品详情页内做 L2 拦截，不在首页。

**COMMUNITY · 19 态**

| 状态 ID | 视觉表达（Phase 1 token） |
|---|---|
| `C-INIT` | 页面底，无内容 |
| `C-FEED-LOADING` | 3 个骨架帖：64rpx 圆头像 + 2 条文字条 + 1 个 4:3 图占位（`--skeleton-gradient`） |
| `C-FEED-READY` | **第 1 条 = Lead Post**（4:3 满宽 + 30rpx/400/1.7 正文 3 行 + 互动行）；其余 = 碎片（左 3:4 图 200rpx + 右文字组） |
| `C-FEED-EMPTY` | 120rpx 圆 `--chip-bg` + `luc-sparkles` 56rpx + 28rpx 标题 + **副标 `--color-text-secondary`** + 主按钮「发布第一条」 |
| `C-FEED-ERROR` | 顶部 inline 错误条（`--state-error-surface` + `luc-alert-triangle` + 「内容加载失败」+ 「重试」） |
| `C-FEED-ERROR-KEEP` | 旧帖保留 + 顶部错误条 |
| `C-LOADING-MORE` | 底部 3 点脉冲 `--color-primary` |
| `C-NO-MORE` | 底部「— 已经到底了 —」+ 1rpx `--divider-weak` 居中 120rpx 宽 |
| `C-LOADMORE-ERROR` | 底部 inline 小错误条 + 「重试」 |
| `C-TOPICS-LOADING` | 横向条位置 6 个 56rpx `--skeleton-base` pill |
| `C-TOPICS-READY` | 横向 chip 条（`--chip-bg` 底 / 点击 `--chip-accent-bg` + `--chip-accent-text`），≤ 8 个 |
| `C-TOPICS-EMPTY` | **5 个本地兜底 chip**（现状已有），**不显示错误条**（辅助信息失败不阻塞主流程） |
| `C-FOLLOW-UNAUTH` | 96rpx 圆 `--chip-bg` + `luc-user` 48rpx + 「登录后查看你关注的人」+ ghost「去登录」 |
| `C-FOLLOW-EMPTY` | 120rpx 圆 `--chip-bg` + `luc-users` 56rpx + 「还没有关注的人」+ **副标 `--color-text-secondary`** + 主按钮「去发现」 |
| `C-TOPIC-EMPTY` | 120rpx 圆 `--chip-bg` + `luc-tag` 56rpx + 「选一个话题看看」+ ghost「回到推荐」 |
| `C-TOPIC-UNSELECTED`（新增） | 话题 tab **未选话题**时的默认态：说明文案 + **话题 chip 选择器网格**（复用 §5.2.2 ③ chip 规格，数据源 `GET /api/v1/community/topics`）；**不是空态**，是筛选器 |
| `C-TOPIC-SELECTED`（v1.7 补入） | 话题 tab **已选话题**态：chip 选择器收起为**顶部一行已选 chip**（`--chip-accent-bg` 底 + `--chip-accent-text` 字 + luc-x 20rpx 清除按钮），下方为按该话题过滤出的帖子流（Lead Post + 碎片，与 `C-FEED-READY` 同构）；**右侧不再显示话题选择器** |
| `C-POST-ACTION` | 点赞：`luc-heart` 立即变 `--color-danger` + 240ms 心跳；删除：该帖淡出 |
| `C-REPORT-SHEET` | Action Sheet（骚扰 / 广告 / 色情） |
| `C-REPORT-DONE` | toast「已提交」 |
| `C-LIKE-UNAUTH` | **L2 modal**：「登录后即可点赞」+「去登录」/「再看看」；`confirmColor` 用字面量品牌色（§6 约束 2） |

> ⚠️ **社区 2 个已确认的隐性 bug（Phase 2 §4.2.4 CC1 / CC3，Phase 3 必须修）**
> | # | 现状问题 | 裁定 | 视觉影响 |
> |---|---|---|---|
> | **B1** | 三个 tab 的**顶部结构不一致**（搜索条 + 话题栏只在「推荐」显示） | **搜索条 3 个 tab 都有**（社区搜索是全局能力）；**话题栏只在「话题」tab 显示**（它是话题 tab 的筛选器，放在推荐 tab 属错位）；「关注」tab 顶部留 24rpx 间距（无筛选器） | 三个 tab 切换时**顶栏以下的第一屏高度会变** → 原型必须体现这一点，否则 Phase 3 会出现"切 tab 布局跳动" |
> | **B2** | **`feedCursor` / `feedSnapshotId` / `feedVersion` 是单份**，切 tab 会串游标 | **按 tab 分别保存**（推荐 / 关注 / 话题 各一套游标）；切 tab **清空旧帖**并重新加载、**滚动位置归零** | 串游标时会出现"加载更多拉回上一 tab 的数据"，视觉上表现为**帖子内容与 tab 标签不符** —— 这是内容正确性 bug，不是纯视觉问题，但会以视觉 bug 的形式暴露 |
| **B3**（v1.5 新增） | **「话题」tab 从未真正按话题过滤** —— `community.vue:348` `tab: this.activeTab === 'follow' ? 'follow' : 'recommend'`（话题 tab 也传 `recommend`），且 `params` **从不传 `topic`** | **真传 `topic` 参数**（后端**零改动**已支持：`CommunityController:56` 收 `topic` → `CommunityFeedServiceImpl:342-344` `wrapper.eq(...getTopic, normalizedTopic)`） | ⚠️ **后果**：`C-TOPIC-EMPTY` 空态**当前是死代码，永不触发**（点话题 tab 与点推荐 tab 返回完全相同的帖子流）。另：**文案 bug** —— `community.emptyTopic` =「选择上方话题标签查看帖子」但**上方没有话题标签**（Topic Rail 按 §5.2.1 只在推荐 tab）→ 用户找不到指针。修正文案见 Phase 2 §4.2.4（11 条 i18n key，中英两份） |
| **B4**（v1.5 新增） | **产品线 ID 双命名冲突 + 徽标无色** —— `goods/detail.vue:370` 的 map 与 DB 权威**1/2 相反**；`tabbar/category.vue:79` 生成 `tag-milo` 系列而 `common.scss` 无此类 → **分类页商品卡的产品线徽标当前完全没有配色** | 见 **§4.4.2** 唯一真值源表 + B1/B2 修法 | 会导致 chip **挂错色**（MILO 显示成 CARE 绿金 / LUNA 显示成 GEAR 灰绿），是**品牌识别错误**级问题 |

**MESSAGES · 14 态**

| 状态 ID | 视觉表达（Phase 1 token） |
|---|---|
| `M-UNAUTH` | **同一个 `--surface-elevated` 收件箱容器内**的一段：96rpx 圆 `--chip-bg` + `luc-user` 48rpx + 「登录后查看消息」+ **副标 `--color-text-secondary`** + 主按钮「去登录」 |
| `M-LOADING` | 通知段：4 个 56rpx `--skeleton-base` 方块 + 4 条 80rpx 骨架条；私信段：4 行骨架（88rpx 圆 + 2 条文字条 100%/50%） |
| `M-READY` | 单一 `--surface-elevated` 容器（24rpx 圆角，无阴影）；上段通知聚合（4 宫格 + 红点）；下段会话列表（行高 ≥112rpx，88rpx 头像，**左侧 6rpx 金色竖条**标记未读） |
| `M-EMPTY-NOTICE` | `luc-inbox` 56rpx + 「还没有通知」+ **副标 `--color-text-secondary`** + ghost「去逛逛」 |
| `M-EMPTY-DM` | `luc-message-square` 56rpx + 「还没有会话」+ **副标 `--color-text-secondary`** + 主按钮「发起私信」 |
| `M-CONV-ERROR` | **只在私信段内** inline 错误条；**通知段照常显示**（部分失败不整体崩） |
| `M-LOADING-MORE` | 底部 3 点脉冲 |
| `M-NO-MORE` | 「加载更多」按钮消失（现状用显式按钮，Phase 2 §4.3.4 MM6 裁定保留） |
| `M-WS-OFFLINE` | 容器**下方**一行 22rpx tertiary + `luc-wifi` 20rpx「实时连接已断开，刷新以同步」；**不做全屏错误、不占位** |
| `M-UNREAD` | 会话行：金色竖条 6rpx + 标题字重 600 + 摘要截断更短；未读数字 `--chip-bg-strong` 底 + `--chip-text-strong` 字 |
| `M-SWIPED` | 该行 `translateX(-140rpx)`，露出 `--color-danger-strong` 底删除按钮（**深色下必须 Charcoal 字**） |
| `M-DELETE-CONFIRM` | Modal：标题「删除会话」+ 正文 + 按钮「删除」/「取消」；`confirmColor` 用 danger 字面量 |
| `M-PINNED` | 会话名前 `luc-bookmark` 24rpx `--color-primary`；排序置顶优先 |
| `M-NEW` | 新消息会话在列表顶部插入时给 180ms fade + translateY(8rpx) |

> ⚠️ **messages 未登录与登录后共用同一容器** —— 消除布局跳变（Phase 2 §4.3.4 MM1）。

**USER · 9 态**

| 状态 ID | 视觉表达（Phase 1 token） |
|---|---|
| `U-UNAUTH` | 账户条位置 = 默认头像 + 「登录 / 注册」32rpx sans 600 + **副标 `--color-text-secondary`** + 金色主按钮「登录」（`--color-primary` 底 + `--text-on-accent` 字，72rpx，pill）。**下方隐藏会员卡 / 钱包带 / 社交带 / 订单带**；功能清单保留，登录相关项加 `luc-lock` 24rpx tertiary |
| `U-LOADING` | 会员卡位 `--img-placeholder` 渐变块（**224rpx 同高，不先渲染渐变卡再变**）；钱包 / 社交数字 36rpx 高 `--skeleton-base` 条；订单图标 44rpx 方块骨架；清单图标 56rpx 方块骨架 |
| `U-READY` | 账户条（通栏，1rpx `--divider-weak` 底，48rpx 32rpx 内边距）→ 会员卡（`--brand-gradient-vip` + `--shadow-brand` + 44rpx 等级名 + 56rpx 成长值 % + 4rpx 刻度）→ 钱包带（`--surface-elevated` + 24rpx + 列间 `--divider-weak`）→ 社交带（**无底色**，32rpx 裸图标）→ 订单带（`--surface-elevated`）→ 功能清单 2 组 → 品牌页脚 |
| `U-MEMBER-ERROR` | 会员卡位置 inline 错误条（`--state-error-surface` + 24rpx 圆角 + `luc-alert-triangle` 28rpx + 26rpx「会员信息加载失败」+ `--text-link`「重试」）；**其余区块正常显示旧数据或 0** |
| `U-COUNT-ERROR` | 仅对应区块内 24rpx **`--color-text-secondary`** 小字「加载失败，点击重试」；**不弹 toast** |
| `U-ZERO` | **正常显示「0」，不显示骨架**；订单带不显示角标 |
| `U-REFRESHING` | 旧内容保留 + 顶部进度线 |
| `U-SESSION-EXPIRED` | 全局 modal「登录已过期」/「您的登录状态已失效，重新登录后将返回此页面」 |
| `U-NETWORK` | 顶部网络条 |

> ⚠️ **user 未登录时 4 个带全部隐藏**（会员卡 / 钱包带 / 社交带 / 订单带）—— 0 关注 0 粉丝同样是误导。
> ⚠️ **「0」是正常值，不是空态** —— 正常显示 0，骨架只用于"未知"。

**PET · 9 态**

| 状态 ID | 视觉表达（Phase 1 token） |
|---|---|
| `P-NO-PET` | 场景内 hint 区 = 96rpx 圆 `--chip-bg` + `luc-paw-print` 48rpx + 「还没有宠物档案」+ **副标 `--color-text-secondary`** + 主按钮「添加宠物档案」 |
| `P-READY` | 场景 + 玻璃浮层（`.scene-info` 标题 **sans 600** + kicker）+ 宠物切换条 + 装扮 / 3D 空间 pill + 护理卡 3 张 |
| `P-DRESS-OPEN` | 装扮弹窗（结构不改） |
| `P-DRESS-SAVED` | toast「装扮已应用」 |
| `P-MODEL-MISSING` | toast（现状已有） |
| `P-MODEL-ERROR` | toast（现状已有） |
| `P-CARE-OFF` | `.care-value` 加 `.muted`（现状已有） |
| `P-CARE-DUE` | 提醒条（现状已有） |
| `P-CARE-ERROR` | 护理卡位置 inline 错误条 + 「重试」；**宠物档案与场景照常显示** |

> **原型提示**：pet 的 3D 场景与弹窗交互在原型里**只还原视觉层次，不还原交互**（§6.13.3）；原型的状态切换用静态切换按钮演示。

#### 5.6.1 发布页「草稿图片丢失」提示（v1.9 补 · 非 tabBar 页，视觉以 Phase 2 为准）

> 发布页 `pages/community/create.vue` **不是 tabBar 页**，不在本规范 5 页范围内。登录回跳后**本地图片路径无法持久化** → 图片会丢。**视觉沿用 Phase 2 §3.5.4 定义**，此处仅登记要点，供 Phase 3/4 保持与社区页错误提示的一致性：

| 项 | 规格（**以 Phase 2 §3.5.4 为准**） |
|---|---|
| 形态 | 发布页**顶部**一条 inline 提示条 |
| 底 | `--state-error-surface` |
| 图标 | `luc-alert-triangle` 28rpx，`--color-danger` |
| 正文 | 24rpx **`--color-text-secondary`** |
| 按钮 | 右端文字按钮「重新选择图片」，`--text-link` |
| 文案 | 「登录后本地图片无法保留，文字已保存，图片需重新选择」 |
| 消失 | 用户重新选图后自动消失 |

> ⚠️ **不可静默丢图**：用户写了长文 + 多图，回跳后只剩文字且无任何说明 → 会以为 App 吃掉内容 → 信任崩塌。Phase 2 已定为 🔴 必做项。
> ⚠️ **形态一致性**：这条提示与社区页的 inline 错误条**同形态**（`--state-error-surface` 底 + luc-alert-triangle + secondary 正文 + link 按钮），只是位置在页面顶部而非内容流内 —— 不要另起一套视觉。

---

## 6. E. 实现约束（uni-app 3 + Vue3 · 前端工程师必读）

> 这些不是"建议"，是 uni-app 的物理限制。违反其中任何一条都会导致**真机与 H5 表现不一致**或**渲染失败**。

### 约束 1 · APP 端禁止用 SVG 做图标 / 占位图

- `lucide.css` 的图标字体是唯一可靠方案：`<text class="luc luc-search" />`（**必须同时带 `luc` 基类**，否则 `font-family` 不生效）。
- 占位图用 **CSS 渐变块**（`--img-placeholder`）+ lucide 图标，**禁止** `data:image/svg+xml;utf8,...`。
  - 现状 `community.vue` 的 `resolveImageUrl()` 与 `onImageError()` 正在用 SVG data-uri。⚠️ **v1.4 实测补充（baseline-capture）**：**H5 端本次渲染正常**（社区帖实际渲染出 1080×1440 实拍配图，未走占位分支），**但 APP 端未验证** —— 这条仍按「APP 端可能空白」处理，不要因为 H5 正常就保留。改为：图片容器常驻 `--img-placeholder` 背景，`@error` 时隐藏 `<image>` 并显示 luc-image 图标层（该方案两端都安全）。
- 只允许在 `renderjs` 里用 SVG / Canvas；本套视觉规范不依赖任何 canvas。

### 约束 2 · 原生组件不吃 CSS 变量

| 位置 | 现状 | 必须怎么做 |
|---|---|---|
| `swiper` 的 `indicator-active-color` / `indicator-color` | `home.vue` 写死 `#2E2B29` / `rgba(46,43,41,0.3)` | **必须写字面量**：浅色 `#DBC98A` / `rgba(46,43,41,0.18)`；深色 `#C9B47A` / `rgba(255,255,255,0.22)`。**推荐**：关掉 `indicator-dots`，在 swiper **外部**自绘指示器（这样能随主题切换、能改形状、能做 3 段短线） |
| `uni.setTabBarStyle` | `theme.js` 已有 `TAB_BAR_PALETTE` | 保持现状：每页 `onShow` 调 `useThemeStore().syncTabBar()`（5 页都有）。原生 tabBar 背景/选中色**不能**用 CSS 变量 |
| `uni.showModal` 的 `confirmColor` | `community.vue` 写死 `#ff4d4f` | 改字面量品牌色：浅 `#8A7844` / 深 `#C9B47A`（同样不能是变量） |
| `uni.showToast` 的 `icon` | — | 仅 `'success' / 'none' / 'error'`，无法自定义品牌图标；品牌化提示请自绘 |

### 约束 3 · `uni-image` 自带固定高度，grid 中 `aspect-ratio` 失效

- **1:1 方图**：外层 `position:relative; padding-top:100%`，内层 `<image>` `position:absolute; inset:0; width:100%; height:100%`（现状 `home.vue` `.recommend-image-wrap` 已是此写法，正确，沿用）。
- **多列网格**：用 `grid-auto-rows: calc((100vw - 64rpx - 24rpx) / 3)` 计算方格边长，**不要**依赖 `aspect-ratio`（现状 `community.vue` 已用 vw 计算，正确，沿用）。
- **强制填满**：uni-image 内部是 `uni-image > div > img`，需同时覆盖两层：
  `::v-deep div` 与 `::v-deep img` 都设 `width/height:100% !important; object-fit:cover !important`（现状社区已写，注意 `::v-deep` 与 `:deep()` 两种写法在 APP/H5 编译结果不同，**全项目统一用 `:deep()`**）。
- **按原图比例**（社区单图）：用 `mode="widthFix"` + `height:auto`；代价是图片加载完成前高度为 0 会跳版 → 建议给容器 `min-height` 或改为固定 4:3 + `aspectFill`。
- **`mode` 取值**：`aspectFill`（裁切填满）/ `aspectFit`（完整显示留白）/ `widthFix`（高度自适应）/ `heightFix`。商品卡一律 `aspectFill` + 固定比例容器。

### 约束 4 · `scroll-view` 与 `swiper` 嵌套在 Android 有 touch-slop 陷阱

- 现象：Android WebView 把 touch-slop（≈20px）用于判定"切断惯性滚动"，体感变成"先下滑一点才能上滑"。
- **规则**：页面级纵向滚动 → 用普通 `<view>` + uni-app page 原生接管（`home.vue` / `community.vue` 已正确改掉，**保持**）。
- **横向** `scroll-view scroll-x` 是允许的（社区话题栏），但：
  - 必须 `white-space: nowrap` + 子项 `display:inline-flex`（否则 APP 端会换行）；
  - 横向条内部**不要**再嵌 `swiper`；
  - 横向条与页面纵向滚动共存时，页面仍须由原生接管。
- **`scroll-view` 内的事件**：APP 端 `scroll-view` 会吞掉部分 click（现状 `user.vue` 已记录该 bug：登录按钮必须单独绑 `@click.stop`）。新增交互时**一律给按钮自身绑事件**，不要依赖父级冒泡。
- **pet 页的缓解项（O7，v1.10 补）**：pet 页同样是 `scroll-view` 纵向滚动 + 内嵌横向 `scroll-view`，是 §6 约束 4 touch-slop 风险的高发页。**Phase 4 只需给 `.page-scroll` 加 `enable-flex`**（横向部分 `.pets-scroll` / `.pets-inner` 现状已具备 `nowrap` + `inline-flex`）。⚠️ **验收必须在 Android 真机做** —— H5 通过不代表 APP 通过，这是本项目反复出现的跨端差异。

#### 4.1 `user.vue` 滚动方案：甲 / 乙 双方案并列规范

> 现状 `user.vue` 用 `<scroll-view class="user-scroll" scroll-y>` + `height: 100vh`，而 `home` / `community` 已改用普通 `<view>` + page 原生接管。Phase 2 正在评估统一。下表给出两方案的**完整视觉规格差异**，Phase 3 按 Phase 2 结论**直接二选一落地即可**，不需要再做技术选型。

##### 两方案总览

| 维度 | **方案甲（保留 `scroll-view`）** | **方案乙（改原生 page 滚动）** ← 推荐 |
|---|---|---|
| 触发方式 | `<scroll-view scroll-y>` 包裹整页 | 普通 `<view>`，由 uni-app page 原生接管 |
| pages.json | 不动 | ⚠️ **v1.5 更正（ux-designer 核实）**：`pages.json` **已经配了** `"enablePullDownRefresh": true`（`pages/tabbar/user`），**无需改动** |
| 下拉刷新 | **双入口**：`refresher-enabled` + page 级 `onPullDownRefresh` 都要接 | **单入口**：仅 page 级 `onPullDownRefresh` |
| 滚动容器高度 | `height: 100vh`（写死） | 由内容撑开，无固定高度 |
| 与其它 3 页一致性 | ❌ 不一致（home/community/messages 已是原生） | ✅ 一致 |
| 手势冲突风险 | ⚠️ 高（scroll-view 内手势 + 下拉手势互相干扰） | ✅ 无 |
| 需额外处理的坑 | 双下拉入口、tabBar 遮挡、登录按钮绑事件兜底 | 仅 tabBar 遮挡 padding-bottom |
| 实现改动量 | 小（现状保留） | 中（改模板根节点 + 改 pages.json + 删 refresher 属性） |

##### 视觉规格差异（**逐项对照**）

| 规格项 | 方案甲 | 方案乙 | 差异说明 |
|---|---|---|---|
| **滚动容器高度** | `.user-scroll { height: 100vh; }` —— 固定占满一屏，内容超出时由 scroll-view 内部滚动 | `.user { min-height: 100vh; }` —— 内容不足时仍占满一屏，超出时页面整体滚动 | 视觉表现一致，差异在滚动条归属 |
| **首屏内容起点** | scroll-view 内部从 `0` 开始，**顶部安全区由 `.header` 的 `padding-top: calc(48rpx + max(--status-bar-height, env(...)))` 撑开** | 同上（`.header` 仍在文档流首位） | 一致，不需调整 |
| **吸顶行为** | ⚠️ `scroll-view` 内的 `position: sticky` **在 APP 端不可靠**（滚动容器自身在滚动，sticky 参照系混乱）→ **账户条无法吸顶**，滚动即消失 | ✅ 账户条设为 `position: sticky; top: 0` 即可吸顶，与 home/community 顶栏行为一致 | **这是两方案最实质的视觉差异**：方案甲下账户条会跟着滚走 |
| **首屏「账户条 + 会员卡」是否必然可见** | ⚠️ 不保证：scroll-view 内部若有任何 `padding-top`，或 `100vh` 在有 tabBar 的情况下超出可视区，会把会员卡顶出首屏 | ✅ 保证：账户条与会员卡在文档流前 432rpx 内，原生 tabBar 遮挡不影响顶部 | 需要用 `padding-bottom` 解决底部遮挡，顶部不受影响 |
| **tabBar 遮挡处理** | scroll-view 有 `overflow:hidden`，原生 tabBar 浮在 scroll-view **之上** → 需要给 `.user` 加 `padding-bottom: calc(112rpx + env(safe-area-inset-bottom, 0px))` | 同左（page 滚动时原生 tabBar 同样浮在内容之上） | **两方案完全相同**，这是最容易漏的坑：去掉 scroll-view 后，最后一项（品牌页脚）会被 tabBar 盖住 |
| **下拉刷新视觉** | `refresher-enabled` 的刷新头是**原生样式**，与页面设计不搭；且与 page 级 `onPullDownRefresh` 会**同时触发**（现状靠 `doRefresh()` 复用逻辑兜底） | 仅原生下拉转圈（`uni.stopPullDownRefresh()` 收尾），无自定义刷新头 | 方案乙少一个视觉噪点源 |
| **刷新触发手感** | scroll-view 内的 refresher 需要在 `scroll-view` 上触发，未滚动到顶部时手势不响应 → 用户容易以为下拉失灵 | page 原生下拉，任何滚动位置都可触发 | 方案乙体验更可靠 |
| **登录按钮点击** | ⚠️ **必须**给按钮自身绑 `@click.stop`（现状已记录此 bug：外层 view 的 click 会被 scroll-view 下拉手势拦截） | ✅ 回归常规冒泡，父级 `.login-prompt` 绑 `@click` 即可 | 方案乙可移除这条兜底 |
| **横向手势** | scroll-view 内的横向滑动（如未来加横滑模块）会被纵向滚动抢走 | 无此问题 | — |
| **空态/骨架屏的定位** | 骨架屏在 scroll-view 内，需要 `min-height` 保证滚动条不抖动 | 同左；但页面原生滚动下更简单 | — |

##### 两方案的**共同视觉规格**（无论选哪个都必须满足）

| 项 | 规格 |
|---|---|
| 账户条（Account Header） | 通栏、无圆角、无阴影，底边 1rpx `--divider-weak`，内边距 `48rpx 32rpx`，`padding-top: calc(48rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)))` |
| 账户条吸顶 | **方案甲不做吸顶**（技术不可靠）；**方案乙做吸顶**，吸顶时加 `backdrop-filter: blur(20rpx)` + 背景换成 `--color-surface-veil-95` |
| 页面底部留白 | `.user { padding-bottom: calc(112rpx + env(safe-area-inset-bottom, 0px)); }` —— **两方案都要写，这是 tabBar 遮挡的唯一防线** |
| 品牌页脚位置 | 距页面底部 40rpx + 安全区 |
| 骨架屏 | 会员卡位用 `--img-placeholder` 占 224rpx 同尺寸；数字位用 `--skeleton-base` 条；**不先渲染渐变卡再变**（防闪） |

##### Phase 3 落地指引

```
若 Phase 2 结论 = 保留 scroll-view（甲）：
  1. 保留 <scroll-view scroll-y class="user-scroll">，保留 height:100vh
  2. 保留 refresher-enabled + page 级 onPullDownRefresh 双入口，都委托 doRefresh()
  3. 保留登录按钮自身 @click.stop 兜底
  4. 账户条不做吸顶（删掉 position:sticky，避免 APP 端行为不一致）
  5. .user 加 padding-bottom: calc(112rpx + env(safe-area-inset-bottom, 0px))

若 Phase 2 结论 = 改原生 page 滚动（乙）：
  1. 根节点改普通 <view class="user">，删掉 scroll-view 包裹与 refresher 属性
  2. ~~pages.json 的 pages/tabbar/user 增加 "enablePullDownRefresh": true~~ → **v1.5 更正：已存在，无需改动**（ux-designer 核实 `pages.json:52`）
  3. 账户条改为 position:sticky; top:0; z-index:20，加 backdrop-filter + veil-95 背景
  4. 登录按钮可回归父级冒泡（但保留 @click.stop 亦无害）
  5. .user 加 padding-bottom: calc(112rpx + env(safe-area-inset-bottom, 0px))
```


### 约束 5 · APP 端逻辑层没有 DOM

- SVG / Canvas / `document` 只能在 `renderjs` 中使用；逻辑层（Vue 组件）不能测量 DOM。
- 任何"根据内容动态计算高度"的方案在 APP 端不可行 → 本规范的所有比例（1:1 / 4:3 / 3:4 / 16:9）都用**固定百分比或 vw 公式**表达，不依赖运行时测量。

### 约束 6 · Playfair Display 字体方案（**已定案：方案 A**）

#### 6.6.1 现状与决策

- 现状：`common.scss` 只写了 `font-family: 'Playfair Display', 'Inter', serif`，但**全项目没有任何 `@font-face`、没有 woff2/ttf 文件、没有网络字体引用** → 实际渲染的是 fallback（iOS: Times/宋体；Android: Noto Serif/默认 serif）。
- **主理人决策（Phase 2 定案）：走方案 A** —— 把 Playfair Display 字体文件打进 `src/static/fonts/`，在 `common.scss` 中以 `@font-face` 声明。`lucide.css` 的 `@font-face` 已验证此路径在 H5 与 APP 双端可行。
- **附加约束（重要）**：真机可能处于内网 / 离线环境，**网络字体不可用**。因此 `@font-face` 必须配 `font-display: swap` 与完整 fallback 链，保证字体未加载时排版不塌、不跳动、不回退成无衬线。

#### 6.6.2 `@font-face` 写法规格

| 项 | 规格 |
|---|---|
| 字体文件路径 | `src/static/fonts/playfair-display-latin.woff2`（+ 必要时 `.ttf` 兜底） |
| `font-family` | `'Playfair Display'` |
| `src` 写法 | `url('@/static/fonts/playfair-display-latin.woff2') format('woff2')` —— **必须用 `@/` 别名**，与 `lucide.css` 保持一致；APP 端 uni 编译器会把字体原样打进 `_www`，原生 WebView 直接加载 |
| **`font-display`** | **`swap`**（必需）。`swap` = 字体未就绪时先用 fallback 渲染、加载完再替换；**不要用 `block`**（会白屏 3s），**不要用 `optional`**（可能永远不换） |
| `font-weight` | `400 700`（variable range）或分档声明 `400` + `500` + `600`；**只声明实际用到的字重**，Playfair Display Bold 体积显著更大 |
| `font-style` | **仅 `normal`**（v1.5 裁定：全 App 不使用斜体 —— slogan 改为正体，见 §6.6.7）→ **不需要** `playfair-display-latin-italic.woff2`，**省下一个字体文件** |
| `unicode-range` | `U+0000-00FF, U+0131, U+0152-0153, ...`（Latin 子集范围）—— 防止浏览器为 CJK 字符回退时误判缺字 |

#### 6.6.3 体积控制（**硬性**）

| 规则 | 说明 |
|---|---|
| **必须子集化** | 只保留 Latin 基本区 + Latin-1 补充 + 常用标点（`U+0020-007E`、`U+00A0-00FF`）。用 `pyftsubset` 或 `fonttools subset` 生成 |
| **禁止打包 CJK 字体** | Playfair Display 不含 CJK；**绝对不要**为中文标题引入 CJK 衬线字体（思源宋体全字库 8MB+，中文字体子集即使裁到 200 字也要 300KB–1MB） |
| **中文由系统承接** | 中文标题走 `Songti SC`（iOS/macOS）→ `STSong`（Android）→ `Noto Serif CJK SC` → `serif` 的系统字体链，**零打包成本** |
| **体积上限** | Playfair 全部字重 + 斜体合计 **≤ 120KB**（子集化后）。超过说明子集范围没裁干净 |
| **不做运行时字体下载** | 不引入任何 CDN 字体、字体加载器、JS 字体切换逻辑 —— 内网真机不可用，且首屏会闪 |
| **不使用 `unicode-range` 分包策略** | 单一 woff2 即可，不要为"按需加载"设计多包机制（复杂度收益比不划算） |

#### 6.6.4 fallback 链规格（**字体没加载上时排版不塌**）

| 角色 | 完整链 | 说明 |
|---|---|---|
| `--font-display`（展示体） | `'Playfair Display', 'Georgia', 'Songti SC', 'STSong', 'Noto Serif CJK SC', serif` | Georgia 是 Latin 首选 fallback（字形与 Playfair 同源气质，都是过渡衬线）；中文依次落到宋体 |
| `--font-sans`（正文体） | `'Inter', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif` | 保持现状，不动 |
| 数字 | 展示体数字优先 `font-variant-numeric: oldstyle-nums` 可选 | Playfair 默认是 lining figures；会员积分这类大数字**建议**显式指定 `lining-nums` 避免视觉跳动 |

#### 6.6.5 排版不塌的三条保证

1. **所有展示体位置都已按 fallback 成立设计**（字距、行高、居中方式、容器高度均不依赖具体字形）—— 见 §4.8、§5.1.2、§5.2.2、§5.4.2。
2. **不依赖 Playfair 的独有特性**：不使用 swash、small caps、oldstyle figures 等只在 Playfair 存在的特性；小标题的"小型大写"效果由 `text-transform: uppercase` + `letter-spacing` 模拟（见 `--tracking-kicker`）。
3. **字体切换不引起布局跳动**：所有展示体文本的容器高度由 `line-height` 固定（1.1–1.3），而非由字体实际高度撑开；`swap` 生效时最多一次字形替换，不产生位移。

#### 6.6.7 中文 slogan 的斜体问题（v1.5 裁定 · 回答 ux-designer 开放项 O11）

**问题**：slogan 文案是中文（如「每段旅程，与你同行。」）→ Playfair Display **不含中文字形** → 中文落到 `Songti SC` / `STSong` → **宋体没有真斜体** → 浏览器只能做**伪斜体**（算法倾斜），汉字笔画会歪斜失真。

**裁定：全 App 不使用斜体，slogan 改为正体。**

| 项 | 裁定 | 理由 |
|---|---|---|
| `font-style: italic` | **不使用**（`@font-face` 只声明 `normal`） | 斜体是字体的一种**真实字形**；伪斜体是**算法倾斜**，属渲染缺陷不是设计。汉字笔画在倾斜后重心偏移、撇捺角度失真，中文排印里几乎总是负面的 |
| 英文 locale slogan | `--font-display` **正体** 26rpx | Playfair 正体本身已有编辑感；不需要靠倾斜 |
| 中文 locale slogan | `--font-sans` **正体** 字重 **300** 26rpx | 细字重 + `--tracking-kicker` 字距同样传递"编辑感"，且不引入伪斜体 |
| 编辑感来源（替代斜体） | ① 细分割线（`--divider-strong` 1rpx）② 字距 `--tracking-kicker` ③ 左右留白 48rpx ④ 上下对称留白 | 编辑感来自**排版节奏**而非字形倾斜 —— 这与 §2「高端感来自减法与秩序」的论证一致 |
| 字体文件 | **省掉** `playfair-display-latin-italic.woff2` | 少一个字体文件 = 少一份体积与一份加载失败风险 |
| `DEFAULT_LOCALE = 'en-US'` | 英文是主路径，中文是切换后的次路径 | 两条路径都必须成立，不能只在主路径好看 |

> **给 ux-designer 的答复**：**不会削弱编辑感定位** —— 反而更强。斜体是"给字加装饰"，编辑感是"给版面加秩序"；后者在中文语境下才是成立的。§2 的品牌论证（P4 图片是主角 / 留白节奏 / 减法与秩序）本身也不依赖倾斜字形。**建议 Phase 2 §6.2 的验收标准同步改为"正体 + 字距"，不再要求斜体。**

#### 6.6.6 验收清单（Phase 3 必须逐条验）

- [ ] `src/static/fonts/` 下存在子集化 woff2，体积 ≤ 120KB
- [ ] `@font-face` 含 `font-display: swap` + `font-weight` 覆盖实际用到的字重 + 独立 italic 文件
- [ ] 断网 / 离线真机下打开 App：展示体文字**仍然可读、层级不塌**，无 FOIT 白屏
- [ ] 浅色 + 深色两套下展示体标题均正常（深色下 `--color-primary` + Playfair 组合对比度 7.28:1 ✅）
- [ ] 中文标题落到 `Songti SC` / `STSong`，不出现豆腐块（□）
- [ ] `common.scss` 中不再有裸的 `font-family: 'Playfair Display'`（必须走 `--font-display` 变量）
- [ ] **全项目无 `font-style: italic`**（v1.5：中文伪斜体是渲染缺陷；slogan 走正体 + 字距，见 §6.6.7）
- [ ] 中英文 slogan 在各自 locale 下均**不倾斜**，且层级清晰（英文展示体正体 / 中文 sans 300）


### 约束 7 · 图标字体的可用类名是封闭集合

- `lucide.css` 共 160 个类名，**新增图标前必须先确认类名存在**。容易踩的坑：
  - ✅ `luc-home` ✅ `luc-grid` ✅ `luc-layout-grid` ✅ `luc-shopping-bag` ✅ `luc-shopping-cart` ✅ `luc-package` ✅ `luc-clipboard-list` ✅ `luc-credit-card` ✅ `luc-check-circle` ✅ `luc-headphones` ✅ `luc-inbox` ✅ `luc-message-square` ✅ `luc-user-plus` ✅ `luc-users` ✅ `luc-bookmark` ✅ `luc-crown` ✅ `luc-calendar` ✅ `luc-target` ✅ `luc-map-pin` ✅ `luc-paw-print` ✅ `luc-footprints` ✅ `luc-help-circle` ✅ `luc-settings` ✅ `luc-sparkles` ✅ `luc-share-2` ✅ `luc-more-horizontal` ✅ `luc-image` ✅ `luc-alert-triangle` ✅ `luc-refresh-cw` ✅ `luc-wifi` ✅ `luc-lock` ✅ `luc-eye` ✅ `luc-tags` ⚠️ `luc-tag`（单数存在，`luc-tags` 复数**不存在**）
  - ❌ `luc-house`（用 `luc-home`） ❌ `luc-archive` ❌ `luc-shop` ❌ `luc-comment`（用 `luc-message-circle`） ❌ `luc-heart-break` ❌ `luc-notification`（用 `luc-bell`） ❌ `luc-user-round`（**`social-grid` 曾踩过这个坑，已改 `luc-user`**）
- `$luc(name)` 助手会自动补 `luc-` 前缀；传入 emoji 会生成 `luc-💳` → **渲染为空**（`user.vue` 现状 bug）。
- 图标字体是 monochrome：颜色靠 `color` 继承；**不要**试图给单个图标上多色。

### 约束 8 · 所有硬编码颜色必须归位（含兜底值）

**必须替换的硬编码清单**

| 文件 | 位置 | 现状 | 改为 |
|---|---|---|---|
| `user.vue` | `.vip-bg` | `linear-gradient(135deg, var(--color-primary, #007aff) 0%, #004fad 100%)` | `var(--brand-gradient-vip)` |
| **`user/mission-center.vue:455`** | **`.vip-bg`（同款蓝色渐变，iOS 蓝第二处泄漏）** | `linear-gradient(135deg, var(--color-primary, #007aff) 0%, #004fad 100%)` | `var(--brand-gradient-vip)` —— ⚠️ **v1.4 补入**（据 baseline-capture 实测定位）。只改 `user.vue` 会漏掉这一处 |
| `user.vue` | `.vip-card` | `box-shadow: 0 8rpx 32rpx rgba(0,122,255,0.3)` | `var(--shadow-brand)` |
| `user.vue` | `.vip-points` | `color: var(--color-primary)`（在深卡上 1.6:1） | `var(--color-text-inverse)` |
| **`goods/detail.vue:370`** | **`brandLine` map `{1:'MOYUYO CARE', 2:'MOYUYO GEAR', ...}`** | ⚠️ **v1.5 补入**：**1/2 与 DB 权威相反**（DB：1=MILO→GEAR、2=LUNA→CARE）→ MILO 商品被标成 CARE | 按 §4.4.2 唯一真值源改正：`{1:'MOYUYO GEAR', 2:'MOYUYO CARE', 3:'MOYUYO PLAY', 4:'MOYUYO HOME'}`<br>⚠️ **v1.6 补充：这与 `goods/list.vue:285` 的 `resolveIpName`（返回 IP 名 `MILO`/`LUNA`…）是两套东西，不要统一**：<br>· `resolveIpName` = **IP 名**（给筛选器用，与 `ipOptions: ['MILO','LUNA','ATLAS','OLIVE','Classic']` 对齐）<br>· `brandLine` = **语义线名**（给用户看的产品线文案）<br>两者可并存，但**各自只能有一份**，且都应从共享常量派生 |
| **`common.scss`** | **`.tag-milo` / `.tag-luna` / `.tag-atlas` / `.tag-olive` 类名不存在** | ⚠️ **v1.5 补入**：`tabbar/category.vue:79` 生成的是 `tag-milo` 系列 → **分类页商品卡的产品线徽标当前完全没有配色** | 按 §4.4.1 定义 **IP 别名 + 语义别名两套**，指向同一 chip 规格 |
| **`pet/pet-hub-3d.vue:220-255`** | 4 个 IP 的 `bgColor` / `textColor` 写死 **iOS 系统色**：`#007aff` / `#34c759` / `#ff9500` / `#af52de` | ⚠️ **v1.6 补入**：**第三套 IP 配色体系**（既非 DB `color_hint`，也非前端 4 条产品线色）；且此处品种（金毛/布偶/哈士奇/柯基）与 DB（灵缇犬/英短金渐层/杜宾犬/缅因猫）**完全不同** | **判定：这是合理的差异化需求**（3D 场景里 4 个角色必须互相可区分，高饱和色是对的），**不要求归入 4 条产品线色**。但必须：① 抽成 `--hub-ip-{milo,luna,atlas,olive}-bg/text` token（消除写死 iOS 蓝）；② **IP 元数据与 DB 收敛**（当前两套品种数据并存，需产品侧定哪套为准） |
| `user.vue` | `.card` | `box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.08)` | `var(--shadow-card-md)` |
| `community.vue` | `.fab` | `box-shadow: 0 8rpx 24rpx rgba(0,122,255,0.35)` | `var(--shadow-brand)` |
| `community.vue` | `.fab` | `color:#fff` | `var(--text-on-accent)` |
| `community.vue` | `.post-content` | `font-weight:600` | `400` |
| `community.vue` | `.tab-indicator` | 48rpx 中心短线 | 满宽 3rpx 线（见 §5.2.2） |
| `community.vue` | `resolveImageUrl` | `data:image/svg+xml` 占位 | `--img-placeholder` + lucide 图标层 |
| `messages.vue` | `.navbar-badge` `.notice-dot` `.section-badge` `.conv-unread` | `#ff4d4f` ×4 | `var(--color-danger)`（仅作图形/小圆点） |
| `messages.vue` | `.conv-swipe-btn` | `#ff4d4f` 底 + 白字（3.57:1 ❌） | `var(--color-danger-strong)` 底；**浅色用白字，深色用 `--text-on-accent` Charcoal 字** |
| `messages.vue` | `.login-btn` `.section-action` `.empty-btn` `.empty-btn-text` `.conv-avatar` | `var(--color-primary, #18b367)`（**绿色兜底**） | `var(--color-primary)` 兜底值改 `#DBC98A`；文字改 `var(--text-on-accent)` |
| `messages.vue` | `.notice-card` | `box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.06)` | `var(--shadow-card-md)` |
| `home.vue` | `.banner` overlay | `background: rgba(0,0,0,0.35)` 方块 | `var(--brand-gradient-ink)` 满幅渐变 |
| `home.vue` | swiper indicator | `#2E2B29` / `rgba(46,43,41,0.3)` | 浅 `#DBC98A` / `rgba(46,43,41,0.18)`；深 `#C9B47A` / `rgba(255,255,255,0.22)`；**推荐自绘** |
| `common.scss` | `.skeleton` | `#f0f0f0 → #f8f8f8` 写死 | `var(--skeleton-base)` → `var(--skeleton-highlight)` |
| `common.scss` | `.tag-care` `.tag-gear` `.tag-play` `.tag-home` | `rgba(产品线色, 0.15)` 底 + 产品线色字（1.40–2.45:1 ❌） | **v1.2 重写**：底 `var(--chip-bg)` + 字 `var(--chip-text-strong)` + 左侧 8rpx 产品线色圆角色条（见 §4.4.1） |
| `pet.vue` | `--hub-*` 6 个本地变量 | 写死 | 共享 token（见 §5.5） |

### 约束 9 · `backdrop-filter` 必须有不透明兜底

- `blur()` 在部分 Android WebView 不支持或性能极差。写法顺序：
  1. 先声明不透明底 `background: var(--color-surface-veil-95);`
  2. 再声明 `backdrop-filter: blur(20rpx);`
  3. 用 `@supports (backdrop-filter: blur(1px))` 内再覆盖为半透明 + 模糊。
- 仅顶栏与浮层使用；不要在长列表里用。

### 约束 10 · 安全区与原生 tabBar 遮挡

- 所有 sticky 顶栏统一：`padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px))`（**取较大值，不要相加**——iOS 上两者同值，相加会翻倍）。
- 页面最后一个可点元素必须留出：`padding-bottom: calc(112rpx + env(safe-area-inset-bottom, 0px))`（112rpx ≈ 原生 tabBar 50–56px + 余量）。
- 固定 FAB 的 bottom 同样基于该值。

### 约束 11 · `scoped` 与 `::v-deep` 的写法统一

- uni-app 3 + Vue3 编译器对 `::v-deep` 与 `:deep()` 支持不一致 → **全项目统一 `:deep()`**（现状 `home.vue` 与 `community.vue` 混用，需统一）。
- CSS 变量在 `scoped` 的 `<style lang="scss">` 中可用；但 **`:root` 中定义的变量无法穿透到原生组件内部**（如 `uni-image` 的内层 `div`），需用 `:deep()` 显式赋值。

### 约束 12 · 骨架屏与动画

- `@keyframes` 在 scoped 样式中正常编译；但**动画类名不要与 `common.scss` 的 `.skeleton` 冲突**（会互相覆盖 `background`）。
- 尊重 `prefers-reduced-motion`（`social-grid.vue` 已有此写法，各页骨架与脉冲点应统一加上）。

### 约束 13 · 新增 token 的深色值必须写入**两个**块（v1.3）

`src/styles/variables.scss` 的深色值目前存在**两份**（这是现有文件的既定结构，Phase 3 必须沿用）：

| 块 | 选择器 | 触发条件 |
|---|---|---|
| ① 系统深色 | `@media (prefers-color-scheme: dark) { :root:not(.theme-light) { ... } }` | 系统深色 **且** 用户未在 App 内强制浅色 |
| ② 强制深色 | `.theme-dark { ... }` | 用户在设置页显式选择深色 |

- **每个新增或修改的 token，两处都要写**，漏一处就会出现「系统深色下正常、App 内切深色失效」或反之。
- `:root:not(.theme-light)` 的特异性 (0,2,0) 高于 `.theme-dark` (0,1,0)，但两者在 `html` 上互斥，不会冲突 —— 沿用现有写法即可，不要改结构。
- **写死值（不跟随主题的 `--background-*` / `--text-*` / `--brand-*` 数字色阶）也要在两处分别定义** —— 现有文件已在做，延续。
- v1.2 / v1.3 新增的 **11 个 token** 逐条落地清单：

| Token | 浅色 | 深色（须写入 ① 与 ② 两处） |
|---|---|---|
| `--brand-gradient-vip` | `#7E6B36 → #6B5F3A → #554A26` | `#4A432C → #3A341F → #2A2619` |
| `--color-danger-strong` | `#B3574B` | `#D9836F` |
| `--surface-elevated` | `#FFFFFF` | `#322E2A` |
| `--surface-sunken` | `#F0ECE6` | `#211E1B` |
| `--surface-mute` | `#FAF8F5` | `#262320` |
| `--surface-inverse` | `#2E2B29` | `#F6F2EE` |
| `--chip-bg` | `#F0ECE6` | `#322E2A` |
| `--chip-bg-strong` | `#E6E0D6` | `#3B3630` |
| `--chip-accent-bg` | `#F5EFDD` | `#383122` |
| `--divider-weak` | `#F0ECE6` | `#2F2B27` |
| `--divider-strong` | `#E2DCD3` | `#453F39` |

> 另有 `--skeleton-base` / `--skeleton-highlight` / `--skeleton-gradient`（§4.6）、`--accent-soft` / `--accent-strong` / `--brand-gradient-ink` / `--brand-scrim-flat` / `--text-on-accent` / `--text-inverse` / `--text-secondary-strong` / `--text-link` / `--text-muted-inverse` / `--state-press` / `--state-hover` / `--state-disabled-bg` / `--state-disabled-text` / `--focus-ring` / `--img-placeholder*` 等，**同样两处都要写**。

---

### 约束 14 · H5 的 `page` 标题与 tabBar 文案**不受 CSS 字体变量影响**（v1.4）

- uni-app H5 端把 `pages.json` 的 `globalStyle.navigationBarTitleText` 与 `tabBar.list[].text` 渲染成**运行时组件**，走的是**系统字体栈**，**不继承 `page` 的 `font-family`**。
- **后果**：改 `--font-display` / `--font-sans` **不会**改变这两处文字的字体。Phase 3 原型里若把 tabBar 文案做成展示体，H5 实际渲染会与原型不一致。
- **处理原则**：
  | 位置 | 能否用展示体 | 说明 |
  |---|---|---|
  | 页面内**自绘顶栏**（本规范 5 页全部是 `navigationStyle: custom` 的自绘顶栏） | ✅ 可以 | 走 `page` / 组件内 CSS，字体变量生效 |
  | **`pages.json` 原生 `navigationBarTitleText`**（二级页用） | ❌ 不行 | 系统字体栈；要改只能改 `uni.setNavigationBarTitle` 之外的渲染方式（不推荐） |
  | **原生 tabBar 的 `text`** | ❌ 不行 | 系统字体栈；**本规范的 tabBar 文字保持系统字体即可**，不要为了统一而强行改造原生组件 |
- **推论**：§3 锚点表的「顶栏用 `--font-sans`」只约束**自绘顶栏**（5 个 tab 页都是自绘）；二级页的原生导航栏标题不在本规范管辖范围。
- **原型注意**：Phase 3 的 HTML 原型底部 tabBar 是**自绘**的，可以自由用字体；但 Spec 面板必须登记「真实实现中此处为系统字体栈」，否则 Phase 4 会照抄原型的字体到自绘 tabBar 上，造成"实现比原型更花"。

---

### 约束 15 · `switchTab` **路径后不能带参数**（v1.7 · 平台硬限制）

**官方类型定义原文**（`node_modules/@dcloudio/types/uni-app/uni/legacy/uni.d.ts:5391`）：

```
interface SwitchTabOptions {
  /**
   * 需要跳转的 tabBar 页面的路径，路径后不能带参数
   */
  url: string | string.PageURIString;
}
```

对照 `redirectTo`（同文件 L5353 区域）：「需要跳转的应用内**非 tabBar** 的页面的路径, 路径后**可以**带参数」。

**结论**：**tabBar 页无法用 URL query 传参**。5 个 tab 页（pet / home / community / messages / user）都属于此列。

**正确的回跳传参方式**

| 目标页类型 | API | 传参方式 |
|---|---|---|
| **非 tabBar 页**（商品详情、帖子详情、发布页…） | `uni.redirectTo({ url: '/pages/x/y?redirect=...' })` | 参数直接放 URL |
| **tabBar 页**（本规范的 5 页） | `uni.switchTab({ url: '/pages/tabbar/x' })` + **storage 传 action** | **裸路径 + storage** |

**storage 传 action 的必要防护**（照抄项目已有先例：`BUYNOW_ITEM` / `COMMUNITY_POST_DRAFTS`）

| 防护项 | 要求 | 原因 |
|---|---|---|
| 唯一 key | 每种 action 一个独立 key（如 `LOGIN_REDIRECT_MESSAGES`） | 避免与业务 storage 串扰 |
| 时间戳 | 写入时带 `ts` | 防止陈旧回跳 |
| 有效期 | 读取时校验 `Date.now() - ts <= 5 * 60 * 1000` | 5 分钟内有效 |
| 消费即删 | 读取成功后**立即** `removeStorage` | 避免重复消费 / 死循环回跳 |
| **action 白名单** | 读取时校验 action 在白名单内，非法则忽略并清空 | **安全**：storage 可被任意页写入，恶意/异常值不得触发未定义的跳转或动作 |

**现状核查**：全项目 `uni.switchTab` 调用点**当前无一带 query 参数**（`home.vue:407-408` 已有注释「tabBar 页在 uni 里只能用 switchTab，且不能带 query」）。所以这不是现存 bug，而是**新增功能（登录回跳）的实现约束** —— Phase 4 做登录回跳时必须走 storage 路径。

**⚠️ 原型陷阱（§6.13 的延伸）**：Phase 3 的 HTML 原型是自绘 tabBar，**切换时带 query 在原型里完全可行**，但真机 `switchTab` 会静默丢参 → **Phase 4 照抄原型做法会得到"参数莫名消失"的 bug**。Spec 面板必须登记此条（见 §6.13.6 数据依赖速查表）。

---

### 约束 16 · tabBar 元数据单一真值源（**含现存顺序错位 Bug**，v1.11 改写）

> ⚠️ **v1.11 升级说明**：v1.9 首次记录此问题时判断为「同步负担 / 未来风险」。**team-lead 复核后发现副本已经不同步 —— 这是正在发生的线上 Bug，不是未来风险**，本节据此改写。

#### 16.1 现存 Bug：APP 端底栏前两个 tab 图标互换

**逐位核对结果（visual-architect 已独立复核确认）**

| 源 | `[0]` | `[1]` | `[2]` | `[3]` | `[4]` |
|---|---|---|---|---|---|
| **T1 `pages.json` `tabBar.list`**（权威） | **pet** `\ue956` | **home** `\ue93c` | community `\ue94c` | messages `\ue94d` | user `\ue982` |
| **T3 `store/theme.js:75` `TAB_BAR_ICON_FALLBACK`** | ❌ **home** `\ue93c` | ❌ **pet** `\ue956` | community `\ue94c` | messages `\ue94d` | user `\ue982` |
| **T4 `tabbar/category.vue:148` `tabbarItems`** | ❌ **home** | ❌ **pet** | community | messages | user |
| **T2 `home.vue:146` `TAB_BAR_PAGES`** | ✅ pet | ✅ home | community | messages | user |

→ **T3 / T4 的前两位与权威相反。**

**根因**：提交 `c18851b4`（`feat(app): 底部 Tab 调整为宠物优先并将首页重命名为商店`）**只改了 `pages.json`**（`src/pages.json | 30 +++++--`），**未同步 `theme.js` 与 `category.vue`**。

**触发链路（`theme.js`）**

```
readTabBarList()                                    // L88
  ├─ H5：globalThis.__uniConfig.tabBar.list  ← 可读 → 用权威配置 ✅ 不复现
  └─ APP：读不到 → 回落 TAB_BAR_ICON_FALLBACK        // L98  ❌ 旧顺序
        ↓
readTabBarList().forEach((item, index) =>            // L262
  uni.setTabBarItem({ index, iconfont: { text: ic.text, ... } })   // L265 ← 按 index 套用
)
```

→ **index 0（pet 位置）拿到 `\ue93c`（home 的房子）**，**index 1（home 位置）拿到 `\ue956`（pet 的爪印）**。

**后果**

| # | 后果 | 影响面 |
|---|---|---|
| 1 | **APP 端底栏前两个图标互换** | 仅深色模式触发（`setTabBarItem` 只在 `syncTabBar()` 时调用） |
| 2 | **「宠物优先」这个产品决策在 APP 端失效** —— 用户以为第一个 tab 是宠物，其实是商城 | 全部 APP 用户 |
| 3 | **`category.vue` 自绘底栏顺序也错**（T4）→ 分类页前两项的**图标与跳转目标全错** | 分类页全部用户 |
| 4 | 与 §6 约束 15 耦合：tab 顺序同时决定原生 tabBar 显示顺序与 `switchTab` 目标页判定 → 顺序不一致会让 storage 里的 pending action **路由到错误的 tab** | 登录回跳链路 |

> **H5 不复现**（能读到 `__uniConfig`）—— 这就是为什么它能长期存活：**测试环境（H5）永远看不到**。

**图标类名本身是对的**（已逐个核对 `lucide.css`）：

| codepoint | lucide 类名 | `category.vue` 用的类名 | 一致 |
|---|---|---|---|
| `\ue956` | `luc-paw-print` | `paw-print` | ✅ |
| `\ue93c` | `luc-home` | `home` | ✅ |
| `\ue94c` | `luc-message-circle` | `message-circle` | ✅ |
| `\ue94d` | `luc-message-square` | `message-square` | ✅ |
| `\ue982` | `luc-user` | `user` | ✅ |

→ **只有顺序错，图标映射全对** —— `category.vue` 的 icon 字段可原样保留，只改顺序。

#### 16.2 4 处副本清单

| # | 位置 | 内容 | 性质 |
|---|---|---|---|
| **T1** | `pages.json` `tabBar.list` | 5 个 `pagePath` + 顺序 + codepoint + 选中/未选中色 + `text`（i18n 占位符 `%home%`） | ✅ **真正的权威**（uni-app 编译期读取） |
| **T2** | `home.vue:146` `TAB_BAR_PAGES` | 5 个路径的**手工副本**，仅 home 内部使用（`:406`） | ⚠️ 作用域局限在 home.vue |
| **T3** | `store/theme.js:75` `TAB_BAR_ICON_FALLBACK` | 5 个 iconfont codepoint，注释写「**顺序必须与 pages.json tabBar.list 一致**」 | ❌ **当前顺序已错位** |
| **T4** | `tabbar/category.vue:148` `tabbarItems` | 5 个 `{ path, labelKey, icon }`，注释写「与 pages.json 的 tabBar.list 保持一致（顺序、文案、图标）」 | ❌ **当前顺序已错位** |

#### 16.3 为什么不能运行时从 `pages.json` 读

`theme.js:69-70` 已有先例注释：H5 端 uni-app 会把 `pages.json` 暴露成 `window.__uniConfig`（可读）；但 **APP 端 `__uniConfig` 由 `app-config-service.js` 以局部 `const` 形式注入，不保证挂在 `globalThis` 上** → APP 端读不到 → 所以**必须**有一份手工维护的常量。

**既然如此，这份手工常量就是唯一真值源，`pages.json` 反而是它的副本。**

#### 16.4 唯一真值源规范

| 项 | 要求 |
|---|---|
| **文件** | 新建 **`src/constants/tabBar.js`**（与 `src/constants/brandIp.js` 同类做法）。**最终命名以本版为准** —— 前一版曾用 `tabPages.js`，因需同时承载 codepoint + icon class，改回 `tabBar.js` 以免再产生第二份真相 |
| **导出结构** | `TAB_BAR_ITEMS`：5 项，每项 `{ path, labelKey, iconClass, iconfont: { text, selectedText, fontSize } }`，**顺序 = pages.json `tabBar.list`** |
| ↳ `path` | `/pages/tabbar/xxx`（带前导斜杠，供 `switchTab` 直接用） |
| ↳ `labelKey` | i18n key（如 `tabBar.home`）；`pages.json` 里对应的是 `%home%` 占位符形式，**两者需在单测里做映射校验**（占位符 → key） |
| ↳ `iconClass` | lucide 类名（`paw-print` / `home` / `message-circle` / `message-square` / `user`），供自绘底栏用 |
| ↳ `iconfont` | `{ text: '\ue956', selectedText: '\ue956', fontSize: '20px' }`，供原生 `setTabBarItem` 用 |
| ↳ **`selectedText` 取值策略** | ⚠️ 已核实 `pages.json` 的 5 项 **`text` 与 `selectedText` 完全相同** → 「选中/非选中字形相同」是**两侧一致的有意设计**（选中态靠 `color` 区分，而非字形）。<br>因此：① **常量表的 `selectedText` 直接等于 `text`**（如实反映 pages.json）；② **单测要断言 `selectedText === text`**，这样将来若 pages.json 出现 `text ≠ selectedText` 的项，测试会失败并提醒同步；③ **不要**为了"补全字段"而给 fallback 塞一个等于 `text` 的 `selectedText` —— 那是无效改动 |
| **消费方** | `theme.js`（`TAB_BAR_ICON_FALLBACK` → 删除，改 import）、`category.vue`（`tabbarItems` → 改 import）、`home.vue`（`TAB_BAR_PAGES` → 改 import）—— **全部页面与 store 只能 import 这一份** |
| **禁止** | 页面内再定义局部副本；禁止再写裸路径数组 |

#### 16.5 防漂移：静态校验单测

| 项 | 要求 |
|---|---|
| **校验内容** | 读 `pages.json` 的 `tabBar.list`，断言与 `src/constants/tabBar.js` 的 **path 顺序、codepoint、labelKey↔占位符映射** 逐项相等 → **不一致即测试失败（CI 阻断）** |
| **为何必须静态** | 「手工常量 + 静态配置」模式下，这是**唯一可靠**的防漂移手段。运行时断言在 APP 端不可靠（`__uniConfig` 读不到），H5 通过也不代表 APP 通过 |
| **建议** | 与 `brandIp.js` 的映射校验**放在同一个测试文件**里（`src/constants/__tests__/constants.spec.js`），形成「共享常量总门禁」 |

#### 16.6 落地优先级：**P0 止血 ≠ P1 架构收敛**

> **P0（先做，不必等常量抽取）** —— APP 端底栏图标错误要立刻止住：

| # | 动作 | 说明 |
|---|---|---|
| 1 | **把 `theme.js:75` `TAB_BAR_ICON_FALLBACK` 的前两项对调**（home ↔ pet） | 止血最小改动，**不需要抽常量** |
| 2 | 把 `category.vue:148` `tabbarItems` 的前两项对调 | 同上（分类页自绘底栏） |
| 3 | ~~给 fallback 补 `selectedText` 字段~~ | ❌ **不必做，做了也无效**（v1.12 修正）—— 已核实 `pages.json` 的 5 项 **`text` 与 `selectedText` 完全相同**，「选中/非选中字形相同」是**两侧一致的有意设计**（靠 `color` 区分选中态），不是 fallback 独有缺陷；而 `theme.js:268` 已有 `selectedText: ic.selectedText || ic.text` 兜底 → 补一个等于 `text` 的 `selectedText` **等于什么都没改**。**真正要做的是在抽 `tabBar.js` 时明确 `selectedText` 的取值策略**（见 16.4） |

> **P1（架构收敛）** —— 抽 `src/constants/tabBar.js` + 三个消费方改为 import + 单测门禁。

**建议 P0 与 P1 分两个提交**：P0 是线上止血的 hotfix，P1 是架构治理。**不要把两者混在一个提交里** —— 否则 hotfix 的回滚会连带撤销架构治理，或反之。

#### 16.7 与其他约束的耦合

| 耦合点 | 说明 |
|---|---|
| **§6 约束 15（`switchTab` 不带参）** | tab 顺序同时决定原生 tabBar 显示顺序与 `switchTab` 目标页判定 → 顺序不一致会让 storage 里的 pending action **路由到错误的 tab** |
| **§3 跨页一致性锚点** | 5 个 tab 的**顺序**是「宠物优先」这个产品决策的唯一载体 —— 顺序错位 = 决策失效，不只是图标错 |
| **§4.4.2 产品线映射** | 同属「同一语义、多处硬编码」→ 统一解法：**抽共享常量 + 一致性校验**，见 §4.11 |

---

### §6.13 原型实现映射（Phase 3 独立 HTML 原型 ↔ 真实 uni-app）

> Phase 3 交付物：**单文件可交互 HTML 原型** `D:/MOYUYOWPC/.workbuddy/design/prototype/index.html`，在 **390×844** 手机框内呈现 5 个 tab 页，含底部 tab 切换、明暗模式切换、状态态（空 / 加载 / 错误 / 未登录）演示。
> 本节的作用：**提前标出原型与真实实现之间的所有差异**，避免 Phase 3 按原型 1:1 翻译到 uni-app 时踩坑返工。

#### 6.13.1 三类内容的判定

| 类别 | 定义 | Phase 3 处理原则 |
|---|---|---|
| ✅ **可直接 1:1 还原** | 原生 CSS 即可表达，且 uni-app 的 `<view>` / `<text>` / `<image>` 行为与浏览器一致 | 原型怎么写，实现就怎么写 |
| ⚠️ **需替代方案** | 原型用了浏览器专属能力，uni-app 端不可用 | 原型自由发挥，**但必须在原型里标注真实实现方式**（注释或 spec 面板） |
| ⛔ **无法在原型体现** | 依赖原生组件 / 系统能力 | 原型用**视觉等价物**模拟，并在 spec 面板写明真实行为 |

#### 6.13.2 ⚠️ 需替代方案清单（**返工高发区，Phase 3 必读**）

| 原型里会怎么写 | 真实 uni-app 里**必须**换成 | 原因 |
|---|---|---|
| **内联 SVG sprite / `<svg><path>`** 做图标 | `<text class="luc luc-search" />` + `lucide.css` 的图标字体 | §6 约束 1：APP 端 SVG 在部分机型不可见；且图标需随 `color` 变色（字体方案天然支持，SVG 需要额外处理） |
| `<img src="xxx.png">` 直接写相对路径 | `<image src="$img(url)" mode="aspectFill">` | APP 端 `<image>` 必须绝对 URL；后端相对路径要走 `toAbsoluteImageUrl()` / `$img()` |
| `aspect-ratio: 1 / 1` | `padding-top: 100%` + 绝对定位子元素 | §6 约束 3：uni-image 在 grid 中 `aspect-ratio` 失效 |
| `<div style="aspect-ratio:16/9">` 做 Hero | 固定 `height: calc((100vw - 64rpx) * 0.5625)` | 同上，宽屏下必须显式算高 |
| `position: sticky` 做吸顶顶栏 | 可用，但容器选择有条件 | §6 约束 4：`scroll-view` 内的 sticky 在 APP 端不可靠 → 吸顶只能放在 page 原生滚动的页面 |
| `backdrop-filter: blur()` 磨砂 | 可用，但**必须**有不透明兜底 | §6 约束 9：部分 Android WebView 不支持 |
| CSS `grid` + `grid-auto-rows: calc((100vw - …)/3)` | 可用，但 **rpx 与 px 混算**时需换算 | 原型用 px（390 宽），实现用 rpx（750 基准）→ 比例 1rpx ≈ 0.52px @390 宽。**公式里的 vw 部分可直接沿用，rpx 部分要按 750 基准重算** |
| CSS 变量 + `prefers-color-scheme` | CSS 变量 + `@media (prefers-color-scheme: dark) :root:not(.theme-light)` + `.theme-dark` | §4：真实项目有三态主题（system / light / dark），原型只有两态 |
| `position: fixed` 的 FAB | 可用，但 bottom 要按 `calc(112rpx + env(safe-area-inset-bottom))` | §6 约束 10 |
| 原生 `<select>` / `<input>` | `<picker>` / `<input>` 组件 | uni-app 组件在 APP 端样式不可完全自定义 |

#### 6.13.3 ⛔ 无法在原型体现清单

| 真实行为 | 原型的模拟方式 | 原型里必须标注 |
|---|---|---|
| **原生 tabBar**（`pages.json` `tabBar` + `iconfontSrc` 指向 `lucide.ttf`） | 自绘 HTML 底栏，图标用 lucide 字体，**颜色用 CSS 变量**（`--color-text-tertiary` / `--color-primary`） | ⚠️ **真实原生 tabBar 不吃 CSS 变量**，必须靠 `theme.js` 的 `TAB_BAR_PALETTE` + `uni.setTabBarStyle()` 在 `onShow` 里运行时改色（§6 约束 2）。原型看起来"自动变色"，实现时要手动同步 |
| **原生 tabBar 图标字形** | lucide 字体 | 原生 tabBar 用的是 `lucide.ttf` 的 codepoint（`\ue956` pet / `\ue93c` home / `\ue94c` community / `\ue94d` messages / `\ue982` user），**与页面内的 `luc-*` 类名不是同一套选择机制** |
| **原生 tabBar 文案字体** | 原型自绘 tabBar 可自由用字体 | ⚠️ **v1.4 补充**：真实原生 tabBar 的 `text` 走**系统字体栈**，**不受 CSS `font-family` 变量影响**（详见 §6 约束 14）→ Spec 面板必须登记这一点，否则 Phase 4 会把原型的展示体照抄到自绘 tabBar 上 |
| `uni.setTabBarStyle` / `setTabBarItem` 运行时改色 | 原型直接切 CSS 变量 | 实现时需 `useThemeStore().syncTabBar()`，且**冷启动首帧会 reject**（theme.js 已做 300ms/600ms 重试） |
| `uni.showModal` / `showActionSheet` / `showToast` | 原型自绘弹层 | 真实弹层的按钮文案/颜色由系统决定，`showModal` 的 `confirmColor` **只能写字面量**（§6 约束 2） |
| 原生下拉刷新（转圈） | 原型可做自定义刷新头（甚至不做） | 真实是系统原生转圈，样式不可定制；原型**不要**设计自定义刷新头，否则实现时会被推翻 |
| `env(safe-area-inset-*)` | 手机框内直接写死内边距（如顶部 44px、底部 34px） | 真实值随设备变；实现时用 `max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px))` |
| WebSocket 实时消息（messages 页） | 原型用定时器模拟新消息 | 真实是 `dmSocket`，收到事件后 300ms 防抖刷新 |
| 左滑删除（messages 页） | 原型用 touch 事件或按钮模拟 | 真实是手写 `@touchstart/move/end` + `translateX` 位移 + 300ms 内忽略补发的 tap（§6 约束 4） |
| 分享（`uni.setClipboardData`） | 原型模拟 toast | 真实需 `uni.setClipboardData` |
| 3D 宠物场景（pet 页） | 原型用静态图/渐变占位 | 真实是 canvas + renderjs（§6 约束 5），原型**只需还原视觉层次**，不还原交互 |
| 真机键盘弹起 | 原型不模拟 | 真实键盘会顶起页面，输入框位置需 `adjust-position` 处理 |
| **sticky 顶栏 + 原生下拉刷新**（v1.5 补充） | 原型可模拟但**手感不可信** | ⚠️ **只能真机验**：账户条吸顶后，从顶栏区域起手下拉可能把顶栏一起拖下来（原生下拉与 sticky 抢手势）。若真机有问题 → **降级为"账户条不吸顶"**（保留乙方案其余全部改动），或改为下拉区域仅限顶栏以下 |
| **APP 端字体加载检测** | 原型可用 `document.fonts` 检测 | ⚠️ **APP 端没有 `document.fonts`** → "字体是否加载成功 / 是否 fallback"**只能真机目视验**，H5 检测结果不能代表 APP |
| **`switchTab` 带参数**（v1.7） | 原型自绘 tabBar 切换时**带 query 完全可行** | ⚠️ **真机 `switchTab` 路径后不能带参数**（`uni.d.ts:5391`）→ 参数会**静默丢失**。tabBar 页的登录回跳必须走 **storage + action** 路径（§6 约束 15）。Phase 4 照抄原型做法会得到"参数莫名消失"的 bug |

#### 6.13.4 ✅ 可 1:1 还原清单（原型即定稿，实现照抄）

| 规格 | 还原说明 |
|---|---|
| **全部色值与 token** | §4.1–§4.7 的 48 个 token **原样搬进原型 CSS**，作为唯一的视觉真值源 |
| **对比度约束** | §4.10 的实测表 —— 原型里的配色就是最终配色，不要在原型阶段"临时调好看" |
| **字号阶 / 字重纪律** | §4.8 表格直接搬 |
| **间距 / 栅格** | §4.9：gutter 32rpx、section-gap 48rpx、stack-gap 24rpx |
| **圆角 / 阴影档位** | §4.9 + §4.7 |
| **各页区块结构与规格** | §5.1–§5.5 的每张表：区块顺序、圆角、阴影、内边距、字号字重、图标尺寸、图文间距 |
| **视觉焦点策略** | §5 各页「焦点建立」小节 —— 原型必须 1:1 体现哪一处是主角 |
| **四态设计** | §5 各页第 4 小节：空 / 加载 / 错误 / 未登录 |
| **暗色模式差异** | §5 各页第 5 小节 —— 原型的暗色切换不是简单反色，要按这些表逐条做 |
| **骨架屏扫光** | `--skeleton-base` → `--skeleton-highlight` 的渐变动画，`--skeleton-duration` 1.4s |
| **展示体排印** | §6 约束 6：原型可用**同名字体栈**（Playfair Display + Georgia + Songti SC 链），字体文件缺失时自动落 fallback —— **正好验证 §6.6.5「排版不塌」** |

#### 6.13.5 原型 ↔ 实现 的**单位换算表**（最易出错）

> **换算基准**：`1rpx = 视口宽度 / 750`。原型 390px 宽 → `1rpx = 0.52px`；若用 375px 宽原稿 → `1rpx = 0.5px`（设计师常用的「×2」心智模型即基于此）。**下表的「原型 px」按 390px 视口给出（已含 0.52 系数），实现侧一律填 rpx 数值本身。**

| 规范单位 | 原型（390×844 逻辑 px） | 实现（uni-app 750rpx 基准） | 换算 |
|---|---|---|---|
| 页面左右边距 | 16.6 px | 32 rpx | rpx ÷ 2（375 基准） |
| 区块间距 | 25.0 px | 48 rpx | ÷2 |
| 卡片内边距 | 12.5 / 16.6 px | 24 / 32 rpx | ÷2 |
| 圆角（小 / 中 / 大 / 特大） | 4.2 / 8.3 / 12.5 / 16.6 px | 8 / 16 / 24 / 32 rpx | ÷2 |
| 字号（xs / sm / base / md / lg / xl / 2xl / 3xl） | 11.4 / 13.5 / 14.6 / 15.6 / 16.6 / 18.7 / 22.9 / 29.1 px | 22 / 26 / 28 / 30 / 32 / 36 / 44 / 56 rpx | ÷2 |
| 图标尺寸（xs→xl） | 12.5 / 16.6 / 20.8 / 25.0 / 29.1 px | 24 / 32 / 40 / 48 / 56 rpx | ÷2 |
| Hero 高度 | `calc((390px - 33.2px) * 0.5625)` ≈ 200.7 px | `calc((100vw - 64rpx) * 0.5625)` | **公式结构相同，rpx 数不同** |
| 多列网格行高 | `calc((390px - 33.2px - 25px) / 3)` ≈ 110.6 px | `calc((100vw - 64rpx - 24rpx) / 3)` | 同上 |

> **规则**：
> 1. 原型里凡是 **`vw` / `%` 公式** → 直接照搬公式结构、只改 rpx 数；
> 2. 原型里凡是 **px 绝对值** → 取 rpx 数值 ÷2 换算（**注意 ÷2 得到的是 375px 基准的 px**，在 390px 视口实际渲染会大 4%，肉眼可接受，不必强行对齐）；
> 3. 原型里凡是 **px 绝对值且要求像素级精确**（如圆角 1px 描边、hairline）→ **实现侧不要换算**，直接写 `1rpx`，让设备自己处理。

#### 6.13.6 原型必须自带的「规格面板」（降低返工率的关键）

> 🔒 **Phase 3 硬性交付项**（team-lead 已确认）：Spec 面板是 **Phase 1 ↔ Phase 3 ↔ Phase 4** 之间**唯一的结构化交接物**（67 态 × 每态的 token 级视觉 + 数据依赖 + 替代写法 + 真实实现方式）。**不做 → Phase 4 只能"看截图猜规格"，返工率显著上升。**
> 本节即为该面板的完整定义 —— Phase 3 **直接照此实现**，无需在 Phase 2 另立接口（避免同一份规格两处维护）。

原型页面内提供一个可折叠的 **Spec 面板**（右下角入口），对每个页面/区块展示：

| 面板字段 | 内容 |
|---|---|
| 区块名 | 对应 §5.x.x 的区块编号（如「5.1.2 ③ Hero Poster」） |
| 关键规格 | 圆角 / 阴影 / 内边距 / 字号字重 / 图标尺寸 / 图文间距（直接从 §5 抄） |
| 视觉主角标注 | 该区块是「主角 / 二级 / 让位」 |
| **真实实现替代方案** | ⛔/⚠️ 项在此标注真实 uni-app 里怎么写（如「原型用 SVG → 实现用 lucide 字体」） |
| **数据依赖（v1.4 新增）** | 该区块依赖的后端字段 / 接口，以及该字段**当前是否存在**。字段不存在时，原型**不得用假数据硬编** —— 应在面板里标注「⛔ 待后端字段，原型不呈现该区块」，避免 Phase 3 产出无法落地的假象 |
| 状态态 | 该区块在 空/加载/错误/未登录 四态下的形态 |
| 深浅差异 | 该区块的暗色特例（若与浅色相同则写「同浅色」） |

**数据依赖速查表（v1.4 新增 · Phase 3 与 Phase 4 通用）**

| 区块 | 依赖字段 / 接口 | 现状 | 原型处理 |
|---|---|---|---|
| 首页产品线索引行 | `product.brandIpId`（**1=GEAR/MILO · 2=CARE/LUNA · 3=PLAY/ATLAS · 4=HOME/OLIVE**，权威表见 §4.4.2） | ✅ 已有（`goods/detail.vue` L371 在用，**但其 map 的 1/2 相反，见 §6 约束 8**） | 可呈现（须用 §4.4.2 的共享常量映射） |
| 商品卡产品线 chip | 同上 | ✅ 已有 | 可呈现 |
| 分类页 `?line=` 高亮 | 同上 + 分类接口分组 | ✅ 可用 | 可呈现 |
| **社区帖子产品线 chip** | `mo_community_post.ip_code`（`MILO`/`LUNA`/`ATLAS`/`OLIVE`） | ⚠️ **字段已在库且有索引**，Entity 有 `ipCode`，发布时自动继承；**仅 VO 未透出**（后端 1 行） | **可呈现**（v1.5 更正 —— v1.4 判为「不呈现」是错的）；色条须经 §4.4.2 语义映射 |
| 会员卡等级名 / 成长值 / 刻度 | `memberApi.getMemberInfo()` → `level` / `growthValue` / `pointsRate` | ✅ 已有 | 可呈现，用 `Platinum` / `Black` 等真实枚举文案 |
| 会员卡 tip 金额折算 | 成长值 → 金额折算率 | ⛔ **业务未确认** | 用占位文案（如 `再消费约 ¥XXX 升级至 Gold`） |
| 会员卡等级名 | `membership.vue` 的 `levels` 数组 + `mapLevelCode()` | ✅ 已有（**L5 展示名是 `Black` 黑卡，不是 `DIAMOND`**） | 可呈现，用 `Platinum` / `Black` 等真实文案 |
| 钱包带积分余额 | `memberApi` 的 `points` | ✅ 已有 | 可呈现 |
| 社交带 4 项计数 | `followApi` / `communityApi` / `browseApi` 的 `total` | ✅ 已有 | 可呈现 |
| 订单带 4 项角标 | `orderApi.getOrderList` 的 `total` | ✅ 已有 | 可呈现 |
| 消息页会话列表 / 未读 | `messageApi` + `dmSocket` | ✅ 已有 | 可呈现（WebSocket 用定时器模拟） |
| 社区话题栏 | `GET /api/v1/community/topics` | ✅ 已有（失败时用 5 个本地兜底） | 可呈现 |
| pet 3D 模型 / 装扮 | renderjs canvas | ✅ 已有 | 仅还原视觉层次，**不还原交互**（§6.13.3） |
| **tabBar 页登录回跳传参** | ⛔ **`switchTab` 路径后不能带参数**（`uni.d.ts:5391`） | — | **必须用 storage + action**（`ts` 时间戳 + ≤5min 有效期 + 消费即删 + action 白名单），见 **§6 约束 15**。原型自绘 tabBar 带 query 可行，**真机不行** |
| **tabBar 元数据（5 项）** | `pages.json` `tabBar.list`（path + 顺序 + codepoint + `%i18n%` 占位符） | ⚠️ **现存 Bug**：副本已错位 → APP 端底栏前两图标互换（**H5 不复现**） | **必须用 `src/constants/tabBar.js`**（`TAB_BAR_ITEMS`：path + labelKey + iconClass + iconfont）。见 **§6 约束 16**。原型自绘 tabBar 的顺序与图标须与常量表一致，并登记「真实实现用原生 tabBar + `setTabBarItem` 按 index 套用」 |

**Phase 3 交付时，该面板内容即 Phase 4 实现的对照表** —— 这比"原型截图 + 口头说明"的传递效率高一个数量级。

#### 6.13.7 原型阶段的**自查红线**（与 §7 反面清单一致）

原型最容易在"追求好看"时违反规范，Phase 3 交付前必须自查：

- [ ] 浅色模式下**没有**用金色做文字（对比度 1.65:1）
- [ ] 每页**只有一个**视觉主角，其余明显让位
- [ ] 正文**不是**全粗（社区尤其）
- [ ] 没有残留的 iOS 蓝 `#007aff` / `#004fad` / 红 `#ff4d4f` / 绿 `#18b367`
- [ ] 没有用 emoji 当图标
- [ ] 四个状态态**都**做了，且与常态同一套 token
- [ ] 暗色模式是**按 §5 各页第 5 小节逐条实现**的，不是 filter 反色
- [ ] 每页金色出现 **≤ 3 处**
- [ ] 原型里所有图标、占位图、渐变遮罩的**真实实现方式**都已在 Spec 面板登记

---

## 7. F. 反面清单（实现自查用）

| # | 禁止 | 判定方式 | 正确做法 |
|---|---|---|---|
| **X1** | **浅色模式下用金色做文字** | 搜 `color: var(--color-primary` 且背景是白/亚麻 → 对比度 1.65–2.41:1 | 金色只做填充（配 `--text-on-accent`）、细线、图形；文字用 `--color-text` / `--color-primary-dark` / `--chip-accent-text` |
| **X2** | **「白卡 + 1rpx 边框 + 24rpx 圆角」三件套复用** | 数一下每页有多少个带边框白卡 —— 目标 ≤ 1 | 用留白 + `--divider-weak` hairline 分组；表面用 `--surface-elevated` / `--surface-sunken` 分级 |
| **X3** | **正文用 ≥500 字重** | 搜 `font-weight: 600` / `700` 出现在正文类名上 | 正文一律 400/500；600 只给未读标题与数字；700 只给展示体标题与会员数字 |
| **X4** | **任何非品牌色的写死值** | 全局搜 `#007aff` `#004fad` `#ff4d4f` `#18b367` `#f0f0f0` `rgba(0,122,255` | 一律 token 化；原生组件处写字面量时也必须是浅/深两套品牌 hex |
| **X5** | **用 emoji 当图标** | 搜 data 里的 emoji 字符串 | 只用 `lucide.css` 中已存在的类名；模板走 `$luc()` |
| **X6** | **只写一句灰字的空态** | 每个空态必须有 图形 + 解释 + 动作 三件套 | 见 §5 各页第 4 小节；用 `--chip-bg` 圆形底 + lucide 图标 + 标题 + 副标 + 按钮 |
| **X7** | **一屏多主角** | 问"这一屏最亮/最大/最有阴影的是哪一个"，答案必须唯一 | 其余元素降级：去阴影、去边框、去金色、缩小字号 |
| **X8** | **产品线色（Care/Gear/Play/Home）在浅色模式做文字** | 搜 `color: var(--color-care)` / `var(--color-gear)` / `var(--color-play)` / `var(--color-home)` | 实测 1.40–2.45:1 全部不达标 → 浅色下产品线色**只做图形**（8rpx 色条 / 色块 / 图标底），文字用 `--chip-text-strong`；深色下才可做文字（4.74–9.00:1）。详见 §4.4.1 |
| **X9** | **用 `--color-danger`（#C96E5F）承载白字** | 搜 `#fff` / `#FFFFFF` 与 danger 同容器 | 白字仅 3.57:1 → 承载文字改用 `--color-danger-strong`（浅 `#B3574B` 白字 4.79 ✅；深 `#D9836F` **必须配 Charcoal 字** 4.97 ✅，白字 2.83 ❌） |
| **X10** | **解释性文字用三级文字 tertiary** | 搜空态副标 / 错误提示 / 表单说明里的 `--color-text-tertiary` | tertiary 仅 2.70:1，只留给时间、计数等非解释性元信息；解释性文字一律 `--color-text-secondary`（4.88:1） |
| **X11** | **给 `uni.switchTab` 传 query 参数** | 搜 `switchTab({` 里带 `?` 的 url | **平台硬限制**（`uni.d.ts:5391`「路径后不能带参数」）→ 参数静默丢失。tabBar 页的登录回跳走 **storage + action**（§6 约束 15）；非 tabBar 页才用 `redirectTo` + URL 参数 |

---

## 8. Phase 2 体验设计需要重点关注的问题

### 8.1 规范冲突待裁记录（v1.8 记录 · **已裁决 2026-11-19**）

> ✅ **最终裁决结果（team-lead 2026-11-19）**：
> - **冲突 A** → **采纳方案 ③「修数据，不迁就数据」**。team-lead 已逐条独立复核 E1–E5 五条证据**全部为真**，明确其原指示中「运营在后台配的就是权威」**被 E5 直接推翻**（admin 后台根本没有 `mo_brand_ip` 管理页）。落地：取色链路不变 + DB 修 4 行数据对齐，**零代码引用点迁移**（§4.4.2）。
> - **冲突 B** → **维持 Phase 1 v1.5+「全 App 取消斜体」**。team-lead 的「仅英文 locale 斜体」指示**作废**。理由：汉字伪斜体是算法倾斜、笔画重心偏移失真（**渲染缺陷非设计**）；且在 ≤120KB 上限下省掉一个 italic 字体文件是实打实的收益。
>
> **附带裁定**：**新增 `src/constants/tabBar.js` 确认落地**，与 `brandIp.js` 一起做；但**修顺序错位是 P0**（APP 端底栏图标是错的），抽常量是 P1 —— **P0 可先单独修**止血，不等架构收敛（§6 约束 16.6）。
>
> **本节保留不删** —— 记录决策过程、证据链与回退点，便于后人理解结论的来由，避免同类问题再次凭印象拍板。若日后需要推翻，§8.1 已标明两处的**准确回退位置与成本**。

#### 冲突 A · chip 色条的取色权威源

**✅ 已裁决：采纳 Phase 1 v1.6–v1.10 的落地版本（取色以语义线为准 + DB 数据修正）。** 落地动作见 §4.4.2「DB 修正动作」。

| | team-lead 指示（对 Phase 2） | **Phase 1 v1.6–v1.8 实际落地** |
|---|---|---|
| 结论 | 「以 DB 为准」→ 以 `mo_brand_ip.color_hint` 为权威，废弃前端 4 个孤儿色，前端所有 `--color-care/gear/play/home` 引用点都要迁移（**破坏性变更**） | 「**`color_hint` 不是权威**，色条取色一律以**语义线**（前端 `variables.scss`）为准」 |
| 依据 | DB 的 `color_hint` 是权威，运营在后台配的就是权威 | **E1** 建表注释示例值 = LUNA 实际值（自由填充列）；**E2** ATLAS 填了 Charcoal 正文色；**E3** 独缺 HOME `#D9B4B0` 且顺序错位；**E4** 无任何运行时消费点；**E5** admin 后台无管理页 / 无配置入口 + 无 CHECK / 无索引 → **"运营在后台配的就是权威"前提不成立** |
| 后果 | 破坏性变更，迁移所有引用点 | **不需要破坏性变更** —— 前端 4 条色值保持权威，只需新增 `ipToLine` 映射层 |

**Phase 1 的补充论证（v1.8）**

1. **「迁移所有引用点」的实际影响面为 0** —— `color_hint` 零消费点，没有任何代码读它；真正被引用的是**前端 4 个 token**，而那 4 个值本来就是正确的语义线色值。该方案的实际动作是**为了迁就一个错误的自由填充列，去改一批本来正确的数据**。
2. **即使决定以 DB 为准，DB 侧也必须先改 2 条数据** —— `OLIVE`（HOME）在 DB 里**取不到色**（缺 `#D9B4B0`）；`ATLAS` 的 `#2E2B29` 是正文色，作 chip 色条对比度 **1.05:1，物理上不可见**。→ 该方案的第一步仍是改数据，改完即等于「语义线为准」。
3. **逻辑闭环**：Phase 1 v1.6 的诚实性修正已指出 —— **连「IP → 语义线」这个映射本身都还是推断**（DB 里根本没这一列）。把一个**推断值**升格为「权威」自相矛盾。

**推荐解：方案 ③「修数据，不迁就数据」** —— 取色链路不变（前端 token 权威），把 `color_hint` 的 4 行**修正为与语义线一致**（补齐 `OLIVE=#D9B4B0`、`ATLAS` 改 `#B38A5A`）。破坏性变更仅 **1 张表 4 行数据**，**零代码引用点迁移**；Phase 1 §4.4.2 与 Phase 2 §2.1.4 均**无需重写**。

> **若最终选择「以 DB 为准」**：Phase 1 §4.4.2 需整体重写（表结构 + 取色链路 + 两条禁令），回退成本约 0.5 天。

#### 冲突 B · slogan 斜体

**✅ 已裁决：维持 Phase 1 v1.5+「全 App 取消斜体」。** team-lead 原「仅英文 locale 斜体」的指示作废 —— 理由：汉字伪斜体是算法倾斜、笔画重心偏移失真（渲染缺陷非设计）；Playfair 无 CJK 字形 → 中文必然伪斜体；≤120KB 上限下省掉一个 italic 字体文件是实打实的收益；编辑感由字距 + 细分割线 + 对称留白承担。**Phase 1 不需回退**（§5.1.2⑥ / §5.4.2⑦ / §6.6.2 / §6.6.7 保持 v1.5+ 现状）。

| | team-lead 指示（对 Phase 2） | **Phase 1 v1.5+ 实际落地** |
|---|---|---|
| 结论 | `font-style: italic` **仅英文 locale 生效**，中文正体 + 加大字距 | **全 App 不使用斜体**（`font-style` 仅 `normal`），中英文都正体；编辑感由字距 + 细分割线 + 对称留白表达 |
| 附带成本 | 需打包 `playfair-display-latin-italic.woff2` | **省下一个字体文件**（≤120KB 体积上限下的实打实收益） |

**Phase 1 的论证**

1. 斜体是字体的**真实字形**，伪斜体是**算法倾斜**；汉字笔画倾斜后重心偏移、撇捺角度失真，中文排印里几乎总是负面的 → **是渲染缺陷，不是设计**。
2. Playfair Display **不含中文字形** → 中文必然落 `Songti SC`/`STSong` → 必然伪斜体 → 「仅英文斜体」并不能避开这个问题，只是把它推到次要路径。
3. **跨语言一致性**：若英文真斜体、中文正体，同一个 slogan 在两个 locale 下会呈现两种视觉性格（"倾斜的编辑感" vs "端正的编辑感"）。slogan 是**品牌收尾位**，用户切换语言时看到的是同一位置的东西，**视觉性格不该变** —— 这与 §3 跨页一致性锚点表是同一原则。
4. 编辑感来自**排版秩序**（细分割线 + `--tracking-kicker` 字距 + 上下对称留白），而非字形倾斜 —— 与 §2 品牌论证「高端感来自减法与秩序」同源。

> **若最终选择「英文斜体」**：Phase 1 需回退 4 处（§5.1.2⑥ / §5.4.2⑦ / §6.6.2 / §6.6.7），并加回 `playfair-display-latin-italic.woff2` 字体文件。

1. **首页首屏的"任务"到底是什么？** 现状首屏是「搜索 + 金刚 + Banner + 商品」，用户意图不明确（是逛品牌、找分类、还是找单品？）。Phase 2 需要定：Hero 是**品牌主张**还是**当日主推**？以及金刚区 4 个入口（分类/会员/领券/限时）与品牌 4 条产品线（Care/Gear/Play/Home）的关系 —— 目前**产品线在首页完全没露出**，而社区页我们建议加产品线 chip，这两处必须一起设计，否则品牌资产无处安放。

2. **会员体系的语义分层（已定案闭环，v1.4）。** ✅ 会员卡 = **身份象征（主）+ 积分入口（次）**；卡面 = **56rpx 等级名（英文）+ 24rpx 中文副标 + 刻度条右端 22rpx 精确成长值 + 动态 tip**；**积分余额移出卡面**，唯一出处为钱包带第 2 格（36rpx）。等级 / 成长值 / 门槛 / 进度算法**全部复用 `membership.vue` L264-283 的 `progressPercent` / `progressHint`**，不另立规则。⚠️ **L5 展示名统一为 `Black` 黑卡**（后端枚举 `DIAMOND`，前端展示名 `Black`，沿用用户已见过的文案）。仍需业务定的只有一项：**tip 的成长值→金额折算率**（Phase 3 用占位文案，Phase 4 落码前不硬编）。

3. ⚠️ **【优先级已调整 · v1.11】O13 > O12** —— team-lead 裁定：**品种对不上用户会直接看出来「这是我的猫吗」，色条对不上只是审美差异**。因此：
   - **O13（IP 元数据两套收敛 + `pet-hub-3d` token 化）** 优先级**高于** O12；
   - **O13 的首要子项不是配色 token 化，而是「品种元数据」** —— `pet-hub-3d.vue:220-255` 硬编码的 IP 品种（金毛寻回犬 / 布偶猫 / 哈士奇 / 柯基）与 DB `mo_brand_ip.species`（灵缇犬 / 英短金渐层 / 杜宾犬 / 缅因猫）**完全不同** → 同一 IP 在社区/商品侧显示「灵缇犬」、在 3D 场景显示「金毛寻回犬」，用户会以为是两只不同的狗。**配色 token 化是次要项**（3D 场景里差异化色本身合理，见 §6 约束 8）。
   - **O12（IP→语义线映射待产品侧书面确认）** 降为次优先 —— 它只影响 chip 色条，不影响用户对内容的识别。

4. **社区的商品化边界与"头条位"的分配机制。** 社区首帖是视觉焦点，那**谁来占首帖**？是"编辑精选"、是"关注的人"、还是"算法最热"？这直接决定社区是内容社区还是电商导购。同时需要明确：帖子是否允许挂商品卡、产品线 chip 是否由用户手选（还是由系统根据帖子内容自动打标）—— 这影响 P4（图片是主角）能否成立。

4. **未读与角标的全局策略。** 5 个 tab 都可能亮红点（首页铃铛、消息页角标、tabBar 角标），规则不统一会互相打架。Phase 2 需要定义统一规则：哪些算"未读"、角标如何合并（总未读 vs 分项）、什么时候用红点 vs 数字、什么时候从红点升级为金色（本规范已把消息页内红点改为金色竖条，需确认全局是否统一）。另外，**通知中心与私信是否还需要两个独立入口**（合并容器后）也需定性。

5. **pet tab 与其余 4 tab 的品牌延续契约。** pet 是沉浸场景 + 3D，其余 4 页是信息流，两者的视觉语法天然断裂。Phase 2 §2.5 已给出**最小延续协议 6 条**并纳入本页约束（§5.5）：kicker 语言 + 金色只用于身份语义 + 毛玻璃只属于场景 + **中文标题统一 `--font-sans` 600**（v1.3 修正：原写「展示体标题」与新的排印规则冲突）+ 护理卡去边框 + token 收敛。

6. **四态的文案与落点。** 空态不只是"没数据"，它是**引导引擎**。Phase 2 需要为每个空态指定文案语气与落地页：空商品 → 分类；空会话 → 发起私信；空关注 → 推荐；空订单 → 首页。以及错误态的**重试粒度**（整页重试 vs 区块重试）与**离线态**是否需要独立视觉（当前无离线检测能力，需评估是否补）。

---

*（完）*
