package com.moyuyo.api.controller.admin;

/**
 * AdminController 已按职责拆分为以下控制器：
 *
 * - AdminAuthController      → /api/admin/auth/*      认证相关
 * - AdminDashboardController → /api/admin/dashboard/* 仪表盘相关
 * - AdminSystemController    → /api/admin/system/*    系统管理相关
 * - AdminSettingsController  → /api/admin/settings/*  设置相关
 *
 * 删除此类后，请同步删除本文件。
 * @see AdminAuthController
 * @see AdminDashboardController
 * @see AdminSystemController
 * @see AdminSettingsController
 */
@Deprecated
public class AdminController {
    private AdminController() {}
}
