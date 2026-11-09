# MOYUYO 安全审计报告（APP 端 + 后端）

- 审计日期：2026-11-08
- 审计范围：`moyuyo-server`（Spring Boot 多模块后端 + admin SPA）、`moyuyo-app`（uni-app 用户端）、部署配置（docker-compose / .env 模板 / 清单文件）
- 审计方式：静态代码审查 + 配置审查（未做运行时渗透）
- 结论：整体安全基线较好（JWT、RBAC、支付验签、越权校验、XSS 净化、CORS 都有落地），但存在 **2 个高危问题**，主要集中在“忘记密码”和“后台文件上传”。建议优先修复后再上线。

## 一、风险摘要

| 编号 | 等级 | 问题 | 影响 |
| --- | --- | --- | --- |
| H-1 | 高 | 密码重置 6 位验证码可被穷举，且失败计数从未生效 | 账号接管（重置任意命中账号的密码） |
| H-2 | 高 | 后台上传允许 SVG，且上传/删除接口绕过 RBAC | 存储型 XSS + 后台 token 窃取 + 越权删文件 |
| M-1 | 中 | 两步验证（2FA）未在登录流程中强制校验 | 用户以为开了 2FA，实际登录不校验，安全预期落空 |
| M-2 | 中 | 邮箱验证码无失败次数限制，且未与请求方绑定 | 可枚举 6 位码完成任意邮箱验证 |
| M-3 | 中 | 测试账号密码以明文进入 Git（`login.json`） | 账号被直接登录；测试库若对外开放则风险升级 |
| M-4 | 中 | 管理员 token 24 小时有效，角色写入 token 且不实时查库 | 禁用/降权管理员后最长 24h 仍可操作 |
| M-5 | 中 | IP 限流未覆盖重置密码/手机验证码等端点，限流又是全局单桶 | 验证码爆破窗口放大、短信轰炸与登录 DoS |
| M-6 | 中 | `ProdConfigValidator` 已被禁用 | 生产可用占位/弱配置启动，缺少启动期兜底 |
| L-1 | 低 | 社区会话未读数拼接 SQL（`inSql`） | 当前入参为 Long 不可注入，但属于危险写法 |
| L-2 | 低 | H5 `index.html` 会把 `CONSUMER_SECRET` 注入页面全局变量 | 一旦配置真实密钥即明文暴露在页面源码 |
| L-3 | 低 | 自定义 scheme 未做来源校验、WebView 允许 localhost 明文 | 钓鱼回跳 / 本机服务探测面 |
| L-4 | 低 | APP 申请了 `QUERY_ALL_PACKAGES`、外部存储、定位等宽权限 | 隐私合规与商店审核风险 |

## 二、高危问题

### H-1 密码重置验证码可穷举，失败计数形同虚设

**证据**

- `moyuyo-server/moyuyo-service/src/main/java/com/moyuyo/service/impl/AuthServiceImpl.java:590-626`：发送重置邮件时生成 6 位数字，Redis key 为 `auth:resetcode:{code}`，value 为邮箱。验证码只按“码”存，不按“邮箱/请求方”存。
- 同文件 `:631-707`：重置接口直接按用户输入的 6 位码查邮箱，查到即改密；错误时直接抛异常，**全文没有任何地方对 `auth:resetcode-fail:{code}` 做自增**（grep 确认只有初始化与读取/删除）。注释写的“每错一次 INCR，超过 5 次强制失效”并未实现。
- `moyuyo-server/moyuyo-common/src/main/java/com/moyuyo/common/security/JwtAuthFilter.java:75`：`/api/v1/auth/password/reset` 在白名单中，公网匿名可直接调用。
- `moyuyo-server/moyuyo-common/src/main/java/com/moyuyo/common/filter/IpRateLimitFilter.java:59-66`：IP 限流的敏感路径列表里没有 `/password/reset`，只有全局 `authLogin`（prod 300 次/分钟，且被登录等接口共享）。

**影响**

攻击者可对公网持续枚举 6 位码。每命中一个“当前有效”的重置码，就能把对应账号的密码改成攻击者指定的值，完成账号接管。成功概率与同时存在的有效重置码数量成正比；由于失败对无效码不计数、不锁定，攻击者可以无限换码尝试。

附带问题：同码冲突（1/100 万）时后一次请求会覆盖前一次的 `code -> email` 映射，前一个用户点击自己的码可能重置成另一个人的账号。

**修复建议**

1. 重置凭证改为 32 字节以上随机不透明 token（或至少 8 位以上字母数字），不走 6 位数字。
2. 失败计数必须落地：对“每次失败”按邮箱与 IP 双维度 `INCR`，5 次后作废该邮箱的重置码并短暂封禁；无效码也要计数。
3. 验证码/Token 与邮箱绑定，且与请求上下文（IP、UA、短时会话）弱绑定，降低“撞码”收益。
4. 将 `/api/v1/auth/password/reset`、`/phone/send-code`、`/phone/login`、`/2fa/*` 加入 IP 敏感路径限流；避免 `authLogin` 全局单桶被一个攻击者吃满导致全站登录不可用。
5. 重置成功后吊销该用户全部 refresh token 与 access token（当前只改密码，其他会话仍有效）。

### H-2 后台 SVG 上传导致存储型 XSS；上传/删除接口绕过 RBAC

**证据**

- `moyuyo-server/moyuyo-api/src/main/java/com/moyuyo/api/controller/admin/AdminUploadController.java:82-85`：图片白名单包含 `svg` / `image/svg+xml`。
- 同文件 `:124-189` 直接落盘；`moyuyo-server/moyuyo-api/src/main/java/com/moyuyo/api/config/WebMvcConfig.java:208-210` 将 `/uploads/**` 作为同源静态资源公开返回（不需要登录）。
- `moyuyo-server/moyuyo-api/src/main/java/com/moyuyo/api/config/SecurityHeadersFilter.java:122-137`：CSP 为 `script-src 'self' 'unsafe-inline' 'unsafe-eval'`，SVG 内联脚本与事件处理器可执行。
- `moyuyo-server/moyuyo-api/src/main/java/com/moyuyo/api/config/AdminPermissionFilter.java:54-60,72-77`：明确把 `/api/admin/upload*` 从模块权限校验中放行，只校验登录态。
- `moyuyo-server/moyuyo-admin/src/api/index.js:54-57`：后台 token 存在 `localStorage`。

**影响**

任意后台账号（包括只读、客服等低权限角色）都能上传带 `<script>` 的 SVG，并把 `/uploads/....svg` 链接发给超管或财务。若后台 SPA 与 API 同源（当前默认由 Spring Boot `/admin/**` 提供），打开该链接后脚本在后台源下执行，可直接读取 `localStorage.admin_token`，实现 **低权限 → 超管** 提权。

同时 `DELETE /api/admin/upload/image` 也绕过 RBAC，且只校验 URL 是否以 `/uploads/` 开头，不区分场景/归属，低权限后台可删除用户私信图片、社区图片等 C 端文件。

**修复建议**

1. 后台上传移除 `svg`，或对 SVG 做 OWASP Sanitizer 净化；更稳妥的是用独立域名/对象存储提供用户内容，并强制 `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff`。
2. CSP 去掉 `'unsafe-inline'` / `'unsafe-eval'`，改用 nonce/hash（Vue 构建产物可配 nonce；富文本用 DOMPurify 在前端二次净化）。
3. 上传/删除纳入 RBAC（`upload:create` / `upload:delete`）；删除接口按 `scene` 与上传人限制，禁止后台删除 `DM`、`USER` 场景文件。
4. 后台 token 建议改存内存 + 短期 access token（或 httpOnly SameSite Cookie），降低 XSS 直接窃取的可利用性。

## 三、中危问题

### M-1 2FA 未在登录流程强制校验

`AuthServiceImpl.login()`（`:289-376`）密码校验通过后直接 `generateTokenPair`；`refreshToken`、手机号登录、Magic Link 登录同样没有读取 `twoFactorEnabled`。`setTwoFactorEnabled`（`:951-999`）只控制开关本身。结果：用户开启 2FA 后，登录仍只需密码，2FA 只是“设置页上的一个状态”。修复：登录成功后若 `two_factor_enabled=1`，返回一次性 challenge（短时 Redis），校验 `/2fa/verify` 后再发 token；或至少明确下线该功能，避免误导用户。

### M-2 邮箱验证码可枚举且未绑定请求方

- `AuthServiceImpl.java:532-563`：6 位码存 `auth:verify:{email}`，无失败计数、无发送频次（按邮箱）限制。
- `:565-587` 与 `:235-245`：校验失败不计数，成功才删除。
- `/api/v1/auth/email/verify-confirm` 在 JwtAuthFilter 白名单中匿名可调。

影响：可枚举 6 位码完成“任意邮箱验证”，用于抢注受害者邮箱、伪造邮箱归属。修复：按邮箱 + IP 失败计数并锁定；发送加入冷却时间与每日上限；验证码只允许一次性使用并限制有效窗口（当前 5 分钟已可）。

### M-3 测试账号明文进入 Git

`git ls-files` 显示 `moyuyo-server/login.json` 被跟踪，内容为：

```
{"email":"bloom@bloomwitness.com","password":"BloomWitness123"}
```

此外 `moyuyo-server/docker-compose.dev-only.yml:152` 与 `application-dev.yml:151-156` 含 dev 管理员口令默认值 `moyuyo_Dev#2026!`。这些值一旦在测试/预发环境复用真实账号口令，或 dev 环境被误暴露到公网，即可直接登录。

修复：删除 `login.json`（并清理 Git 历史），改用环境变量/`.env.local`；dev 默认口令改为随机生成或强制显式注入；轮换已泄露的测试账号口令。

### M-4 管理员会话失效延迟

- `AdminAuthController.java:135` 将 `role` 写进 JWT；`JwtAuthFilter.java:137-177` 只校验签名、黑名单和 role 非空，不查 `mo_admin_user.status`。
- `application-prod.yml:203-209` access token 有效期 24 小时。

影响：管理员被禁用、删除或降权后，其已签发 token 最长 24h 仍可访问；角色变更也不同步。修复：管理端 token 有效期收紧到 2~4h；JWT 只放用户 ID，每次请求查 `AdminUser.status` + 角色（可缓存 1~5 分钟）；禁用/降权时写入黑名单。

### M-5 限流覆盖不全且为全局单桶

`IpRateLimitFilter.java:59-66` 只覆盖 login/register/email verify/forgot/magic-link send/payments create；`resilience4j.authLogin` 是**全实例单桶**（prod 300/min），不区分 IP/账号。

影响：攻击者可用一个脚本吃满 `authLogin` 配额，让正常用户无法登录/重置密码（可用性 DoS）；手机验证码接口无 IP 维度限制，可按号码逐个发送，造成短信费用与骚扰（服务内仅按单号码限 1 条/分钟、5 条/小时）。

修复：敏感端点全部纳入 IP 限流；`authLogin` 拆分按 IP/账号的本地限流桶；短信发送增加 IP + 全局配额与图形/行为校验。

### M-6 生产配置强校验被禁用

`ProdConfigValidator.java:27-37,44-54` 已是空实现，prod 启动不再校验必填项/弱占位符。结合 `application-prod.yml:268` 的 `sk_test_placeholder` 默认值，若运维漏配 Stripe 密钥，支付会进入 Mock 分支，且 `whsec_placeholder` 会让 webhook 验签恒失败。

修复：恢复启动期校验（至少校验 `JWT_SECRET`、数据库/Redis、支付密钥、`MOYUYO_CORS_ALLOWED_ORIGINS`、`ADMIN_PASSWORD`）；或改为“启动健康检查 + 告警”。

## 四、低危 / 加固建议

- **L-1 拼接 SQL**：`moyuyo-service/src/main/java/com/moyuyo/service/CsService.java:166` 使用 `inSql("... WHERE user_id = " + userId)`。当前 `userId` 为 `Long`，不可注入，但建议改为参数化或 `exists` 子查询，避免后续改成字符串入参时出事。
- **L-2 H5 页面注入密钥**：`moyuyo-app/index.html:12-17` 会把 `CONSUMER_KEY/CONSUMER_SECRET` 写入 `window.__MOYUYO_CONFIG__`；`utils/config.js:24-27,64-65` 也会读取。当前生产 `.env.production` 是占位符，暂无泄露；建议彻底移除这两个字段与对应读取逻辑，WooCommerce 凭证只留在服务端。
- **L-3 WebView / scheme**：`moyuyo-app/src/utils/payAppBridge.js:58-63` 在生产构建中仍允许 `http://localhost` / `127.0.0.1`；`manifest.json:59-93` 注册了 `moyuyo` 与 `com.moyuyoshop.app` 自定义 scheme，scheme 可被任意 App 抢注。支付结果已回查服务端（`pages/order/pay.vue:485-533`），伪造成功无效，但仍可能存在钓鱼回跳。建议生产构建禁止 localhost 明文，回跳链接增加一次性签名/订单绑定。
- **L-4 APP 权限**：`moyuyo-app/src/manifest.json:46-57` 申请了 `QUERY_ALL_PACKAGES`、`READ/WRITE_EXTERNAL_STORAGE`、精确定位等；建议按实际功能裁剪（Google Play 对 `QUERY_ALL_PACKAGES` 审核严格）。
- **L-5 后台 `v-html`**：`moyuyo-admin/src/views/OrderPrint.vue:357,373` 与 `ProductEdit/RichTextEditor.vue:51` 使用 `v-html`。动态值已转义，但模板/商品富文本本身是原始 HTML；低权限后台写恶意模板仍可能 XSS 高权限后台。建议渲染前统一 DOMPurify。
- **L-6 白名单前缀偏宽**：`JwtAuthFilter.java:83-112` 对 `/api/v1/products/`、`/api/v1/community/posts/`、`/api/v1/coupons/`、`/api/v1/help/` 做 GET 前缀放行；未来在这些前缀下新增敏感 GET 接口会被默认公开。建议改为显式路由清单或按 Controller 白名单。
- **L-7 WebSocket token 放 URL**：`DmWebSocketAuthInterceptor.java:46-50` 与 `moyuyo-app/src/utils/dmSocket.js:42-46` 通过 `?token=` 传 JWT。Nginx access log / 代理日志可能记录完整 URL。建议改用握手后首帧鉴权或一次性短期 ticket。
- **L-8 dev 口令**：`docker-compose.dev-only.yml:152` 的 `ADMIN_PASSWORD` 默认值不应存在，即使是 dev 文件，也建议改为 `:?must be set`。

## 五、已确认做得较好的部分（不必返工）

- **JWT**：HMAC 验签、过期时间、Bearer 长度上限、Redis 黑名单 fail-closed；`/api/admin/**` 强制角色非空；`Keys.hmacShaKeyFor` 会拒绝 <32 字节弱密钥。
- **管理端 RBAC**：按 `resource:action` 逐请求校验；退款单独角色白名单；过滤器顺序显式（JWT 先于权限）。
- **支付回调**：Stripe 用官方 `Webhook.constructEvent`，PayPal 调官方验签接口且校验 `cert_url` 域名白名单；签名失败抛 500 触发渠道重投，不会静默丢弃。
- **越权校验**：订单详情/取消/确认/删除、退款申请、售后详情、客服会话关闭、私信会话成员均校验 `userId` 归属。
- **XSS**：社区/资料使用 OWASP HTML Sanitizer 白名单；昵称/简介走纯文本净化；URL 协议白名单。
- **SQL**：MyBatis-Plus 参数化为主，未发现 `${}` 注入（仅 L-1 一处 `inSql` 拼接 Long）。
- **CORS**：显式 origin 白名单、禁 `*`、校验字符集/长度，prod 未配置则不注册。
- **安全头**：nosniff、X-Frame-Options DENY、Referrer-Policy、Permissions-Policy、HTTPS 下 HSTS。
- **上传**：路径 `normalize` + `startsWith` 防穿越、UUID 重命名、扩展名 + Content-Type 双白名单（SVG 除外，见 H-2）。
- **APP**：没有 `v-html`/`innerHTML`；WebView 有 host 白名单；支付成功必须回查服务端；支付子 WebView 未注入 plus API。

## 六、建议修复顺序

1. H-1 密码重置（先加失败计数 + 换长 token + 会话吊销）
2. H-2 SVG/XSS 与上传 RBAC（移除 SVG、收紧 CSP、上传删除纳入权限）
3. M-3 清理 Git 中的明文凭证并轮换口令
4. M-1 / M-2 2FA 与邮箱验证码
5. M-4 / M-5 / M-6 会话时效、限流与生产配置校验
6. L 系列按迭代逐步加固

## 七、修复记录（2026-11-08）

### H-1 已修复

- `ResetPasswordRequest` 新增必填 `email`，验证码与邮箱强绑定。
- `AuthServiceImpl#sendPasswordReset`：Redis key 由 `auth:resetcode:{code}` 改为 `auth:resetcode:{email}`（value 存 code）；新增同邮箱 60 秒发送冷却 `auth:resetcode-cooldown:{email}`。
- `AuthServiceImpl#resetPassword`：按 `email + code` 校验；失败计数 `auth:resetcode-fail:{email}` 现在会真正 `INCR`，达到 5 次立即作废验证码；成功后删除验证码/失败计数并清理发送冷却。
- 失败与"验证码不存在"返回完全相同的错误文案，避免形成邮箱注册枚举 oracle；失败/超限时不删除 60 秒发送冷却，防止"5 次猜错后立刻重发"绕过频率限制。
- 改密成功后调用 `revokeAllRefreshTokens(userId)`，吊销该用户全部 refresh token（与登出共用同一实现）。
- `IpRateLimitFilter` 将 `/api/v1/auth/password/reset` 纳入 IP 维度限流。
- APP 端同步调整：`api/user.js`、`store/user.js`、`pages/user/forgot.vue` 提交 `email + token + newPassword`。
- 已通过 `AuthServiceImplPasswordResetTest`（含新增的“错误码必须 INCR 失败计数”用例）。

### 改密后旧 access token 失效（已补充修复）

- `JwtUtil` 新增 `ver` claim 与 4 参 `generate(..., tokenVersion)`；历史 3 参调用不写 claim，鉴权侧按版本 0 兼容。
- `JwtAuthFilter` 用一次 `multiGet` 同时校验黑名单与 token 版本号；版本不一致返回 401。C 端与后台使用独立命名空间（`auth:token-version:` / `auth:admin-token-version:`），避免同 ID 互相影响。
- `AuthServiceImpl`：登录/续期时把当前版本写入 access token 与 refresh token（refresh value 为 `userId:version`）；`resetPassword` / `changePassword` 递增版本并吊销 refresh token。
- `AdminAuthController` 登录写入管理员版本；`AdminStaffServiceImpl` 重置管理员密码递增管理员版本；`AdminUserManageServiceImpl` 重置用户密码递增用户版本并吊销 refresh token。
- `DmWebSocketAuthInterceptor` 握手阶段同样校验黑名单 + token 版本。
- 强制断开旧 WebSocket：新增 `UserTokenInvalidatedEvent`，改密/重置密码事务提交后由 `DmWebSocketEventListener` 触发 `DmSessionRegistry#closeUserSessions`，先下发 `session_invalidated` 消息，再以自定义关闭码 `4001` 关闭该用户全部在线连接；APP `dmSocket.js` 收到 4001 后停止自动重连，避免用失效 token 反复握手。
- 多实例跨节点下线：新增 `TokenInvalidationBroadcaster` + `TokenInvalidationRedisConfig`，本机关闭连接后向 Redis Pub/Sub 频道 `auth:token-invalidated` 广播 `{userId, reason, source}`；每个实例订阅并关闭本机对应连接，`source` 为实例 UUID，发布方忽略自己发出的消息，避免重复处理。
- APP `change-password.vue` 改密成功后主动清理本地登录态并跳登录页，避免旧 token 触发反复 401。
- 残余风险：Redis Pub/Sub 是 fire-and-forget，恰好处于断线重连窗口的实例可能漏收一条广播，该实例上的旧 WS 连接会保留到下次断开；但旧 token 已失效，客户端下次 HTTP 请求会 401 并重建连接。Redis 数据丢失会导致已递增版本的 token 全部失效，用户需重新登录（fail-closed）。

### M-1 / M-2 已修复（2FA 与邮箱验证码）

- 2FA 登录强制校验：`LoginRequest` / `PhoneLoginRequest` 新增可选 `twoFactorCode`；`login` 与 `loginByPhone` 在主凭证（密码/短信码）通过后，若账号开启 2FA：
  - 未带验证码 → 发送邮箱验证码并返回 `TWO_FACTOR_REQUIRED`（HTTP 428），**不签发任何 token**；
  - 已带验证码 → 校验通过后才签发 token；验证码错误返回 `TWO_FACTOR_INVALID`，失败 5 次立即作废。
- 2FA 发码防滥用：同一用户 60 秒发送冷却 + 24 小时最多 10 次；新码写入时重置失败计数；发送失败释放冷却。
- 开启 2FA 的二次确认接口 `verifyTwoFactorCode` 也接入同一失败计数，防止 6 位码被穷举。
- Magic Link 登录对 2FA 账号直接拒绝（Magic Link 仅证明邮箱可达，与 2FA 邮箱码是同一因子，无法构成第二因子），强制走“密码 + 2FA”。
- 手机号登录流程调整：短信验证码在 2FA 通过后才标记已使用，2FA 未完成/失败时可用同一短信码重试。
- APP 登录页新增 2FA 验证码输入区（邮箱与手机号登录共用），处理 `TWO_FACTOR_REQUIRED` / `TWO_FACTOR_INVALID` / 发码失败；切换登录方式时清空 2FA 状态。
- 邮箱验证码加固：发送增加同邮箱 60 秒冷却 + 24 小时 10 次上限；验证码与失败计数按邮箱存储，错误 5 次作废；注册校验与确认接口均使用统一失败文案，避免邮箱枚举；`/email/verify-confirm` 纳入 IP 限流。
- 开启 2FA 时吊销旧会话：`setTwoFactorEnabled` 成功开启后递增 token 版本、吊销全部 refresh token，并发布 token 失效事件关闭各实例 WebSocket；版本递增失败会回滚本次 2FA 开启。APP 安全设置页在开启成功后主动清理本地登录态并跳登录页。
- 新增 `AuthServiceImplSecurityHardeningTest` 覆盖 2FA 强制、失败计数、邮箱验证码失败计数、开启 2FA 吊销旧会话；`AuthServiceImplPasswordResetTest`、`JwtUtilTest`、`AdminAuthControllerTest`、`DmSessionRegistryTest`、`TokenInvalidationBroadcasterTest` 均通过；APP 修改文件 `eslint` 通过。
- 残余风险：关闭 2FA 不会吊销旧会话（该操作不提升安全门槛，符合预期）。`AuthControllerTest` 的 2 个失败是测试 H2 schema 缺少 `gender` 列的历史问题，与本次改动无关。

### M-4 / M-5 已修复（管理员会话时效与限流覆盖）

- 管理端账号状态实时校验：新增 `AdminAccountStateService`，按管理员 ID 缓存当前有效角色（60 秒 TTL）；`AdminPermissionFilter` 对除登录外的所有 `/api/admin/*` 请求校验：
  - 账号不存在/非 ACTIVE → 401（前端清 token 重新登录）；
  - 角色与 JWT 快照不一致 → 以数据库最新角色为准，权限立即按新角色校验；
  - `AdminStaffServiceImpl.updateUser/deleteUser` 主动 evict 缓存，禁用/删除/改角色立即生效，无需等待 60 秒。
- 管理端 token 有效期收紧：`JwtUtil.generateAdmin` 使用 `jwt.admin-expire-hours`（默认 4 小时，prod 可用 `JWT_ADMIN_EXPIRE_HOURS` 覆盖），不再沿用 C 端 24 小时。
- IP 限流补全并按端点分桶：`IpRateLimitFilter` 新增手机发码/手机登录、Magic Link 验证、2FA 发码/校验、管理端登录；key 由「IP + 分钟」改为「IP + 分钟 + 端点」，单个端点被刷不会挤占同 IP 其他端点配额。
- 认证限流实例拆分：心跳、邮箱验证码、找回密码、重置密码、Magic Link、手机发码、手机登录分别使用独立 `@RateLimiter` 实例，避免心跳等高频流量耗尽登录/注册的全局配额（实例已在 `application.yml` 提供默认值）。
- 短信发送防滥用：`sendPhoneCode` 增加全站每分钟 100 条总量上限（多实例共享 Redis），并对客户端传入的 `purpose` 做 LOGIN/REGISTER/RESET_PASSWORD/CHANGE_PHONE 白名单校验。
- 测试库与单测修正（仅测试代码，不影响生产逻辑）：
  - `schema-test.sql` 补齐 `mo_user` 缺失列（gender/bio/registration_channel/points_frozen/隐私开关/封禁字段等），修复 `AuthControllerTest.login_*` 因查询缺列导致的 500；
  - `schema-test.sql` 新增 `mo_upload_file` 表，修复 `AdminPermissionFilterTest.uploadApi` 500；
  - `OrderServiceTest` 补全 `OrderServiceImpl` 新增依赖的 Mock，并默认返回零优惠计价与积分规则，修复 5 个 NPE；
  - `AuthControllerTest` 新增匿名手机号登录回归用例。
- 验证：`AuthControllerTest` 7/7、`AdminPermissionFilterTest` 10/10、`AdminAuthControllerTest` 11/11、`OrderServiceTest` 20/20、`JwtUtilTest`、`AuthServiceImplSecurityHardeningTest`、`AuthServiceImplPasswordResetTest` 全部通过。
- 残余风险：分布式多 IP 攻击仍可能打满 `authLogin` 全局限流；短信场景尚未接入图形/行为验证码。若业务规模扩大，建议将登录限流改为按 IP + 账号维度，并在短信发送前增加人机校验。

### H-2 已修复

- `AdminUploadController`：图片白名单移除 `svg` / `image/svg+xml`，`extractExtFromContentType` 不再推断 svg。
- `SecurityHeadersFilter`：对 `/uploads/**` 下的 `.svg/.svgz/.html/.htm/.xhtml/.xml/.js/.mhtml/.shtml` 强制 `Content-Disposition: attachment` + `Content-Security-Policy: default-src 'none'; sandbox`，覆盖历史残留的可执行文档；判断前先 URL 解码并去掉 `;` 矩阵参数，防编码/分号绕过。
- `AdminUploadController#deleteImage`：仅允许 `SUPER_ADMIN` / `OPERATOR` 删除，且要求 `mo_upload_file` 中存在 `scene=ADMIN` 记录；额外禁止删除 `dm/` 私信目录文件，禁止删除用户/私信/社区图片。
- 已通过 `mvn -pl moyuyo-api -am -DskipTests compile` 编译验证。
- 残余风险：全局 CSP 仍保留 `'unsafe-inline'` / `'unsafe-eval'`（后台 SPA 依赖）；上传接口本身仍只校验登录态（未新增 `upload:*` 权限种子）。建议后续以 nonce 方式收紧 CSP，并补 `upload:create/delete` 权限矩阵。
