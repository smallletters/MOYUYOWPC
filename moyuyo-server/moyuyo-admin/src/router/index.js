import { createRouter, createWebHistory } from 'vue-router'

const BASE_PATH = '/admin/'
const TOKEN_KEY = 'admin_token'

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
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('../views/OrderList.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('../views/OrderDetail.vue'),
        meta: { title: '订单详情' }
      },
      {
        path: 'products',
        name: 'ProductList',
        component: () => import('../views/ProductList.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'products/edit/:id',
        name: 'ProductEdit',
        component: () => import('../views/ProductEdit.vue'),
        meta: { title: '编辑商品' }
      },
      {
        path: 'users',
        name: 'UserList',
        component: () => import('../views/UserList.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'marketing',
        name: 'MarketingList',
        component: () => import('../views/MarketingList.vue'),
        meta: { title: '营销管理' }
      },
      {
        path: 'reviews',
        name: 'ContentReview',
        component: () => import('../views/ContentReview.vue'),
        meta: { title: '内容审核' }
      },
      {
        path: 'cs',
        name: 'CustomerService',
        component: () => import('../views/CustomerService.vue'),
        meta: { title: '客服管理' }
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('../views/Analytics.vue'),
        meta: { title: '数据分析' }
      },
      {
        path: 'logistics',
        name: 'LogisticsList',
        component: () => import('../views/LogisticsList.vue'),
        meta: { title: '物流管理' }
      },
      {
        path: 'settings',
        name: 'SystemSettings',
        component: () => import('../views/SystemSettings.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'refund',
        name: 'RefundManage',
        component: () => import('../views/RefundManage.vue'),
        meta: { title: '退款管理' }
      },
      {
        path: 'products/add',
        name: 'ProductAdd',
        component: () => import('../views/ProductEdit.vue'),
        meta: { title: '新增商品' }
      },

      // ===== 第一阶段：核心运营模块 =====
      {
        path: 'cms',
        name: 'CmsManage',
        component: () => import('../views/CmsManage.vue'),
        meta: { title: 'CMS内容管理' }
      },
      {
        path: 'rbac',
        name: 'RbacManage',
        component: () => import('../views/RbacManage.vue'),
        meta: { title: 'RBAC权限管理' }
      },
      {
        path: 'finance',
        name: 'FinanceManage',
        component: () => import('../views/FinanceManage.vue'),
        meta: { title: '财务概览' }
      },
      {
        path: 'inventory',
        name: 'InventoryManage',
        component: () => import('../views/InventoryManage.vue'),
        meta: { title: '库存管理' }
      },
      {
        path: 'push-manage',
        name: 'PushManage',
        component: () => import('../views/PushManage.vue'),
        meta: { title: '推送管理' }
      },
      {
        path: 'ticket',
        name: 'TicketManage',
        component: () => import('../views/TicketManage.vue'),
        meta: { title: '工单管理' }
      },

      // ===== 第二阶段：运营辅助模块 =====
      {
        path: 'campaign',
        name: 'CampaignCreate',
        component: () => import('../views/CampaignCreate.vue'),
        meta: { title: '活动创建' }
      },
      {
        // 活动详情：id 通过 query 传入（?id=xxx），MarketingList.vue的「查看详情」按钮跳转此路由
        path: 'campaign-detail',
        name: 'CampaignDetail',
        component: () => import('../views/CampaignDetail.vue'),
        meta: { title: '活动详情' }
      },
      {
        path: 'complaint',
        name: 'ComplaintManage',
        component: () => import('../views/ComplaintManage.vue'),
        meta: { title: '投诉管理' }
      },
      {
        path: 'review-manage',
        name: 'ReviewManage',
        component: () => import('../views/ReviewManage.vue'),
        meta: { title: '评价管理' }
      },
      {
        path: 'product-analysis',
        name: 'ProductAnalysis',
        component: () => import('../views/ProductAnalysis.vue'),
        meta: { title: '商品分析' }
      },
      {
        path: 'product-report',
        name: 'ProductReport',
        component: () => import('../views/ProductReport.vue'),
        meta: { title: '商品报表' }
      },
      {
        path: 'product-review',
        name: 'ProductReviewManage',
        component: () => import('../views/ProductReviewManage.vue'),
        meta: { title: '商品评价审核' }
      },
      {
        path: 'price-manage',
        name: 'PriceManage',
        component: () => import('../views/PriceManage.vue'),
        meta: { title: '价格管理' }
      },
      {
        path: 'price-history',
        name: 'PriceHistory',
        component: () => import('../views/PriceHistory.vue'),
        meta: { title: '价格历史' }
      },
      {
        path: 'order-export',
        name: 'OrderExport',
        component: () => import('../views/OrderExport.vue'),
        meta: { title: '订单导出' }
      },
      {
        path: 'order-intercept',
        name: 'OrderIntercept',
        component: () => import('../views/OrderIntercept.vue'),
        meta: { title: '订单拦截' }
      },
      {
        path: 'order-monitor',
        name: 'OrderMonitor',
        component: () => import('../views/OrderMonitor.vue'),
        meta: { title: '订单监控' }
      },
      {
        path: 'order-price-modify',
        name: 'OrderPriceModify',
        component: () => import('../views/OrderPriceModify.vue'),
        meta: { title: '订单改价' }
      },
      {
        path: 'order-print',
        name: 'OrderPrint',
        component: () => import('../views/OrderPrint.vue'),
        meta: { title: '订单打印' }
      },
      {
        path: 'sms',
        name: 'SmsManage',
        component: () => import('../views/SmsManage.vue'),
        meta: { title: '短信管理' }
      },
      {
        path: 'sensitive-words',
        name: 'SensitiveWords',
        component: () => import('../views/SensitiveWords.vue'),
        meta: { title: '敏感词管理' }
      },

      // ===== 第三阶段：高级/专业模块 =====
      {
        path: 'funnel',
        name: 'FunnelAnalysis',
        component: () => import('../views/FunnelAnalysis.vue'),
        meta: { title: '漏斗分析' }
      },
      {
        path: 'rfm',
        name: 'RfmAnalysis',
        component: () => import('../views/RfmAnalysis.vue'),
        meta: { title: 'RFM分析' }
      },
      {
        path: 'risk-control',
        name: 'RiskControl',
        component: () => import('../views/RiskControl.vue'),
        meta: { title: '风控管理' }
      },
      {
        path: 'risk-rule-engine',
        name: 'RiskRuleEngine',
        component: () => import('../views/RiskRuleEngine.vue'),
        meta: { title: '风控规则引擎' }
      },
      {
        path: 'realtime-screen',
        name: 'RealtimeScreen',
        component: () => import('../views/RealtimeScreen.vue'),
        meta: { title: '实时大屏' }
      },
      {
        path: 'user-profile',
        name: 'UserProfile',
        component: () => import('../views/UserProfile.vue'),
        meta: { title: '用户画像' }
      },
      {
        path: 'ab-test',
        name: 'AbTest',
        component: () => import('../views/AbTest.vue'),
        meta: { title: 'A/B测试' }
      },
      {
        path: 'app-version',
        name: 'AppVersion',
        component: () => import('../views/AppVersion.vue'),
        meta: { title: '应用版本管理' }
      },
      {
        path: 'batch-import',
        name: 'BatchImport',
        component: () => import('../views/BatchImport.vue'),
        meta: { title: '批量导入' }
      },
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('../views/KnowledgeBase.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'search-analysis',
        name: 'SearchAnalysis',
        component: () => import('../views/SearchAnalysis.vue'),
        meta: { title: '搜索分析' }
      },
      {
        path: 'traffic-analysis',
        name: 'TrafficAnalysis',
        component: () => import('../views/TrafficAnalysis.vue'),
        meta: { title: '流量分析' }
      },
      {
        path: 'satisfaction',
        name: 'SatisfactionManage',
        component: () => import('../views/SatisfactionManage.vue'),
        meta: { title: '满意度管理' }
      },
      {
        path: 'gdpr',
        name: 'GdprManage',
        component: () => import('../views/GdprManage.vue'),
        meta: { title: 'GDPR合规' }
      },
      {
        path: 'audit-log',
        name: 'AuditLog',
        component: () => import('../views/AuditLog.vue'),
        meta: { title: '审计日志' }
      },

      // ===== 新增功能模块 =====
      {
        path: 'product-approval',
        name: 'ProductApproval',
        component: () => import('../views/ProductApproval.vue'),
        meta: { title: '商品审核' }
      },
      {
        path: 'coupon-manage',
        name: 'CouponManage',
        component: () => import('../views/CouponManage.vue'),
        meta: { title: '优惠券管理' }
      },
      {
        path: 'flash-sale-manage',
        name: 'FlashSaleManage',
        component: () => import('../views/FlashSaleManage.vue'),
        meta: { title: '秒杀管理' }
      },
      {
        path: 'points-manage',
        name: 'PointsManage',
        component: () => import('../views/PointsManage.vue'),
        meta: { title: '积分管理' }
      },
      {
        path: 'blacklist',
        name: 'BlacklistManage',
        component: () => import('../views/BlacklistManage.vue'),
        meta: { title: '黑名单管理' }
      },
      {
        path: 'tariff',
        name: 'TariffManage',
        component: () => import('../views/TariffManage.vue'),
        meta: { title: '关税管理' }
      },
      {
        path: 'risk-alert',
        name: 'RiskAlert',
        component: () => import('../views/RiskAlert.vue'),
        meta: { title: '风控告警' }
      },
      {
        path: 'cs-sessions',
        name: 'CsSessions',
        component: () => import('../views/CsSessions.vue'),
        meta: { title: '客服会话' }
      },
      {
        path: 'cs-performance',
        name: 'CsPerformance',
        component: () => import('../views/CsPerformance.vue'),
        meta: { title: '客服绩效看板' }
      },
      {
        path: 'order-tags',
        name: 'OrderTags',
        component: () => import('../views/OrderTags.vue'),
        meta: { title: '订单标签' }
      },
      {
        path: 'inventory-transfer',
        name: 'InventoryTransfer',
        component: () => import('../views/InventoryTransfer.vue'),
        meta: { title: '库存调拨' }
      },

      // ===== 第四阶段：物流/订单/系统辅助模块 =====
      {
        path: 'merge-package',
        name: 'MergePackage',
        component: () => import('../views/MergePackage.vue'),
        meta: { title: '合包管理' }
      },
      {
        path: 'split-package',
        name: 'SplitPackage',
        component: () => import('../views/SplitPackage.vue'),
        meta: { title: '分包裹' }
      },
      {
        path: 'carrier-compare',
        name: 'CarrierCompare',
        component: () => import('../views/CarrierCompare.vue'),
        meta: { title: '承运商对比' }
      },
      {
        path: 'overseas-warehouse',
        name: 'OverseasWarehouse',
        component: () => import('../views/OverseasWarehouse.vue'),
        meta: { title: '海外仓管理' }
      },
      {
        path: 'warehouse-manage',
        name: 'WarehouseManage',
        component: () => import('../views/WarehouseManage.vue'),
        meta: { title: '仓库管理' }
      },
      {
        path: 'clearance',
        name: 'ClearanceManage',
        component: () => import('../views/ClearanceManage.vue'),
        meta: { title: '清关管理' }
      },
      {
        path: 'customs',
        name: 'CustomsManage',
        component: () => import('../views/CustomsManage.vue'),
        meta: { title: '海关管理' }
      },
      {
        path: 'settlement',
        name: 'SettlementManage',
        component: () => import('../views/SettlementManage.vue'),
        meta: { title: '结算管理' }
      },
      {
        path: 'settlement-detail',
        name: 'SettlementDetail',
        component: () => import('../views/SettlementDetail.vue'),
        meta: { title: '结算详情' }
      },
      {
        path: 'system-config',
        name: 'SystemConfig',
        component: () => import('../views/SystemConfig.vue'),
        meta: { title: '系统配置' }
      },
      {
        path: 'operation-log',
        name: 'OperationLog',
        component: () => import('../views/OperationLog.vue'),
        meta: { title: '运营日志' }
      },
      {
        path: 'live-manage',
        name: 'LiveManage',
        component: () => import('../views/LiveManage.vue'),
        meta: { title: '直播管理' }
      },
      {
        path: 'marketing-effect',
        name: 'MarketingEffect',
        component: () => import('../views/MarketingEffect.vue'),
        meta: { title: '营销效果' }
      },
      {
        path: 'shipping-strategy',
        name: 'ShippingStrategy',
        component: () => import('../views/ShippingStrategy.vue'),
        meta: { title: '发货策略' }
      },
      {
        path: 'content-review-detail',
        name: 'ContentReviewDetail',
        component: () => import('../views/ContentReviewDetail.vue'),
        meta: { title: '内容审核详情' }
      },
      {
        path: 'push-detail',
        name: 'PushDetail',
        component: () => import('../views/PushDetail.vue'),
        meta: { title: '推送详情' }
      },
      {
        path: 'complaint-handle',
        name: 'ComplaintHandle',
        component: () => import('../views/ComplaintHandle.vue'),
        meta: { title: '投诉处理详情' }
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
  history: createWebHistory(BASE_PATH),
  routes
})

router.beforeEach((to, from, next) => {
  // 同时检查 localStorage 与 sessionStorage，兼容"记住我"两种场景
  const token = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY)
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
