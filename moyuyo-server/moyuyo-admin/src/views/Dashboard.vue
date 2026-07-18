<template>
  <div class="dashboard-page">
    <h2 class="page-title">管理工作台</h2>

    <!-- KPI 卡片 -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="kpi in kpiList" :key="kpi.label">
        <div class="kpi-label">{{ kpi.label }}</div>
        <div class="kpi-value">{{ kpi.value }}</div>
        <div class="kpi-change" :class="kpi.trend === 'up' ? 'kpi-change--up' : 'kpi-change--down'">
          {{ kpi.change }}
          <span class="kpi-arrow">{{ kpi.trend === 'up' ? '↑' : '↓' }}</span>
        </div>
      </div>
    </div>

    <!-- 提示栏 -->
    <div class="alert-bar">
      <span class="alert-icon">📢</span>
      <span>{{ dashboardData.pendingShip || 0 }}笔待发货 {{ dashboardData.pendingReview || 0 }}条待审核 {{ dashboardData.pendingRefund || 0 }}笔退款申请</span>
    </div>

    <!-- 快捷操作 -->
    <div class="section-title">快捷操作</div>
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
        <table class="data-table">
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
              <td>{{ order.no }}</td>
              <td>{{ order.user }}</td>
              <td>¥{{ order.amount }}</td>
              <td><span :class="'tag-' + order.statusClass">{{ order.status }}</span></td>
              <td>{{ order.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 近7日销售额 -->
      <div class="bottom-panel">
        <div class="panel-header">
          <span class="panel-title">近7日销售额</span>
        </div>
        <div class="chart-bar">
          <div class="bar-item" v-for="item in salesData" :key="item.day">
            <div class="bar-track">
              <div
                class="bar-fill"
                :style="{ height: item.percent + '%' }"
              ></div>
            </div>
            <span class="bar-label">{{ item.day }}</span>
            <span class="bar-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardStats, getRecentOrders, getSalesTrend } from '../api/admin'

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
      { label: 'GMV', value: '¥' + (stats.todayGmv || 0), change: (stats.gmvTrend != null ? stats.gmvTrend + '%' : '0%'), trend: stats.gmvTrend >= 0 ? 'up' : 'down' },
      { label: '订单数', value: String(stats.todayOrders ?? 0), change: (stats.ordersTrend != null ? stats.ordersTrend + '%' : '0%'), trend: stats.ordersTrend >= 0 ? 'up' : 'down' },
      { label: '用户数', value: String(stats.activeUsers ?? 0), change: (stats.usersTrend != null ? stats.usersTrend + '%' : '0%'), trend: stats.usersTrend >= 0 ? 'up' : 'down' },
      { label: '转化率', value: (stats.conversionRate != null ? stats.conversionRate + '%' : '0%'), change: (stats.rateTrend != null ? stats.rateTrend + '%' : '0%'), trend: stats.rateTrend >= 0 ? 'up' : 'down' }
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
    recentOrders.value = (orders || []).map(order => ({
      id: order.orderNo || '',
      no: order.orderNo || '',
      user: order.productName || '',
      amount: order.amount != null ? String(order.amount) : '0.00',
      status: order.status || '未知',
      statusClass: 'gray',
      time: ''
    }))
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
      value: '¥' + (item.value || 0),
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

onMounted(async () => {
  await Promise.all([loadDashboardData(), loadRecentOrders(), loadSalesTrend()])
})
</script>

<style scoped lang="css">
.dashboard-page {
  max-width: 1200px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0 0 24px 0;
}

/* KPI 卡片网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.kpi-label {
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 4px;
}

.kpi-change {
  font-size: 13px;
  font-weight: 500;
}

.kpi-change--up {
  color: #10b981;
}

.kpi-change--down {
  color: #ef4444;
}

.kpi-arrow {
  margin-left: 2px;
}

/* 提示栏 */
.alert-bar {
  background: #fff;
  border-radius: 10px;
  padding: 14px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #1d1d1f;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
  border-left: 4px solid #f59e0b;
}

.alert-icon {
  font-size: 18px;
}

/* 分区标题 */
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 14px;
}

/* 快捷操作 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 28px;
}

.quick-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.quick-icon {
  font-size: 28px;
}

.quick-label {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

/* 底部区域 */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.bottom-panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
}

.panel-more {
  font-size: 13px;
  color: #2563eb;
  text-decoration: none;
}

.panel-more:hover {
  text-decoration: underline;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.data-table td {
  padding: 10px 12px;
  font-size: 13px;
  color: #333;
  border-bottom: 1px solid #f5f5f7;
}

/* 柱状图 */
.chart-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  height: 200px;
  padding-top: 20px;
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

.bar-track {
  width: 100%;
  max-width: 40px;
  height: 140px;
  background: #f0f0f0;
  border-radius: 6px 6px 0 0;
  position: relative;
  display: flex;
  align-items: flex-end;
}

.bar-fill {
  width: 100%;
  background: linear-gradient(180deg, #2563eb, #60a5fa);
  border-radius: 6px 6px 0 0;
  transition: height 0.3s;
  min-height: 4px;
}

.bar-label {
  font-size: 11px;
  color: #8e8e93;
}

.bar-value {
  font-size: 10px;
  color: #8e8e93;
}
</style>
