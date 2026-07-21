# AdminController 拆分 — API 变更文档

## 概述

将原有 `AdminController`（`@RequestMapping("/api/admin")`）按职责拆分为 4 个独立控制器，消除 **Divergent Change**（一个类因多种无关原因而变更）的架构坏味。

**REST API 路径不变**，前端无需修改任何代码。

---

## 变更汇总

| 操作 | 文件 | 说明 |
|------|------|------|
| 删除 | `AdminController.java` | 原控制器，所有接口已分发 |
| 新增 | `AdminAuthController.java` | 认证相关接口 |
| 新增 | `AdminDashboardController.java` | 仪表盘相关接口 |
| 新增 | `AdminSystemController.java` | 系统管理相关接口 |
| 新增 | `AdminSettingsController.java` | 设置相关接口 |

---

## 新旧映射表

### 认证 — AdminAuthController

| 方法 | 旧路径 | 新路径 | 变更 |
|------|--------|--------|------|
| POST | `/api/admin/auth/login` | `/api/admin/auth/login` | **无** |
| POST | `/api/admin/auth/logout` | `/api/admin/auth/logout` | **无** |
| GET | `/api/admin/auth/me` | `/api/admin/auth/me` | **无** |

### 仪表盘 — AdminDashboardController

| 方法 | 旧路径 | 新路径 | 变更 |
|------|--------|--------|------|
| GET | `/api/admin/dashboard/stats` | `/api/admin/dashboard/stats` | **无** |
| GET | `/api/admin/dashboard/recent-orders` | `/api/admin/dashboard/recent-orders` | **无** |
| GET | `/api/admin/dashboard/sales-trend` | `/api/admin/dashboard/sales-trend` | **无** |

### 系统管理 — AdminSystemController

| 方法 | 旧路径 | 新路径 | 变更 |
|------|--------|--------|------|
| GET | `/api/admin/system/security-config` | `/api/admin/system/security-config` | **无** |
| GET | `/api/admin/system/info` | `/api/admin/system/info` | **无** |

### 设置 — AdminSettingsController

| 方法 | 旧路径 | 新路径 | 变更 |
|------|--------|--------|------|
| GET | `/api/admin/settings/payment-methods` | `/api/admin/settings/payment-methods` | **无** |

---

## 前端影响评估

**无影响。**

前端通过 `moyuyo-admin/src/api/admin.js` 调用后端 API，所有函数路径保持不变：

- `getDashboardStats()` → `GET /dashboard/stats` ✓
- `getRecentOrders()` → `GET /dashboard/recent-orders` ✓
- `getSalesTrend()` → `GET /dashboard/sales-trend` ✓
- `getSecurityConfig()` → `GET /system/security-config` ✓
- `getSystemInfo()` → `GET /system/info` ✓
- `getPaymentMethods()` → `GET /settings/payment-methods` ✓
- `getAdminInfo()` → `GET /auth/me` ✓

> 注意：admin.js 中的函数在请求时自动拼接 API 基础路径 `/api/admin`，因此拆分后无需任何改动。

---

## 测试影响评估

**无影响。**

`AdminControllerTest.java` 使用 MockMvc 按 HTTP 路径测试，不依赖 `AdminController` 类的直接注入。测试类名虽为 `AdminControllerTest`，但实际通过 URL 发送请求，拆分后测试仍可通过。

---

## 回滚方案

如需回滚，只需：
1. 删除 4 个新控制器文件
2. 从 Git 恢复原 `AdminController.java`

```bash
git checkout -- src/main/java/com/moyuyo/api/controller/admin/AdminController.java
git rm src/main/java/com/moyuyo/api/controller/admin/AdminAuthController.java
git rm src/main/java/com/moyuyo/api/controller/admin/AdminDashboardController.java
git rm src/main/java/com/moyuyo/api/controller/admin/AdminSystemController.java
git rm src/main/java/com/moyuyo/api/controller/admin/AdminSettingsController.java
```
