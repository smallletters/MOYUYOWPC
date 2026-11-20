# MOYUYOWPC 项目长期记忆

## moyuyo-app（uni-app 3 + Vue3 + pinia，同构 H5 + APP 原生）

### 铁律 1：`VITE_*` 必须编译期「裸读」
- `vite.config.js` 用 `define` 把 `process.env.VITE_*` 替换成字面量；APP 端运行时无 `process`。
- 正解 `const base = process.env.VITE_X || ''`；**禁止** `typeof process !== 'undefined'` 守卫、`import.meta.env`、`process.env[key]` 动态下标（三者都会让 APP 端值恒空/白屏）。
- 统一入口 `src/utils/config.js` 的静态 `ENV` 对象（新增变量必须同步加进去 + `.env*` 里定义）。

### 铁律 2：APP 端读 URL 参数禁用 `getCurrentPages().options`
- `pageVm.options` 在 app-plus 永不存在（`$page.options` 才是），H5 有 → 典型「仅真机复现」Bug。
- 统一用 `src/utils/pageQuery.js#getPageQuery()`。页面模式：`onLoad(options)` 主路径 + 幂等 `bootstrap()`，`booted` 去重；读参必须在早退之前、异步动作之前；`decodeURIComponent` 必须 try/catch；ID 一律 `String()`（雪花 ID > 2^53）。

### 铁律 3：新页面必须注册进 `src/pages.json`
- `pages.json` 是 JSONC，`JSON.parse` 会炸，用 Edit 文本编辑。
- 已知漏注册：`user/coupons`、`user/wallet-recharge`；调用方路径写错：`order/after-sales.vue:187`、`points-mall.vue:145`。

### 铁律 4：APP 端不要用 SVG 做图标/占位图
- 图标优先级：本地位图 PNG > 纯 view/CSS > 图标字体。`uni.stringToArrayBuffer` 在 uni-app 无定义，产物无 TextEncoder polyfill。

### 铁律 5：图片 URL 一律 `$img(x)` 包裹
- 后端返回相对路径 `/uploads/...`，经 `utils/imageUrl#toAbsoluteImageUrl` 绝对化（`main.js` 注册 `$img`）；`/static/**` 原样返回。
- 占位图必须本地：`/static/images/default-avatar.png`、`default-product.png`，`<image>` 挂 `@error` 兜底（防递归，范式 `pages/order/list.vue`）。
- dev base `http://192.168.6.103:8080`，prod `https://api.moyuyoshop.com`。

### 铁律 6：`navigationStyle: custom` 顶部安全区 v2
```scss
height: calc(88rpx + var(--status-bar-height, 0px));            /* 兜底行在前 */
height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
padding-top: var(--status-bar-height, 0px);
padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
```
- 基准值必须在 `calc()` 外层，绝不写 `max(64rpx, ...)`；v1（相加）已废弃（iOS 翻倍）。
- 普通 flex 子项只给 `.header` 加 `padding-top`；`position:absolute` 子项要自己加。
- 非 custom 页**不要**加 `--status-bar-height`；自定义属性不能用重复声明兜底，要用 `@supports`。
- 审计脚本：技能 `~/.workbuddy/skills/moyuyo-status-bar-safe-area/scripts/audit-statusbar.mjs`。

### 铁律 7：前后端参数传递必须一致；DELETE 走 query
- `eq(col, null)` 拼成 `= null` 恒假 → **删 0 行照样 200**，最会骗过测试的坑。改接口前先看 `src/api/*.js` 实际怎么发（query/body/path）。
- 按 id 删/查必须显式校验非空；ID 比较一律 `String()`。
- 后端部署：`cd moyuyo-server && docker compose build app && docker compose up -d app`（容器/镜像 `moyuyo-server(-app)`，约 11 分钟）。
- 本地验证接口可自造 JWT：HS256 + `.env` 的 `JWT_SECRET`，claims 仅需 `sub`、`email`。

### 铁律 8：`swiper` 不要 `disable-touch`；原生组件参数不认 CSS 变量
- 原生组件 `*-color` 必须写字面 `#RRGGBB`。
- `swiper` 无 `click` payload：点击绑到 `swiper-item` 内部普通节点，用 v-for 闭包索引，勿依赖 `e.detail.current`。
- tabBar 页跳转只能 `switchTab` 且不带 query；解析 CMS 里带域名的站内路径用纯字符串正则，勿用 `URL` 构造函数。

### 铁律 9：纯文本禁用 OWASP 实体转义版；管理端要单独构建
- `sanitizePlainText`/`sanitizeRichText` 把所有非 ASCII 转成数字实体 → `<text>`/`{{}}` 渲染成乱码；纯文本用 `sanitizeText()`，富文本才用 `sanitizeRichText`。
- `moyuyo-admin` 是独立 Vue 项目，产物打进后端 jar `static/admin/`：必须 `npm run build` + `docker compose build app`。
- `moyuyo-admin/vite.config.js` 必须 `emptyOutDir: false`（清空 400+ 产物会被 safe-delete 拦）。
- 管理端 axios baseURL 已是 `/api/admin`。

### 铁律 10：APP 端页面脚本在逻辑层，没有 DOM
- `<script>` 跑在 jscore/V8，`document`/`window`/WebGL 永不存在；`typeof document === 'undefined'` 不会自愈，别写重试。
- 唯一出口 `<script module="x" lang="renderjs">`；renderjs 块由 esbuild 单独打包，**不认 `@` 别名、不能用动态 `import()`**，必须静态相对路径。
- 依赖 uni 文件 API 的逻辑只能留逻辑层，把结果当纯数据传视图层；大资源留 HTTP 回退。
- 参考实现 `src/utils/pet3d/space3d-engine.js` + `create-engine.js` + `pages/pet/space-3d.vue`。

## 开发数据 / 工具环境

- 开发库：容器 `moyuyo-mysql`，库 `moyuyo_prod`，凭证在 `moyuyo-server/.env`。本机无 mysql 客户端，走 docker；PowerShell 禁止 `cmd /c`。
  姿势：SQL 写文件 → `Get-Content x.sql -Raw -Encoding UTF8 | docker exec -i moyuyo-mysql mysql -uroot -p<pwd> --default-character-set=utf8mb4 --force -B -D moyuyo_prod` → 读输出文件。
- Flyway 历史表名是 **`schema_history`**（非默认名）。新增迁移必须避开已应用最大版本，否则启动即失败（撞号 / outOfOrder）。
- `moyuyo_prod` 是唯一业务库；种子 SQL 在 `db/seed/`，改完需 `mvn clean package` 防 `target/classes` 旧副本。
- 种子/demo 数据禁用外部图床（会 404），一律自托管 `/uploads/` 或 `/static/`。列表无图先查库再怀疑代码。
- 本机 bash 环境异常（`dirname`/`ls`/`head` 缺失），优先用 Read/Glob/Grep 工具。PowerShell stdout 不回传 → 先写文件再 Read。
- 无 HBuilderX 也可 CLI 构建：`node node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p app|-p h5`。
