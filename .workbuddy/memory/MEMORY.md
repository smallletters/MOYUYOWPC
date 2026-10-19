# MOYUYOWPC 项目长期记忆

## moyuyo-app（uni-app 3 + Vue3 + pinia，同构 H5 + APP 原生）

### 铁律 1：读 `VITE_*` 必须「裸读」，禁止运行时守卫 / 动态下标
- `vite.config.js:11-14,62-64` 用 `define` 在**编译期**把 `process.env.VITE_*` 替换成字面量；APP 端运行时不存在 `process`。
- 正确：`const base = process.env.VITE_X || ''`。
- **禁止** `typeof process !== 'undefined' && process.env...` 守卫（APP 端短路，值恒空）；**禁止** `import.meta.env`（APP polyfill 用 `new URL`/`document` → 白屏）；**禁止** `process.env[key]` 动态下标（define 不替换，APP 端整段失效）。
- 统一入口：`src/utils/config.js` 的显式静态 `ENV` 映射对象（新增变量必须同步加进该对象，且 `.env*` 里有定义）。其余实例：`utils/request.js:231-239`、`api/cms.js`、`api/product.js`、`pages/tabbar/community.vue:501-504`、`utils/imageUrl.js`。
- 踩坑史：`imageUrl.js`、`config.js`（曾导致 APP 端全配置回落 defaultValue）、`coupon-center/flash-sale/flash-sale-detail` 的 currencySymbol，均已修。

### 铁律 2：APP 端读页面 URL 参数禁用 `getCurrentPages()[last].options`
- `pageVm.options` 在 app-plus **永远不存在**（`uni-app-plus/uni.runtime.esm.js:1875 initPageVm` 只写 `route/$vm/$page/$mpType`）；H5 会写 → **H5 好、APP 静默空对象**，是最隐蔽的「仅真机复现」Bug 类。
- APP 上真实位置：**`pageVm.$page.options`**（或 `$page.query`）；H5 的 `$page` 也可达。
- 统一读法：`src/utils/pageQuery.js#getPageQuery()`，兜底链 `options → $page.options → $page.query → $vm.$page → {}`，全程 try/catch，非对象返回 `{}`。
- 页面模式：`onLoad(options)` 官方入参作主路径 + 幂等 `bootstrap(options)`，用 `booted`/`selectionRestored` 去重，`onLoad`/`onMounted` 都调、顺序无关（范例 `pages/user/address.vue`）。
- 顺序铁律：读参必须在 `booted` 早退**之前**、且在依赖它的异步动作（`ensureSession()`/`load()`）**之前**；i18n 订阅移进 booted 块内防重复，但 `onUnmounted` 清理**必须留在块外**。
- `decodeURIComponent` 读参必须 try/catch（`100%棉` 会抛 `URIError`，在 booted 之前会致页面永久不可用）。
- URL 里的 ID 一律 `String()`，禁用 `Number()/parseInt`（雪花 ID ~2e18 > 2^53）。
- 误报别改：`community/create|detail`、`goods/detail`、`community-topic` 已走 `onLoad(query)`，其 `getCurrentPages()` 只取 `pages.length`；`App.vue:131` 只读 `top.route`。

### 铁律 3：新页面必须已注册进 `src/pages.json`
- `pages.json` 是 **JSONC**（`//`、`/* */`、尾随逗号），`JSON.parse` 会炸 —— 用 Edit 做文本编辑（或先 `stripJsonComments`）。
- 跳转报「页面不存在」多为漏注册（比读不到参数更前置）。已注册 132 条；**仍漏注册**：`user/coupons`（被 `coupon-center.vue:366`、`wallet.vue:259` 跳）、`user/wallet-recharge`（被 `balance-manage.vue:154`、`wallet.vue:232` 跳）。
- 调用方路径写错：`order/after-sales.vue:187` → 应为 `/pages/order/apply-after-sales`；`points-mall.vue:145` → 应为 `/pages/user/address`（且 `address.vue` 需支持 `purpose=exchange` 回流）。
- 孤儿页：`order/returns.vue`、`order/logistics-tracking.vue`（src/pages 下共 10 个未注册 .vue）。
- 建议固化为 CI：src 下所有 `/pages/...` 字面量 vs pages.json 差集。

### 铁律 4：APP 端不要用 SVG 做图标/占位图
- APP `<image>` 有 SVG 解码风险（原生控件只认位图），已知表现「节点在但空白」。已删除 `src/static/icons/pay-icons.js`，`checkout.vue` 支付方式改纯 view+text 色块。
- 事实：`uni.stringToArrayBuffer` 在 uni-app/uni-app-plus **零定义**；产物**无 TextEncoder polyfill**。
- 残留硬编码 SVG data URI 未处理：`goods/detail` defaultAvatar、`favorites` defaultImage、`community/detail` 图片宫格兜底。
- 图标优先级：本地位图 PNG > 纯 view/CSS > 图标字体（`.luc`）；不要再用 SVG。

### 铁律 5：图片 URL 一律 `$img(x)` 包裹
- 后端（moyuyo-server）返回**相对路径** `/uploads/...`（见 `UserController.java:222`），前端必须经 `utils/imageUrl#toAbsoluteImageUrl` 绝对化（`main.js` 全局注册 `$img`）。
- 2026-10-16 全站批量替换 23 文件 / 37 处，复扫为 0。
- **`/static/**` 必须原样返回**（随包内置资源），`toAbsoluteImageUrl` 已加保护。
- 开发 base `http://192.168.6.103:8080`（`.env`/`.env.development`），生产 `https://api.moyuyoshop.com`。
- **占位图必须本地**（真机常无公网）：头像 `/static/images/default-avatar.png`，商品图 `/static/images/default-product.png`。列表 `<image>` 挂 `@error` 兜底，范式见 `pages/order/list.vue`（`resolveItemImage()` + `onItemImageError()` 内部判断已是占位图则 return，防递归）。`pet/profile.vue` 的 `picsum.photos` 外链未处理。

### 铁律 6：`navigationStyle: custom` 页面顶部安全区 v2（60 页在用，禁止自创）
```scss
height: calc(88rpx + var(--status-bar-height, 0px));                                              /* 兜底行在前 */
height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
padding-top: var(--status-bar-height, 0px);
padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
padding-right: 24rpx; padding-left: 24rpx; padding-bottom: 0; box-sizing: border-box;
```
- 基准值必须在 `calc()` 外层；**绝不能写 `max(64rpx, ...)`**（H5 二者皆 0 → 回归）。
- v1（二者**相加**）已废弃，iOS 上翻倍。证据：`dist/dev/app-plus/__uniappview.html:10-14` 自动写 `viewport-fit=cover` → iPhone X+ `env()` 非 0 且等于状态栏高 → 相加翻倍；Android env=0 看不出。
- header 子元素：普通 flex 子项 → 只给 `.header` 加 `padding-top`（再给子项加 `top` 会双倍）；`position:absolute` 子项吃不到父 padding，必须自己加。不可跨页照抄。
- **非 custom 页不要加 `--status-bar-height`**（原生导航栏已在下方），只保留 `env(safe-area-inset-top)`。已修正 `goods/search`、`community/search`、`goods/bundle-deal`。
- **自定义属性不能用重复声明兜底**：`--sb: max(...)` 会覆盖前一条而非回落，必须 `@supports (top: max(0px,0px))` 包住（唯一实例 `tabbar/pet.vue` 的 `--sb`）。
- JS 方案（`getSystemInfoSync().statusBarHeight` 绑内联 padding）已全部废弃，勿再引入。
- 审计基线（2026-10-16）：131 页 / 61 custom / 已占位 60 全 v2 / 漏处理 0 / 待确认 1（`user/splash`）。`src/components/` 无公共导航栏兜底。
- 审计脚本已固化为技能 `~/.workbuddy/skills/moyuyo-status-bar-safe-area/`（`scripts/audit-statusbar.mjs`，单独统计 v1 残留、剥离注释后判定）。

### 铁律 7：后端「参数从哪来」必须和前端实际发的一致；DELETE 一律走 query
- 2026-10-19 事故：`api/cart.js#removeFavorite` 发 `DELETE /api/v1/favorites?productId=..`（刻意不带 body），`FavoriteController` 却只写 `@RequestBody` → 参数恒 null；MyBatis-Plus `LambdaQueryWrapper.eq(col, null)` 拼成 `product_id = null`（恒假），**删 0 行照样 200** → 前端提示"取消收藏成功"、刷新后记录还在。
- 规范：改接口前先看 `src/api/*.js` 实际怎么发（query / body / path）；DELETE 用 `@RequestParam`/`@PathVariable`，需兼容旧客户端再补 `@RequestBody(required=false)`。
- 所有"按 id 删/查"的 service 必须显式校验 id 非空并抛 `IllegalArgumentException`（→400），删 0 行打 warn。`eq(x, null)` 静默失效是这套栈最会骗过测试的坑。
- 雪花 ID（19 位）比较一律 `String()`，禁止 `Number()`/`parseInt`（后端 `JacksonLongToStringConfig` 已把 Long 序列化为 string）。
- 后端部署：`cd moyuyo-server && docker compose build app && docker compose up -d app`（容器 `moyuyo-server`、镜像 `moyuyo-server-app:latest`、project=`moyuyo-server`；根目录那份 compose 的 `container_name: moyuyo-app` **不是**本机在跑的）。构建约 11 分钟。
- 本地验证接口可自造 JWT：HS256 + `.env` 的 `JWT_SECRET`，claims 只需 `sub=<userId>`、`email`（`JwtAuthFilter` 只额外查 redis 黑名单）。

### 铁律 8：`swiper` 不要加 `disable-touch`；原生组件参数不认 CSS 变量
- 2026-10-19 事故：`goods/detail` 主图 `<swiper :disable-touch="true">` → 主图无法左右滑动。uni-h5 `uni-h5.es.js:15508` 的 `useTouchtrack` 回调首行 `if (props.disableTouch) return;`，触摸起点即被丢弃。
- 原生组件（APP 端 swiper、picker 等）的 `*-color` 参数走原生传参，**不支持 `var(--xxx)`**；这些属性必须写字面 `#RRGGBB`（参照 `home.vue` / `flash-sale-detail.vue` 的写法）。
- 排查"轮播不能滑"先看三处：`disable-touch`、外层是否有覆盖层拦截 touch、数据是否真的只有 1 张图。


## 开发数据 / 开发库（moyuyo-server）

- **开发库**：容器 `moyuyo-mysql`，库 `moyuyo_prod`，凭证在 `moyuyo-server/.env`。本机无 mysql 客户端，只能走 docker；PowerShell 里**禁止 `cmd /c`**。
  - 姿势：SQL 写文件 → `Get-Content x.sql -Raw -Encoding UTF8 | docker exec -i moyuyo-mysql mysql -uroot -p<pwd> --default-character-set=utf8mb4 --force -B -D moyuyo_prod 2>&1 | Set-Content out.txt -Encoding UTF8` → Read。`-B` 无表头，`--force` 遇错继续。
- **Flyway 历史表名是 `schema_history`（非默认 `flyway_schema_history`）** —— 查不到默认名不等于「没启用 Flyway」。`db/migration/` 是有效的迁移目录，**新增迁移文件必须避开已应用的最大版本号**，否则启动直接失败：① 版本号重复 → `Found more than one migration with version X`；② 版本号 ≤ 已应用最大版本 → 撞 `outOfOrder=false` 校验。（2026-10-16 实测：`schema_history` 111 行，最大版本 `20261011.01`。当时有人把新迁移写成 `V20260929_01` 与既有 `add_carrier_api_config` 撞号，服务起不来。）
- 该库 `moyuyo_prod` 是唯一业务库；缺表时执行 `moyuyo-api/src/main/resources/db/seed/*.sql` 不受 checksum 约束，可直接改（只改 src，不碰 `target/classes`）。`target/classes/db/migration/` 会残留旧名迁移副本，改文件名后必须 `mvn clean package`，否则旧名会被打进 jar 复现冲突。
- **种子/demo 数据禁用外部图床**（Unsplash/pravatar 会 404，且真机可能无公网）：一律自托管 `/uploads/...` 或随包 `/static/...`。2026-10-16 踩坑：`V20260829_01__test_orders_seed.sql` 的 6 个 Unsplash 外链全 404 → 待发货/待收货订单卡片无图，**表象像前端 bug 实为数据问题**。定位此类问题先查库再怀疑代码。`docs/database/migrations`、`V20260717_02__demo_seed_data.sql`、`V20260718_02__admin_logistics_seed.sql` 仍有外链残留。
- 后端静态资源：`/uploads/**` → 容器 `MOYUYO_UPLOAD_DIR`（默认 `/app/uploads`，卷 `moyuyo-uploads`）。验证：`http://192.168.6.103:8080/uploads/<rel>` 应 200 + `image/jpeg`。

### 构建 / 工具环境
- 本机 bash 环境异常（`dirname`/`ls`/`head`/`tail` 缺失、`wsl.exe` 被拦截），需绕开；文件搜索用 Glob/Grep 工具。
- **PowerShell stdout 不回传**（exit 0 但取不到输出）。取输出可靠姿势：node 脚本 `fs.writeFileSync` 到 `tmp/*.out.txt` → Read；含 ANSI 码会被判二进制，先剥离控制字符。
- 无 HBuilderX 也可 CLI 构建：`node node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p app`（或 `-p h5`）。
