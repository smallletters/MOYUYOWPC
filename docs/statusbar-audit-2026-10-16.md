# APP 端顶部状态栏遮挡 · 全站审计报告

- 日期：2026-10-16
- 范围：`moyuyo-app`（uni-app 3 + Vue3），131 个页面
- 触发：客服页 `pages/user/customer-service` 真机反馈「顶部导航栏压住系统状态栏」
- 审计脚本：`~/.workbuddy/skills/moyuyo-status-bar-safe-area/scripts/audit-statusbar.mjs`
- 原始输出：`tmp/audit-statusbar.out.txt`

## 一句话结论

已全站治理完毕：61 个 `navigationStyle: custom` 页面中 **60 个已按规范 v2 补齐顶部状态栏占位**，`pages/user/splash`（全屏启动页）经确认无需处理。治理过程中还**证实并修掉了一个只在 iOS 上暴露的既有缺陷**——旧公式把 `env(safe-area-inset-top)` 与 `var(--status-bar-height)` 相加，在 iOS 刘海机上会双重计算，顶栏被推低一倍。

## 统计（治理前 → 治理后）

| 项 | 治理前 | 治理后 |
|---|---|---|
| 页面总数 | 131 | 131 |
| `navigationStyle: "custom"` 页面 | 61 | 61 |
| 已占位 | 25（其中 **22 个是 v1 双算写法**） | **60（全部 v2）** |
| 高置信漏处理（真机会被遮挡） | 35 | **0** |
| 需人工确认 | 1 | 1（`pages/user/splash`，已确认为全屏启动页、无需占位） |

审计脚本实读（`tmp/audit-statusbar.out.txt`）：`已占位 60 ├ v2 60 └ v1 残留 0` / `RISK 0` / `需人工确认 1`。
两轮改动合计：**57 files changed, 436 insertions(+), 98 deletions(-)**（第 1 轮补漏处理 35 页 + 第 2 轮 v1→v2 迁移 22 页）。

## 根因

`src/pages.json` 中声明 `"navigationStyle": "custom"` 后，uni-app **不渲染原生导航栏**，WebView 铺满全屏（含系统状态栏区域），页面内容从 y=0 开始。此时自渲染的 `.header` / `.navbar` 若只写固定高度（如 `height: 88rpx`）而**没有 `padding-top`**，内容就会被系统状态栏（时间/电量/信号、刘海/灵动岛/挖孔）压住。

> 注：`src/components/` 中**没有**公共导航栏组件在内部处理状态栏（已 grep 确认），因此页面级漏处理就是真漏处理，不存在组件兜底。

## 修复标准 —— 规范 v2（全站统一约定，纯 CSS）

> ⚠️ 下面的 **v1（二者相加）写法已废弃**，勿再用。原因是 iOS 上会双重计算，详见文末「iOS 双重计算已证实」。

```scss
/* 顶部导航栏
   APP 端因为 navigationStyle:custom 自渲染 header,
   需为系统状态栏(刘海/灵动岛/胶囊)预留顶部空间。
   取 --status-bar-height 与 env(safe-area-inset-top) 的较大值(而非相加):
   iOS 支持 env() 时二者同为状态栏高度,相加会翻倍。 */
.header {
  /* 兜底行必须在前:老 WebView 不认识 max() 时会丢弃整条声明 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  padding-right: 24rpx;   /* 原左右内边距，按各页实际值填 */
  padding-left: 24rpx;
  padding-bottom: 0;
  box-sizing: border-box; /* 必须：否则 padding 会叠加在 height 之外 */
}
```

只靠 padding 撑开的顶栏（无固定 `height`）只需改 `padding-top`，**基准值必须留在 `calc()` 外层**：
`padding-top: calc(24rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)))`。
**绝不能写成 `max(24rpx, ...)`** —— H5 端（二者皆 0）会算出 24rpx，凭空多出基准值 → H5 回归。

### ⚠️ 自定义属性是唯一例外，不能用「重复声明」兜底

`--*` 的值几乎不解析失败，`--sb: max(...)` 会**正常覆盖**前一条（而非被丢弃），导致消费点 IACVT 退化为初始值（如 `top: auto`）。必须用能力查询包住自定义属性本身：

```scss
--sb: var(--status-bar-height, 0px);
@supports (top: max(0px, 0px)) {
  --sb: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
}
```

全站唯一实例：`src/pages/tabbar/pet.vue` 的 `--sb`。影响窗口：iOS 13.0~13.3 / Chrome 49~78。

已按此约定修好的参考实现：

- `src/pages/user/settings.vue:362-379`
- `src/pages/user/browsing-history.vue:257-287`
- `src/pages/community/search.vue:388-396`

### ⚠️ 子元素布局决定要不要单独加偏移（最容易改错的地方）

| header 内子元素布局 | 做法 |
|---|---|
| **普通 flex 子项**（`justify-content: space-between` 的返回键 / 标题 / 右侧按钮） | **只给 `.header` 加 `padding-top`**，flex 会带所有子项一起下移。**禁止**再给返回键加 `top:`，否则双重下移 |
| **绝对定位**（`position: absolute`） | 子元素吃不到父级 padding，**必须**自己加 `top: calc(env(...) + var(...))`（如 `settings.vue` 的返回键） |

`customer-service.vue` 属前者；`settings.vue` 属后者。**两页不能照抄。**

## 本轮修复范围

第 1 轮补齐漏处理的 35 页，第 2 轮把原先 22 个 v1 页面迁移到 v2，合计 57 个文件。起点的单页样板是客服页（`src/pages/user/customer-service.vue` 的 `.header`，当时用的是 v1，第 2 轮也已一并迁到 v2）。

## 已修复清单（35 个漏处理页 + 22 个 v1 迁移页，按模块，全部已于当日完成）

> `fixed/sticky` 列的 `Y` 表示该页顶栏是 `position: fixed` 或 `sticky`（通常需连带检查 `z-index` 与滚动行为），`-` 表示顶栏在正常文档流内。

### pages/order（3）
| 页面 | fixed/sticky |
|---|---|
| `pages/order/detail` | Y |
| `pages/order/after-sales` | Y |
| `pages/order/reviewDetail` | - |

### pages/user（21）
| 页面 | fixed/sticky |
|---|---|
| `pages/user/notifications` | - |
| `pages/user/feedback` | - |
| `pages/user/scan` | Y |
| `pages/user/messages` | Y |
| `pages/user/edit-profile` | Y |
| `pages/user/avatar-settings` | Y |
| `pages/user/privacy` | Y |
| `pages/user/terms-document` | - |
| `pages/user/my-reviews` | Y |
| `pages/user/gift-card-manage` | Y |
| `pages/user/balance-recharge` | Y |
| `pages/user/balance-withdraw` | Y |
| `pages/user/subscribe` | Y |
| `pages/user/subscribe-manage-detail` | Y |
| `pages/user/service-booking` | Y |
| `pages/user/booking-detail` | - |
| `pages/user/new-user-zone` | - |
| `pages/user/festival-event` | - |
| `pages/user/affiliate-center` | - |
| `pages/user/prime-page` | - |
| `pages/user/achievement-wall` | - |
| `pages/user/block-manager` | - |

### pages/goods（5）
| 页面 | fixed/sticky |
|---|---|
| `pages/goods/qa` | Y |
| `pages/goods/frequent-purchase` | Y |
| `pages/goods/share-product` | Y |
| `pages/goods/product-subscribe` | Y |
| `pages/goods/product-subscribe-list` | Y |
| `pages/goods/ar-try-on` | - |

### pages/pet（2）
| 页面 | fixed/sticky |
|---|---|
| `pages/pet/pet-hub-3d` | Y |
| `pages/pet/allergy-profile` | Y |

### pages/community / debug（2）
| 页面 | fixed/sticky |
|---|---|
| `pages/community/drafts` | - |
| `pages/debug/plugin-test` | - |

### 需人工确认（1）
| 页面 | 说明 |
|---|---|
| `pages/user/splash` | 全屏启动页，通常不需要状态栏占位，人工确认即可 |

## H5 零回归说明

H5 端 `--status-bar-height = 0`、桌面浏览器 `env(safe-area-inset-top) = 0`，代入后：

- `height = calc(88rpx + 0 + 0) = 88rpx`（与修改前一致）
- `padding-top = 0`，左右内边距不变，`padding-bottom = 0`
- 项目全局无 `*{box-sizing}`（仅 `common.scss` 给 `.container`、`App.vue` 给 `.uni-scroll-view`），`.header` 默认 content-box；但 border-box 与 content-box **仅在垂直 padding ≠ 0 时有高度差**，本例上下 padding 均为 0 → 二者恒等

→ 像素级等价，H5 无回归。

## iOS 双重计算：已证实，并已在本轮修掉

### 决定性证据

`moyuyo-app/dist/dev/app-plus/__uniappview.html:10-14`：

```js
var coverSupport = 'CSS' in window && typeof CSS.supports === 'function' &&
  (CSS.supports('top: env(a)') || CSS.supports('top: constant(a)'))
document.write('<meta name="viewport" content="width=device-width, ...' +
  (coverSupport ? ', viewport-fit=cover' : '') + '" />')
```

uni-app **检测到设备支持 `env()` 就自动写入 `viewport-fit=cover`**。iOS WKWebView 必然命中 → 页面铺到状态栏底下 → iPhone X+ 上 `env(safe-area-inset-top)` = 44~59px（**非 0**）；而 `--status-bar-height` 同为状态栏高度 → 旧公式相加得 88~118px，**顶栏被推低一倍**。Android 上 `env()` = 0，故只有 `--status-bar-height` 生效 —— 这正是旧公式在 Android 上看着正常、iOS 问题长期无人发现的原因。

佐证：uni-app 自身的内置样式在 APP 端**只用** `var(--status-bar-height)`，从不与 `env()` 相加（`@dcloudio/uni-app-plus/style/api/location-picker.css:59`、`location-view.css:90`）。

### 各端取值

| 端 | env | --status-bar-height | v1 相加 | **v2 取 max** |
|---|---|---|---|---|
| iOS App（刘海） | 44~59 | 44~59 | 88~118 ❌ | **44~59** ✅ |
| Android App | 0 | ≈24 | 24 ✅ | **24** ✅（与 v1 逐像素相同） |
| H5 桌面 | 0 | 0 | 0 ✅ | **0** ✅ |

→ **改 v2 对 Android 是零回归的**：`max(0, sbh) = sbh`，与 v1 结果完全相同。即便将来证明某个端 `env()` 为 0，v2 也算 `max(sbh, 0) = sbh`，**怎么改都不会更差**。

### 仍需真机确认的项

在 iPhone 刘海/灵动岛机型上目视「返回按钮/标题距屏幕顶端」的像素：

- **≈44~59px** = 正确（v2 生效）
- **≈88~118px** = 说明仍是旧行为，需排查

Android 挖孔屏：`--status-bar-height` 应生效、`env()` 应为 0；若某台 Android 真返回了非 0 的 `env()`，v2 只会从「超留白」变成「正确」，不会产生新的遮挡。

## 遗留项 —— 已全部修复（2026-10-16 收尾，5 files / +28 / -22）

### 1. `goods/detail` / `cart/index`：JS 方案 → 纯 CSS v2

原方案靠 `uni.getSystemInfoSync().statusBarHeight` 绑定内联 `:style="{ paddingTop: statusBarHeight + 'px' }"`。已删除内联绑定、`data.statusBarHeight: 20` 与 `onLoad` 里的 `getSystemInfoSync`，改为在 `.navbar` / `.header` 上写 v2 的两条 `padding-top`。

修掉的三处缺陷：

| 缺陷 | 说明 |
|---|---|
| `statusBarHeight \|\| 20` | H5 下该值为 0，被 `\|\|` 当成 falsy 退化成 20px → H5 顶部凭空多 20px 留白 |
| 缺 env 项 | 无法兜底 H5 PWA/standalone（`viewport-fit=cover`）场景 |
| `data` 初值 20 | API 回填前先用 20 渲染，真机上是 1 帧位移 |

`.header` 保留 `box-sizing: content-box`，padding 累加到高度，与原内联行为一致。

### 2. 非 custom 页去掉多余的 `--status-bar-height`

`pages/goods/search.vue`、`pages/community/search.vue` 在 `pages.json` 中**没有** `navigationStyle: custom`（`globalStyle` 也无全局 custom），即使用原生导航栏，webview 内容本就从导航栏下方开始 → 再加 `--status-bar-height` 会在 APP 端多出一段空白。已改为只保留 `env` 项：

```scss
/* 只保留 env —— 兜底 H5 PWA/standalone（viewport-fit=cover）场景 */
padding-top: calc(16rpx + env(safe-area-inset-top, 0px));
```

### 3. `goods/bundle-deal.vue` 的 sticky 吸附点

`.tab-bar` 原为 `position: sticky; top: 88rpx`，是为配合上方的 `.nav-header`（`height: 88rpx`、`sticky top: 0`）。但 **`.nav-header` 在模板里没有对应节点** —— 模板 L3 只有 `<!-- 顶部导航栏 -->` 注释，`.nav-title` / `.share-icon` 全是死样式。而该页也不是 `navigationStyle: custom`。

结果：滚动时 tab 栏悬在距顶 88rpx 处、上方露出一条空白。已改为 `top: 0`。

> 教训：**sticky 吸附点按「假设存在的兄弟元素高度」硬算时，务必确认该元素真的有模板节点。**

### 本轮验证

- 5 个文件的 `<style>` 块用项目自带 sass 实编译：**5/5 通过，0 失败**。
- 审计脚本复跑：仍为 **已占位 60（全部 v2）/ v1 残留 0 / 漏处理 0 / 需人工确认 1**。
- 全仓 grep `statusBarHeight` 仅剩注释，无代码残留。
- `git diff --stat`（本轮 5 个文件）：`28 insertions(+), 22 deletions(-)`。
