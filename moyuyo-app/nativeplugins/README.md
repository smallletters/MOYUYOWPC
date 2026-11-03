# 原生插件接入说明（推送）

推送插件目录：

- Android：`nativeplugins/android/moyuyo-push/`
- iOS：`nativeplugins/ios/MOYUYOPush/`

前端统一调用 `src/plugins/push.js`（`registerPush` / `getPushToken` / `setEnabled` 等），
设备上报走 `src/store/user.js` 的 `upsertCurrentDevice()`，后端接口为 `/api/devices`（`pushToken` 字段）。

## Android（FCM）

1. Firebase 控制台 → 项目设置 → 添加 Android 应用：
   - Android package name 必须与 HBuilderX 云打包时的包名完全一致（当前 App 使用 `com.moyuyoshop.app`）
2. 下载 `google-services.json`，放到：
   ```
   nativeplugins/android/moyuyo-push/src/main/assets/google-services.json
   ```
   > 该文件包含项目密钥，禁止提交到 Git。
3. 不需要改 Gradle 插件：`PushModule` 在运行时读取 assets 中的 `google-services.json`，
   手动初始化 FirebaseApp（缺少文件时返回失败，不影响打包）。
4. `moyuyo-push/build.gradle` 已包含 `firebase-bom` 与 `firebase-messaging`。
5. `AndroidManifest.xml` 已声明 `FcmMessagingService`（`com.google.firebase.MESSAGING_EVENT`）。
6. Android 13+ 首次调用 `register` 会向用户申请通知权限（`POST_NOTIFICATIONS`）。

行为：

- `register()` 返回真实 FCM token；未放配置文件或初始化失败时返回 `success=false`，前端不会上报空 token
- `onNewToken` 会把刷新后的 token 写入本地，下次 App 启动时通过 `getToken/register` 读取并上报
- 前台收到消息由 `FcmMessagingService` 构建系统通知；后台由 FCM 自动展示

## iOS（APNs 直连）

1. Apple Developer → Identifiers → 目标 App ID 开启 **Push Notifications** capability；
   在 HBuilderX/iOS 工程中同步开启 Background Modes → Remote notifications。
2. Apple Developer → Keys → 新建 APNs Key（Apple Push Notifications service），下载 `AuthKey_XXXXXXXXXX.p8`，
   记录 **Key ID** 与 **Team ID**。
3. `PushModule` 使用系统 `UserNotifications` API：
   - `register()` 请求通知权限并调用 `registerForRemoteNotifications`
   - 通过 hook `AppDelegate` 的 `didRegisterForRemoteNotificationsWithDeviceToken` 获取 deviceToken（十六进制字符串）
   - 首次调用 token 可能为空（回调异步），前端会在后续启动时再次获取并上报
4. iOS 插件源码里只依赖系统框架；若使用 Firebase iOS SDK 托管 APNs，可改为 `FIRMessaging` 获取 token（当前实现不依赖 Firebase）。
5. 后端需要配置 APNs 通道（见下文），否则 iOS 设备仅能收到站内通知。

## 后端配置

Android（FCM）：

```env
MOYUYO_FCM_ENABLED=true
MOYUYO_FCM_SERVICE_ACCOUNT_FILE=/path/to/firebase-service-account.json
```

iOS（APNs）：

```env
MOYUYO_APNS_ENABLED=true
MOYUYO_APNS_KEY_FILE=/path/to/AuthKey_XXXXXXXXXX.p8
MOYUYO_APNS_KEY_ID=XXXXXXXXXX
MOYUYO_APNS_TEAM_ID=TEAMID
MOYUYO_APNS_BUNDLE_ID=com.moyuyoshop.app
MOYUYO_APNS_ENVIRONMENT=prod   # 开发包用 dev / sandbox
```

后端按 `mo_device.platform` 分流：`IOS` → APNs，其余 → FCM；两个通道都未启用时只发站内通知。

## 联调检查

1. 启动 App 并登录，确认 `mo_device` 表对应用户的 `push_token` 已有值（Android 为 FCM token，iOS 为 64 位十六进制串）。
2. 管理后台 → 推送管理 → 新建「App 推送」→ 发送，查看后端日志：
   - `[push] App Push 完成: ... 成功=N`
   - 失败时看 `[push] App Push 发送失败: ... reason=...`
3. iOS 开发包必须使用 sandbox 环境；App Store/TestFlight 使用 prod，环境与证书不匹配会返回 `BadDeviceToken` / `InvalidProviderToken`。
4. 若返回 `Unregistered` / `InvalidRegistration`，说明设备 token 已失效，需要 App 重新注册后上报新 token。
