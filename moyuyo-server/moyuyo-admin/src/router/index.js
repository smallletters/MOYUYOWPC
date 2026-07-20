import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/AdminLogin.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    component: () => import('../views/AdminLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: [
      // ===== 已有页面 =====
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue')
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('../views/OrderList.vue')
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('../views/OrderDetail.vue')
      },
      {
        path: 'products',
        name: 'ProductList',
        component: () => import('../views/ProductList.vue')
      },
      {
        path: 'products/edit/:id',
        name: 'ProductEdit',
        component: () => import('../views/ProductEdit.vue')
      },
      {
        path: 'users',
        name: 'UserList',
        component: () => import('../views/UserList.vue')
      },
      {
        path: 'marketing',
        name: 'MarketingList',
        component: () => import('../views/MarketingList.vue')
      },
      {
        path: 'reviews',
        name: 'ContentReview',
        component: () => import('../views/ContentReview.vue')
      },
      {
        path: 'cs',
        name: 'CustomerService',
        component: () => import('../views/CustomerService.vue')
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('../views/Analytics.vue')
      },
      {
        path: 'logistics',
        name: 'LogisticsList',
        component: () => import('../views/LogisticsList.vue')
      },
      {
        path: 'settings',
        name: 'SystemSettings',
        component: () => import('../views/SystemSettings.vue')
      },
      {
        path: 'refund',
        name: 'RefundManage',
        component: () => import('../views/RefundManage.vue')
      },
      {
        path: 'products/add',
        name: 'ProductAdd',
        component: () => import('../views/ProductEdit.vue')
      },

      // ===== 第一阶段：核心运营模块 =====
      {
        path: 'cms',
        name: 'CmsManage',
        component: () => import('../views/CmsManage.vue')
      },
      {
        path: 'rbac',
        name: 'RbacManage',
        component: () => import('../views/RbacManage.vue')
      },
      {
        path: 'finance',
        name: 'FinanceManage',
        component: () => import('../views/FinanceManage.vue')
      },
      {
        path: 'inventory',
        name: 'InventoryManage',
        component: () => import('../views/InventoryManage.vue')
      },
      {
        path: 'push-manage',
        name: 'PushManage',
        component: () => import('../views/PushManage.vue')
      },
      {
        path: 'ticket',
        name: 'TicketManage',
        component: () => import('../views/TicketManage.vue')
      },

      // ===== 第二阶段：运营辅助模块 =====
      {
        path: 'campaign',
        name: 'CampaignCreate',
        component: () => import('../views/CampaignCreate.vue')
      },
      {
        path: 'complaint',
        name: 'ComplaintManage',
        component: () => import('../views/ComplaintManage.vue')
      },
      {
        path: 'review-manage',
        name: 'ReviewManage',
        component: () => import('../views/ReviewManage.vue')
      },
      {
        path: 'product-analysis',
        name: 'ProductAnalysis',
        component: () => import('../views/ProductAnalysis.vue')
      },
      {
        path: 'product-report',
        name: 'ProductReport',
        component: () => import('../views/ProductReport.vue')
      },
      {
        path: 'product-review',
        name: 'ProductReviewManage',
        component: () => import('../views/ProductReviewManage.vue')
      },
      {
        path: 'price-manage',
        name: 'PriceManage',
        component: () => import('../views/PriceManage.vue')
      },
      {
        path: 'price-history',
        name: 'PriceHistory',
        component: () => import('../views/PriceHistory.vue')
      },
      {
        path: 'order-export',
        name: 'OrderExport',
        component: () => import('../views/OrderExport.vue')
      },
      {
        path: 'order-intercept',
        name: 'OrderIntercept',
        component: () => import('../views/OrderIntercept.vue')
      },
      {
        path: 'order-monitor',
        name: 'OrderMonitor',
        component: () => import('../views/OrderMonitor.vue')
      },
      {
        path: 'order-price-modify',
        name: 'OrderPriceModify',
        component: () => import('../views/OrderPriceModify.vue')
      },
      {
        path: 'order-print',
        name: 'OrderPrint',
        component: () => import('../views/OrderPrint.vue')
      },
      {
        path: 'sms',
        name: 'SmsManage',
        component: () => import('../views/SmsManage.vue')
      },
      {
        path: 'sensitive-words',
        name: 'SensitiveWords',
        component: () => import('../views/SensitiveWords.vue')
      },

      // ===== 第三阶段：高级/专业模块 =====
      {
        path: 'funnel',
        name: 'FunnelAnalysis',
        component: () => import('../views/FunnelAnalysis.vue')
      },
      {
        path: 'rfm',
        name: 'RfmAnalysis',
        component: () => import('../views/RfmAnalysis.vue')
      },
      {
        path: 'risk-control',
        name: 'RiskControl',
        component: () => import('../views/RiskControl.vue')
      },
      {
        path: 'risk-rule-engine',
        name: 'RiskRuleEngine',
        component: () => import('../views/RiskRuleEngine.vue')
      },
      {
        path: 'realtime-screen',
        name: 'RealtimeScreen',
        component: () => import('../views/RealtimeScreen.vue')
      },
      {
        path: 'user-profile',
        name: 'UserProfile',
        component: () => import('../views/UserProfile.vue')
      },
      {
        path: 'ab-test',
        name: 'AbTest',
        component: () => import('../views/AbTest.vue')
      },
      {
        path: 'app-version',
        name: 'AppVersion',
        component: () => import('../views/AppVersion.vue')
      },
      {
        path: 'batch-import',
        name: 'BatchImport',
        component: () => import('../views/BatchImport.vue')
      },
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('../views/KnowledgeBase.vue')
      },
      {
        path: 'search-analysis',
        name: 'SearchAnalysis',
        component: () => import('../views/SearchAnalysis.vue')
      },
      {
        path: 'traffic-analysis',
        name: 'TrafficAnalysis',
        component: () => import('../views/TrafficAnalysis.vue')
      },
      {
        path: 'satisfaction',
        name: 'SatisfactionManage',
        component: () => import('../views/SatisfactionManage.vue')
      },
      {
        path: 'gdpr',
        name: 'GdprManage',
        component: () => import('../views/GdprManage.vue')
      },
      {
        path: 'audit-log',
        name: 'AuditLog',
        component: () => import('../views/AuditLog.vue')
      },

      // ===== 新增功能模块 =====
      {
        path: 'product-approval',
        name: 'ProductApproval',
        component: () => import('../views/ProductApproval.vue')
      },
      {
        path: 'coupon-manage',
        name: 'CouponManage',
        component: () => import('../views/CouponManage.vue')
      },
      {
        path: 'flash-sale-manage',
        name: 'FlashSaleManage',
        component: () => import('../views/FlashSaleManage.vue')
      },
      {
        path: 'blacklist',
        name: 'BlacklistManage',
        component: () => import('../views/BlacklistManage.vue')
      },
      {
        path: 'tariff',
        name: 'TariffManage',
        component: () => import('../views/TariffManage.vue')
      },
      {
        path: 'risk-alert',
        name: 'RiskAlert',
        component: () => import('../views/RiskAlert.vue')
      },
      {
        path: 'cs-sessions',
        name: 'CsSessions',
        component: () => import('../views/CsSessions.vue')
      },
      {
        path: 'order-tags',
        name: 'OrderTags',
        component: () => import('../views/OrderTags.vue')
      },
      {
        path: 'inventory-transfer',
        name: 'InventoryTransfer',
        component: () => import('../views/InventoryTransfer.vue')
      },

      // ===== 第四阶段：物流/订单/系统辅助模块 =====
      {
        path: 'merge-package',
        name: 'MergePackage',
        component: () => import('../views/MergePackage.vue')
      },
      {
        path: 'split-package',
        name: 'SplitPackage',
        component: () => import('../views/SplitPackage.vue')
      },
      {
        path: 'carrier-compare',
        name: 'CarrierCompare',
        component: () => import('../views/CarrierCompare.vue')
      },
      {
        path: 'overseas-warehouse',
        name: 'OverseasWarehouse',
        component: () => import('../views/OverseasWarehouse.vue')
      },
      {
        path: 'warehouse-manage',
        name: 'WarehouseManage',
        component: () => import('../views/WarehouseManage.vue')
      },
      {
        path: 'clearance',
        name: 'ClearanceManage',
        component: () => import('../views/ClearanceManage.vue')
      },
      {
        path: 'customs',
        name: 'CustomsManage',
        component: () => import('../views/CustomsManage.vue')
      },
      {
        path: 'settlement',
        name: 'SettlementManage',
        component: () => import('../views/SettlementManage.vue')
      },
      {
        path: 'settlement-detail',
        name: 'SettlementDetail',
        component: () => import('../views/SettlementDetail.vue')
      },
      {
        path: 'system-config',
        name: 'SystemConfig',
        component: () => import('../views/SystemConfig.vue')
      },
      {
        path: 'operation-log',
        name: 'OperationLog',
        component: () => import('../views/OperationLog.vue')
      },
      {
        path: 'live-manage',
        name: 'LiveManage',
        component: () => import('../views/LiveManage.vue')
      },
      {
        path: 'marketing-effect',
        name: 'MarketingEffect',
        component: () => import('../views/MarketingEffect.vue')
      },
      {
        path: 'shipping-strategy',
        name: 'ShippingStrategy',
        component: () => import('../views/ShippingStrategy.vue')
      },
      {
        path: 'content-review-detail',
        name: 'ContentReviewDetail',
        component: () => import('../views/ContentReviewDetail.vue')
      },
      {
        path: 'push-detail',
        name: 'PushDetail',
        component: () => import('../views/PushDetail.vue')
      },
      {
        path: 'complaint-handle',
        name: 'ComplaintHandle',
        component: () => import('../views/ComplaintHandle.vue')
      },

      // ===== 404 =====
      {
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('../views/NotFound.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
