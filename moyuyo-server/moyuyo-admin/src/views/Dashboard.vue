<template>
  <div class="dashboard-page">
    <!-- 页面标题区域（设计系统标准化） -->
    <div class="page-title-area">
      <h1>管理工作台</h1>
      <p>今日运营数据概览</p>
    </div>

    <!-- KPI 卡片网格（使用设计系统 kpi-card 类） -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="kpi in kpiList" :key="kpi.label">
        <div class="kpi-card-header">
          <div class="kpi-card-icon-box" :style="{ background: kpi.iconBg }">
            <span style="font-size:16px">{{ kpi.icon }}</span>
          </div>
          <span class="kpi-card-label">{{ kpi.label }}</span>
        </div>
        <div class="kpi-card-value">{{ kpi.value }}</div>
        <div :class="kpi.trend === 'up' ? 'kpi-trend-up' : 'kpi-trend-down'" style="font-size:13px;font-weight:500">
          {{ kpi.change }}
          <span>{{ kpi.trend === 'up' ? '↑' : '↓' }}</span>
        </div>
      </div>
    </div>

    <!-- 待办提示栏 -->
    <div class="alert-bar">
      <span class="alert-icon">📢</span>
      <span>
        <strong>{{ dashboardData.pendingShip || 0 }}</strong> 笔待发货
        &nbsp;·&nbsp;
        <strong>{{ dashboardData.pendingReview || 0 }}</strong> 条待审核
        &nbsp;·&nbsp;
        <strong>{{ dashboardData.pendingRefund || 0 }}</strong> 笔退款申请
      </span>
    </div>

    <!-- 快捷操作 -->
    <h3 style="font-size:15px;font-weight:600;color:var(--text-800);margin:0 0 14px">快捷操作</h3>
    <div class="quick-grid">
      <div
        class="quick-card"
        v-for="action in quickActions"
        :key="action.label"
        @click="handleQuickAction(action)"
      >
        <span class="quick-icon">{{ action.icon }}</span>
        <span class="quick-label">{{ action.label }}</span>
      </div>
    </div>

    <!-- 底部区域 -->
    <div class="bottom-grid">
      <!-- 最近订单 -->
      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">最近订单</span>
          <router-link to="/orders" class="panel-more">查看全部 →</router-link>
        </div>
        <table class="data-table" v-if="recentOrders.length > 0">
          <thead>
            <tr>
              <th>订单号</th>
              <th>用户</th>
              <th>金额</th>
              <th>状态</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in recentOrders" :key="order.id">
              <td class="cell-mono">{{ order.no }}</td>
              <td>{{ order.user }}</td>
              <td class="cell-mono">${{ order.amount }}</td>
              <td><span :class="'tag tag-' + order.statusClass">{{ order.status }}</span></td>
              <td class="cell-mono">{{ order.time }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">暂无订单数据</div>
      </div>

      <!-- 近7日销售额 -->
      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">近7日销售额</span>
        </div>
        <div class="chart-area" v-if="salesData.length > 0">
          <div class="chart-bar">
            <div class="bar-item" v-for="item in salesData" :key="item.day">
              <span class="bar-value-text">{{ item.value }}</span>
              <div class="bar-track-v">
                <div class="bar-fill-v" :style="{ height: item.percent + '%' }"></div>
              </div>
              <span class="bar-label">{{ item.day }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无销售数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardStats, getRecentOrders, getSalesTrend } from '../api/admin'

const TOKEN_KEY = 'admin_token'

const router = useRouter()

const dashboardData = ref({})
const kpiList = ref([])
const recentOrders = ref([])
const salesData = ref([])

const quickActions = [
  { icon: '📦', label: '发布商品', path: '/products' },
  { icon: '🛒', label: '查看订单', path: '/orders' },
  { icon: '👥', label: '用户管理', path: '/users' },
  { icon: '📢', label: '创建活动', path: '/marketing' },
  { icon: '📝', label: '内容审核', path: '/reviews' },
  { icon: '🎧', label: '客服消息', path: '/cs' },
  { icon: '📈', label: '数据报表', path: '/analytics' },
  { icon: '⚙️', label: '系统设置', path: '/settings' }
]

// 加载仪表盘统计数据
async function loadDashboardData() {
  try {
    const stats = await getDashboardStats()
    dashboardData.value = stats
    kpiList.value = [
      { label: '今日 GMV', value: '$' + (stats.todayGmv || 0).toFixed(2), change: (stats.gmvTrend != null ? stats.gmvTrend + '%' : '0%'), trend: stats.gmvTrend >= 0 ? 'up' : 'down', icon: '💰', iconBg: 'rgba(16,185,129,0.12)' },
      { label: '订单数', value: String(stats.todayOrders ?? 0), change: (stats.ordersTrend != null ? stats.ordersTrend + '%' : '0%'), trend: stats.ordersTrend >= 0 ? 'up' : 'down', icon: '🛒', iconBg: 'rgba(37,99,235,0.12)' },
      { label: '活跃用户', value: String(stats.activeUsers ?? 0), change: (stats.usersTrend != null ? stats.usersTrend + '%' : '0%'), trend: stats.usersTrend >= 0 ? 'up' : 'down', icon: '👤', iconBg: 'rgba(139,92,246,0.12)' },
      { label: '转化率', value: (stats.conversionRate != null ? stats.conversionRate + '%' : '0%'), change: (stats.rateTrend != null ? stats.rateTrend + '%' : '0%'), trend: stats.rateTrend >= 0 ? 'up' : 'down', icon: '📊', iconBg: 'rgba(245,158,11,0.12)' }
    ]
  } catch (e) {
    console.error('获取仪表盘统计失败', e)
    ElMessage.error('获取仪表盘数据失败')
  }
}

// 加载最近订单
async function loadRecentOrders() {
  try {
    const orders = await getRecentOrders()
    recentOrders.value = (orders || []).map(order => {
      const statusClassMap = {
        'PENDING_PAY': 'gray',
        'PENDING_SHIP': 'blue',
        'SHIPPED': 'warning',
        'COMPLETED': 'green',
        'CANCELLED': 'red',
        'REFUNDED': 'red'
      }
      const statusMap = {
        'PENDING_PAY': '待支付',
        'PENDING_SHIP': '待发货',
        'SHIPPED': '已发货',
        'COMPLETED': '已完成',
        'CANCELLED': '已取消',
        'REFUNDED': '已退款'
      }
      return {
        id: order.orderNo || '',
        no: order.orderNo || '',
        user: order.userName || order.productName || '',
        amount: order.payAmount != null ? String(order.payAmount) : (order.amount != null ? String(order.amount) : '0.00'),
        status: statusMap[order.status] || order.status || '未知',
        statusClass: statusClassMap[order.status] || 'gray',
        time: order.createTime ? order.createTime.substring(0, 10) : (order.paidAt ? order.paidAt.substring(0, 10) : '')
      }
    })
  } catch (e) {
    console.error('获取最近订单失败', e)
    ElMessage.error('获取最近订单失败')
  }
}

// 加载销售额趋势
async function loadSalesTrend() {
  try {
    const trend = await getSalesTrend()
    const items = trend || []
    const maxValue = Math.max(...items.map(i => Number(i.value || 0)), 1)
    salesData.value = items.map(item => ({
      day: item.day || '',
      value: '$' + (item.value || 0),
      percent: Number(item.value || 0) / maxValue * 100
    }))
  } catch (e) {
    console.error('获取销售额趋势失败', e)
    ElMessage.error('获取销售额趋势失败')
  }
}

function handleQuickAction(action) {
  router.push(action.path)
}

onMounted(() => {
  if (!localStorage.getItem(TOKEN_KEY)) {
    router.push('/login')
    return
  }
  Promise.all([loadDashboardData(), loadRecentOrders(), loadSalesTrend()])
})
</script>

<style scoped>
.dashboard-page {
  max-width: 1200px;
}

/* KPI 卡片网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

/* 提示栏 */
.alert-bar {
  background: var(--card);
  border-radius: var(--radius);
  padding: 14px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-700);
  box-shadow: var(--shadow-xs);
  margin-bottom: 24px;
  border-left: 4px solid var(--state-warning);
}
.alert-icon { font-size: 18px; }

/* 快捷操作 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 28px;
}
.quick-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: var(--shadow-xs);
  border: 1px solid var(--border);
}
.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--brand-200);
}
.quick-icon { font-size: 28px; }
.quick-label { font-size: 13px; color: var(--text-600); font-weight: 500; }

/* 底部区域 */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.bottom-panel {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  box-shadow: var(--shadow-xs);
  border: 1px solid var(--border);
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.panel-title { font-size: 15px; font-weight: 600; color: var(--text-800); }
.panel-more { font-size: 13px; color: var(--primary); text-decoration: none; }
.panel-more:hover { text-decoration: underline; }

/* 表格 */
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-400);
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
}
.data-table td {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--text-600);
  border-bottom: 1px solid var(--background-200);
}
.cell-mono { font-family: 'SF Mono', 'Menlo', monospace; font-variant-numeric: tabular-nums; }
.empty-state { text-align: center; padding: 40px 0; color: var(--text-400); font-size: 14px; }

/* 柱状图 */
.chart-area { padding-top: 20px; }
.chart-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  height: 200px;
}
.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
}
.bar-track-v {
  width: 100%;
  max-width: 40px;
  height: 140px;
  background: var(--background-200);
  border-radius: 6px 6px 0 0;
  position: relative;
  display: flex;
  align-items: flex-end;
}
.bar-fill-v {
  width: 100%;
  background: linear-gradient(180deg, var(--brand-500), var(--brand-300));
  border-radius: 6px 6px 0 0;
  transition: height 0.3s;
  min-height: 4px;
}
.bar-label { font-size: 11px; color: var(--text-400); }
.bar-value-text { font-size: 10px; color: var(--text-400); font-variant-numeric: tabular-nums; }
</style>
