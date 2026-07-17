<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="admin-sidebar">
      <!-- 品牌区： paw 图标 + MOYUYO Admin -->
      <div class="sidebar-brand">
        <span class="sidebar-brand-icon">🐾</span>
        <span class="sidebar-brand-text">MOYUYO Admin</span>
      </div>

      <nav class="sidebar-nav">
        <!-- 顶层独立导航项 -->
        <router-link
          v-for="item in topNavItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="nav-item--active"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>

        <!-- 分隔线 -->
        <div class="nav-divider" />

        <!-- 分组折叠导航 -->
        <div
          v-for="group in navGroups"
          :key="group.label"
          class="nav-group"
        >
          <div class="nav-group-header" @click="toggleGroup(group.label)">
            <span class="nav-group-icon">{{ group.icon }}</span>
            <span class="nav-group-label">{{ group.label }}</span>
            <span class="nav-group-arrow" :class="{ expanded: expandedGroups[group.label] }">▾</span>
          </div>
          <div v-show="expandedGroups[group.label]" class="nav-group-children">
            <router-link
              v-for="item in group.children"
              :key="item.path"
              :to="item.path"
              class="nav-item nav-item--child"
              active-class="nav-item--active"
            >
              <span class="nav-label">{{ item.label }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- 侧边栏底部：蓝色32px圆角方块 + User 图标 + 管理员信息 -->
      <div class="sidebar-footer">
        <div class="admin-info">
          <div class="admin-avatar-box">
            <!-- 内联 SVG User 图标 -->
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <div class="admin-detail">
            <span class="admin-name">Admin</span>
            <span class="admin-role">超级管理员</span>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout">退出登录</button>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="admin-main">
      <header class="admin-topbar">
        <!-- 面包屑：链接 + / 分隔符 + 当前页（加粗） -->
        <div class="breadcrumb">
          <a href="#" @click.prevent="router.push('/dashboard')">首页</a>
          <span class="separator">/</span>
          <span class="current">{{ currentPage }}</span>
        </div>

        <!-- 右侧：操作按钮 + 通知铃铛 -->
        <div class="topbar-right">
          <!-- 通知铃铛：36x36圆角8px灰色背景，18px铃铛图标，右上角7px红色圆点 -->
          <button class="notif-btn" @click="handleNotif">
            <!-- 内联 SVG Bell 图标（18px） -->
            <svg class="notif-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            <span class="notif-dot"></span>
          </button>
        </div>
      </header>

      <div class="admin-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// 顶部独立导航项（常用页面 + 已有页面）
const topNavItems = [
  { path: '/dashboard', icon: '📊', label: '仪表盘' },
  { path: '/orders', icon: '🛒', label: '订单管理' },
  { path: '/products', icon: '📦', label: '商品管理' },
  { path: '/refund', icon: '💰', label: '退款管理' },
  { path: '/users', icon: '👥', label: '用户管理' },
  { path: '/marketing', icon: '📢', label: '营销管理' },
  { path: '/reviews', icon: '📝', label: '内容审核' },
  { path: '/cs', icon: '🎧', label: '客服管理' },
  { path: '/analytics', icon: '📈', label: '数据分析' },
  { path: '/logistics', icon: '🚚', label: '物流管理' },
  { path: '/finance', icon: '💵', label: '财务概览' },
  { path: '/inventory', icon: '📋', label: '库存管理' },
  { path: '/ticket', icon: '🎫', label: '工单管理' }
]

// 分组折叠导航
const navGroups = [
  {
    icon: '📦',
    label: '商品运营',
    children: [
      { path: '/product-analysis', label: '商品分析' },
      { path: '/product-report', label: '商品报表' },
      { path: '/price-manage', label: '价格管理' },
      { path: '/price-history', label: '价格历史' },
      { path: '/review-manage', label: '评价管理' },
      { path: '/product-review', label: '评价审核' }
    ]
  },
  {
    icon: '🛒',
    label: '订单运营',
    children: [
      { path: '/order-export', label: '订单导出' },
      { path: '/order-intercept', label: '订单拦截' },
      { path: '/order-monitor', label: '订单监控' },
      { path: '/order-price-modify', label: '订单改价' },
      { path: '/order-print', label: '订单打印' }
    ]
  },
  {
    icon: '📢',
    label: '营销运营',
    children: [
      { path: '/campaign', label: '活动创建' },
      { path: '/marketing-effect', label: '营销效果' },
      { path: '/ab-test', label: 'A/B测试' }
    ]
  },
  {
    icon: '🚚',
    label: '物流仓储',
    children: [
      { path: '/warehouse-manage', label: '仓库管理' },
      { path: '/overseas-warehouse', label: '海外仓管理' },
      { path: '/merge-package', label: '合包管理' },
      { path: '/split-package', label: '分包裹' },
      { path: '/carrier-compare', label: '承运商对比' },
      { path: '/shipping-strategy', label: '发货策略' },
      { path: '/clearance', label: '清关管理' },
      { path: '/customs', label: '海关管理' }
    ]
  },
  {
    icon: '⚙️',
    label: '系统管理',
    children: [
      { path: '/cms', label: 'CMS管理' },
      { path: '/rbac', label: '权限管理' },
      { path: '/system-config', label: '系统配置' },
      { path: '/sensitive-words', label: '敏感词管理' },
      { path: '/audit-log', label: '审计日志' },
      { path: '/operation-log', label: '操作日志' }
    ]
  },
  {
    icon: '🔒',
    label: '安全合规',
    children: [
      { path: '/gdpr', label: 'GDPR合规' },
      { path: '/risk-control', label: '风控管理' },
      { path: '/risk-rule-engine', label: '风控规则引擎' }
    ]
  },
  {
    icon: '📊',
    label: '数据洞察',
    children: [
      { path: '/rfm', label: 'RFM分析' },
      { path: '/funnel', label: '漏斗分析' },
      { path: '/user-profile', label: '用户画像' },
      { path: '/search-analysis', label: '搜索分析' },
      { path: '/traffic-analysis', label: '流量分析' },
      { path: '/realtime-screen', label: '实时大屏' }
    ]
  },
  {
    icon: '🛠️',
    label: '运营工具',
    children: [
      { path: '/push-manage', label: '推送管理' },
      { path: '/push-detail', label: '推送详情' },
      { path: '/sms', label: '短信管理' },
      { path: '/knowledge-base', label: '知识库' },
      { path: '/batch-import', label: '批量导入' },
      { path: '/live-manage', label: '直播管理' },
      { path: '/satisfaction', label: '满意度管理' },
      { path: '/app-version', label: '版本管理' },
      { path: '/complaint', label: '投诉管理' },
      { path: '/complaint-handle', label: '投诉处理' }
    ]
  },
  {
    icon: '📄',
    label: '结算管理',
    children: [
      { path: '/settlement', label: '结算管理' },
      { path: '/settlement-detail', label: '结算详情' }
    ]
  }
]

// 页面标题映射（包含所有页面）
const pageTitleMap = {
  '/dashboard': '仪表盘',
  '/products': '商品管理',
  '/orders': '订单管理',
  '/users': '用户管理',
  '/marketing': '营销管理',
  '/reviews': '内容审核',
  '/cs': '客服管理',
  '/analytics': '数据分析',
  '/logistics': '物流管理',
  '/settings': '系统设置',
  '/refund': '退款管理',

  // 第一阶段
  '/cms': 'CMS内容管理',
  '/rbac': 'RBAC权限管理',
  '/finance': '财务概览',
  '/inventory': '库存管理',
  '/push-manage': '推送管理',
  '/ticket': '工单管理',

  // 第二阶段
  '/campaign': '活动创建',
  '/complaint': '投诉管理',
  '/review-manage': '评价管理',
  '/product-analysis': '商品分析',
  '/product-report': '商品报表',
  '/product-review': '商品评价审核',
  '/price-manage': '价格管理',
  '/price-history': '价格历史',
  '/order-export': '订单导出',
  '/order-intercept': '订单拦截',
  '/order-monitor': '订单监控',
  '/order-price-modify': '订单改价',
  '/order-print': '订单打印',
  '/sms': '短信管理',
  '/sensitive-words': '敏感词管理',

  // 第三阶段
  '/funnel': '漏斗分析',
  '/rfm': 'RFM分析',
  '/risk-control': '风控管理',
  '/risk-rule-engine': '风控规则引擎',
  '/realtime-screen': '实时大屏',
  '/user-profile': '用户画像',
  '/ab-test': 'A/B测试',
  '/app-version': '应用版本管理',
  '/batch-import': '批量导入',
  '/knowledge-base': '知识库',
  '/search-analysis': '搜索分析',
  '/traffic-analysis': '流量分析',
  '/satisfaction': '满意度管理',
  '/gdpr': 'GDPR合规',
  '/audit-log': '审计日志',

  // 第四阶段
  '/merge-package': '合包管理',
  '/split-package': '分包裹',
  '/carrier-compare': '承运商对比',
  '/overseas-warehouse': '海外仓管理',
  '/warehouse-manage': '仓库管理',
  '/clearance': '清关管理',
  '/customs': '海关管理',
  '/settlement': '结算管理',
  '/settlement-detail': '结算详情',
  '/system-config': '系统配置',
  '/operation-log': '运营日志',
  '/live-manage': '直播管理',
  '/marketing-effect': '营销效果',
  '/shipping-strategy': '发货策略',
  '/content-review-detail': '内容审核详情',
  '/push-detail': '推送详情',
  '/complaint-handle': '投诉处理详情'
}

// 默认展开的组
const expandedGroups = reactive({
  '商品运营': true,
  '系统管理': true
})

function toggleGroup(label) {
  expandedGroups[label] = !expandedGroups[label]
}

const currentPage = computed(() => {
  return pageTitleMap[route.path] || '未知页面'
})

function handleLogout() {
  router.push('/login')
}

function handleNotif() {
  console.log('notifications')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--background-200, #f5f5f7);
}

/* ===== 侧边栏 ===== */
.admin-sidebar {
  width: 240px;
  min-width: 240px;
  background: #1d1d1f;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* 品牌区 */
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-brand-icon {
  font-size: 20px;
  line-height: 1;
}

.sidebar-brand-text {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.02em;
}

/* 导航 */
.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.15s ease;
  margin-bottom: 2px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.9);
}

.nav-item--active {
  background: var(--brand-500);
  color: #ffffff;
}

.nav-item--child {
  padding-left: 42px;
  font-size: 13px;
}

.nav-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

.nav-label {
  font-size: 14px;
}

.nav-item--child .nav-label {
  font-size: 13px;
}

.nav-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.06);
  margin: 6px 16px;
}

/* 分组 */
.nav-group {
  display: flex;
  flex-direction: column;
}

.nav-group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  border-radius: 8px;
}

.nav-group-header:hover {
  color: rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.06);
}

.nav-group-icon {
  font-size: 13px;
  width: 20px;
  text-align: center;
}

.nav-group-label {
  flex: 1;
}

.nav-group-arrow {
  font-size: 10px;
  transition: transform 0.2s ease;
  color: rgba(255, 255, 255, 0.35);
}

.nav-group-arrow.expanded {
  transform: rotate(180deg);
}

.nav-group-children {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 2px;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 蓝色32px圆角方块 + User 图标 */
.admin-avatar-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-500);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.admin-detail {
  display: flex;
  flex-direction: column;
}

.admin-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.admin-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.logout-btn {
  padding: 8px;
  background: rgba(255, 255, 255, 0.08);
  border: none;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.3);
  color: #fff;
}

/* ===== 主区域 ===== */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏 */
.admin-topbar {
  height: 56px;
  min-height: 56px;
  background: var(--card);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

/* 面包屑：链接 + / 分隔符 + 当前页（加粗） */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-400);
}

.breadcrumb a {
  color: var(--text-500);
  text-decoration: none;
  transition: color 0.15s ease;
}

.breadcrumb a:hover {
  color: var(--primary);
}

.breadcrumb .separator {
  color: var(--text-300);
}

.breadcrumb .current {
  color: var(--text-800);
  font-weight: 600;
}

/* 右侧 */
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 通知铃铛：36x36圆角8px灰色背景，18px铃铛图标，右上角7px红色圆点 */
.notif-btn {
  position: relative;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--background-200);
  border: none;
  cursor: pointer;
  color: var(--text-600);
  transition: background 0.15s ease;
}

.notif-btn:hover {
  background: var(--background-300);
}

.notif-icon {
  display: block;
}

.notif-dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--state-error);
}

/* 内容区 */
.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
</style>
