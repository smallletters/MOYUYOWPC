# moyuyo-app 全站 i18n 整改 · 验收报告

日期：2026-10-16　范围：`D:\MOYUYOWPC\moyuyo-app`（uni-app 3 + Vue3 + pinia，H5 / APP 双端）

---

## 1. TL;DR

按「第 1、2、3 步」完成全站硬编码中文的 i18n 整改：**49 个目标文件**的 469 处命中已处理完毕，
目标文件残留的 91 处**全部属于「有意跳过」类**（匹配键 / 诊断日志 / emoji 检索字典 / 枚举值 / 死代码）。
过程中额外发现并修复了 **7 个会静默把页面改坏的 Bug**（含 1 个整页崩溃、1 个整页显示 key 原文）。

## 2. 交付概览

| 指标 | 结果 |
|---|---|
| 目标文件 | 49 个（其中 **36 个已清零**） |
| 目标文件残留 | 13 文件 / 91 处，**逐条核对均属有意跳过** |
| 字典 key 路径总数 | **3687**（zh-CN / en-US **逐 key 完全对齐**） |
| 重复 key | **0** |
| `[object Object]` 残留 | **0** |
| 全站 `t()` 引用解析 | 235 文件 / 2641 处引用 → **无法解析 0** |
| eslint（38 个改动文件） | **EXIT=0，0 error**（13 warning 均为既有风格项） |
| 构建 H5 | **EXIT=0 · DONE Build complete** |
| 构建 APP | **EXIT=0 · DONE Build complete** |

## 3. 本轮修复的 7 个真 Bug（都会静默失效）

| # | 文件 | 问题 | 后果 |
|---|---|---|---|
| 1 | `utils/browsingHistory.js` | 局部变量 `const t = date.getTime()` 遮蔽了从 `@/i18n` 导入的翻译函数 `t` | `t is not a function`，**浏览历史页整页崩溃** |
| 2 | `pages/community/chat-history.vue` | `import i18n from '@/i18n'` —— 该模块**无 default export**（只有命名导出 `i18n` / `t`） | `i18n` 为 `undefined`，运行时炸；`no-undef` **不会报** |
| 3 | `pages/community/dm-chat.vue` | 只导入 `{ t }`，却有 2 处写 `i18n.t(` | `i18n is not defined` |
| 4 | `pages/pet/health-calendar.vue` | 声明为 `WEEKDAY_KEYS`/`LEGEND_DEFS`，5 处引用仍是 `WEEKDAYS`/`LEGEND_ITEMS` | `data()` 抛异常，**整页白屏** |
| 5 | `src/i18n/*.js` | `newUser` 命名空间被**误缩进到 `returns` 内部** | `$t('newUser.*')` 全取不到，`new-user-zone.vue` **整页显示 key 原文**（自 HEAD 起就是坏的） |
| 6 | `src/i18n/*.js` | 合并脚本把嵌套对象 `qa.mock` 写成 `mock: [object Object],` | 字典语法损坏 |
| 7 | `src/i18n/*.js` + `pet/dresser.vue` | 新增键 `petDresser.equipped` 与既有 `'已穿戴 {name}'` 重名，插值版胜出 | 「已穿戴」标签会渲染出字面 `{name}` |

## 4. 有意跳过（**不要当遗漏去改**）

| 类别 | 例子 | 原因 |
|---|---|---|
| 后端消息匹配键 | `utils/request.js` 的 `SERVER_ERROR_MAP`（`宠物不存在: 'serverMsg.petNotFound'`）、`msg.includes('今日已签到')` | 中文是**与后端响应做匹配的键**，翻译后永不命中 |
| 枚举比较值 | `goods/qa.vue` 的 `item.answerTag === '商家'` | 用于 class 判断，翻译后判断恒假、样式全废 |
| 检索字典 | `community/create.vue` 的 emoji 中文关键词（23 处） | 仅供 `k.includes(q)` 检索，从不渲染 |
| 诊断日志 | `pet/space-3d.vue`（21 处）、`utils/modelDownload.js`（7 处） | 仅进 `console.*`，保持调试可读 |
| 死代码 | `plugins/auth.ts` / `payment.ts` / `pethub.ts` | 与同名 `.js` 并存，Vite 默认 `.js` 优先 → `.ts` 永不加载 |
| 非文案 | `dress-popup.vue` 的 `'3D模型.glb'`、`tabbar/community.vue` 的 SVG 内部文本 | 文件名 / 图片源码 |

## 5. 改动清单（主要）

**字典**：`src/i18n/zh-CN.js`、`src/i18n/en-US.js`（新增 14 个命名空间：`communitySearch`、`communityChatHistory`、`dmChat`、`webviewPay`、`validate`、`upload`、`authPlugin`、`paymentPlugin`、`cameraPlugin`、`locationPlugin`、`biometricPlugin`、`pushPlugin`、`petHubPlugin`、`imageFallback`、`browsingHistory`）

**非页面层**：`utils/validate.js`、`utils/browsingHistory.js`、`api/upload.js`、`plugins/{auth,payment,camera,location,biometric,push,petHub3d,arTryOn}.js`、`store/{cart,pet}.js`、`components/{dress-popup,image-fallback}`

**页面层（节选）**：`goods/{share-product,flash-sale-detail,product-subscribe,qa}`、`community/{search,drafts,chat-history,dm-chat}`、`pet/{health-calendar,weight-chart,dresser,album,space-3d,care-records}`、`user/{subscription-manage,subscribe-plan,new-user-zone}`、`cart/index`、`tabbar/{home,community}`、`webview/pay`

## 6. 遗留与建议

1. **`plugins/*.ts` 潜在雷**：`.js` 一旦被删，`.ts` 会带着裸中文接管。建议直接删除三个 `.ts` 孪生文件。
2. **`goods/detail`、`cart/index` 的 `localeVersion` 是死引用**（页面从未定义）。`$t` 底层读 Vue ref，本身已响应式，建议清理。
3. **历史耦合仍存在**：`coupon-center` / `mission-center` 用**中文字串匹配后端数据**决定分类与跳转，英文环境下会失配。建议后续改为结构化枚举匹配（超出本次文案改造范围）。
4. **CI 收口**：建议把 `scan-i18n-hardcode.mjs` 与 `verify-i18n-keys.mjs` 挂到 CI，新增硬编码或引用不存在的 key 即告警。
5. **真机复测**：切英文后重点走查宠物 3D、健康日历、问答、分享、秒杀订阅等本轮改动密集的页面。
