<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>实时大屏</h2>
      <el-tag type="success" effect="dark">
        <span class="live-dot"></span> 实时更新中
      </el-tag>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">实时在线用户</div>
            <div class="kpi-value live-value">{{ kpi.onlineUsers }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">今日访客</div>
            <div class="kpi-value">{{ kpi.todayVisitors }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">今日订单数</div>
            <div class="kpi-value">{{ kpi.todayOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">今日销售额</div>
            <div class="kpi-value">¥{{ kpi.todaySales }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 第二行：GMV 趋势 + 今日发货 -->
    <el-row :gutter="16" class="middle-row">
      <!-- GMV 趋势（今日/昨日 按小时分布柱状图，纯 CSS 实现） -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>GMV 趋势</span>
              <!-- 图例 -->
              <div class="legend">
                <span class="legend-item"><i class="legend-dot legend-today"></i>今日</span>
                <span class="legend-item"><i class="legend-dot legend-yesterday"></i>昨日</span>
              </div>
            </div>
          </template>
          <!-- 柱状图区域 -->
          <div class="gmv-chart">
            <div v-for="item in gmvTrend" :key="item.hour" class="bar-group" :title="item.hour + ' 时'">
              <div class="bar bar-yesterday" :style="{ height: item.yesterday + '%' }"></div>
              <div class="bar bar-today" :style="{ height: item.today + '%' }"></div>
            </div>
          </div>
          <!-- X 轴时间标签 -->
          <div class="gmv-x-axis">
            <span>00</span>
            <span>03</span>
            <span>06</span>
            <span>09</span>
            <span>12</span>
            <span class="x-current">{{ gmvTrend[gmvTrend.length - 1].hour }}</span>
          </div>
        </el-card>
      </el-col>
      <!-- 今日发货 -->
      <el-col :span="10">
        <el-card shadow="never" class="ship-card">
          <template #header><span>今日发货</span></template>
          <div class="ship-grid">
            <div class="ship-item">
              <div class="ship-icon ship-icon-warning"></div>
              <p class="ship-value">{{ shipping.pending }}</p>
              <span class="status-badge status-warning">待发货</span>
            </div>
            <div class="ship-item">
              <div class="ship-icon ship-icon-success"></div>
              <p class="ship-value">{{ shipping.shipped }}</p>
              <span class="status-badge status-success">已发货</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 第三行：实时订单流 + 热门商品排行 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span>实时订单流</span></template>
          <div class="order-scroll-wrap">
            <el-table :data="realtimeOrders" stripe style="width: 100%" :show-header="true" height="400">
              <el-table-column prop="orderNo" label="订单号" width="160" />
              <el-table-column prop="user" label="用户" width="100" />
              <el-table-column prop="amount" label="金额" width="100">
                <template #default="{ row }">¥{{ row.amount }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === '支付成功' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="time" label="时间" width="160" />
            </el-table>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>热门商品排行榜</span></template>
          <div class="rank-list">
            <div v-for="(item, idx) in hotProducts" :key="item.id" class="rank-item">
              <span class="rank-num" :class="{ 'rank-top': idx < 3 }">{{ idx + 1 }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <span class="rank-sales">{{ item.sales }} 单</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRealtimeData, getRealtimeOrderFlow, getRealtimeTopProducts } from '../api/admin'
import { toArray } from '../utils/safeArray'

const kpi = reactive({
  onlineUsers: '—',
  todayVisitors: '—',
  todayOrders: '—',
  todaySales: '—'
})

const realtimeOrders = ref([])
const hotProducts = ref([])

// 示例数据：GMV 趋势（今日/昨日 00-13 时按小时占比 %，无真实接口时使用）
// 数值参考设计稿柱状图形态：夜间低谷、8-11 时高峰、午间回落
const gmvTrend = ref([
  { hour: '00', today: 5, yesterday: 8 },
  { hour: '01', today: 3, yesterday: 4 },
  { hour: '02', today: 2, yesterday: 3 },
  { hour: '03', today: 2, yesterday: 2 },
  { hour: '04', today: 2, yesterday: 3 },
  { hour: '05', today: 8, yesterday: 6 },
  { hour: '06', today: 18, yesterday: 15 },
  { hour: '07', today: 32, yesterday: 28 },
  { hour: '08', today: 50, yesterday: 45 },
  { hour: '09', today: 65, yesterday: 58 },
  { hour: '10', today: 78, yesterday: 72 },
  { hour: '11', today: 92, yesterday: 85 },
  { hour: '12', today: 75, yesterday: 68 },
  { hour: '13', today: 88, yesterday: 80 }
])

// 示例数据：今日发货（待发货 / 已发货，无真实接口时使用）
const shipping = reactive({
  pending: 186,
  shipped: 997
})

let timer = null
let gmvTimer = null

async function loadKpi() {
  try {
    const res = await getRealtimeData()
    if (res) {
      kpi.onlineUsers = res.onlineUsers ?? '—'
      kpi.todayVisitors = res.todayVisitors ?? '—'
      kpi.todayOrders = res.todayOrders ?? '—'
      kpi.todaySales = res.todaySales ?? '—'
    }
  } catch (err) {
    console.error('获取实时数据失败', err)
    ElMessage.warning('实时数据加载失败，将自动重试')
  }
}

async function loadOrders() {
  try {
    const res = await getRealtimeOrderFlow()
    realtimeOrders.value = toArray(res)
  } catch (err) {
    console.error('获取实时订单流失败', err)
    // 订单流刷新失败不弹窗，避免干扰大屏展示
  }
}

async function loadHotProducts() {
  try {
    const res = await getRealtimeTopProducts()
    hotProducts.value = toArray(res)
  } catch (err) {
    console.error('获取热门商品失败', err)
    // 热门商品刷新失败不弹窗，避免干扰大屏展示
  }
}

// 模拟实时更新：微调最后一小时（当前时段）的今日柱与待发货数，营造实时感
function simulateRealtime() {
  const last = gmvTrend.value[gmvTrend.value.length - 1]
  last.today = Math.min(100, Math.max(10, last.today + Math.round(Math.random() * 6 - 2)))
  shipping.pending = Math.max(0, shipping.pending + Math.round(Math.random() * 4 - 2))
}

onMounted(() => {
  loadKpi()
  loadOrders()
  loadHotProducts()
  // 定时刷新（30 秒同步一次业务数据）
  timer = setInterval(() => {
    loadKpi()
    loadOrders()
    loadHotProducts()
  }, 30000)
  // 模拟实时更新（5 秒一次，仅作用于示例数据）
  gmvTimer = setInterval(simulateRealtime, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (gmvTimer) clearInterval(gmvTimer)
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.live-dot { display: inline-block; width: 8px; height: 8px; background: #52c41a; border-radius: 50%; margin-right: 6px; vertical-align: middle; animation: blink 1s infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
.kpi-row { margin-bottom: 16px; }
.middle-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.live-value { color: #52c41a; }
/* 卡片头部：标题 + 图例 */
.card-header { display: flex; justify-content: space-between; align-items: center; }
.legend { display: flex; align-items: center; gap: 12px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-400); }
.legend-dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; }
.legend-today { background: var(--brand-500); }
.legend-yesterday { background: var(--text-300); opacity: 0.5; }
/* GMV 趋势柱状图（纯 CSS） */
.gmv-chart { display: flex; align-items: flex-end; gap: 4px; height: 200px; padding: 4px 2px 0; }
.bar-group { flex: 1; display: flex; align-items: flex-end; gap: 3px; height: 100%; }
.bar { flex: 1; min-height: 2px; border-radius: 4px 4px 0 0; transition: height 0.6s ease; }
.bar-today { background: var(--brand-500); }
.bar-yesterday { background: var(--text-300); opacity: 0.5; }
.gmv-x-axis { display: flex; justify-content: space-between; margin-top: 8px; padding: 0 2px; font-size: 12px; color: var(--text-400); }
.gmv-x-axis .x-current { color: var(--brand-500); font-weight: 600; }
/* 今日发货 */
.ship-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 12px 0; }
.ship-item { background: var(--background-100); border-radius: 12px; padding: 16px 0; text-align: center; }
.ship-icon { width: 40px; height: 40px; border-radius: 50%; margin: 0 auto 8px; }
.ship-icon-warning { background: rgba(255, 149, 0, 0.15); }
.ship-icon-success { background: rgba(52, 199, 89, 0.15); }
.ship-value { font-size: 24px; font-weight: 700; color: var(--text-800); line-height: 1.2; }
.status-badge { display: inline-block; margin-top: 8px; padding: 2px 10px; border-radius: 999px; font-size: 12px; }
.status-warning { background: rgba(255, 149, 0, 0.12); color: var(--state-warning); }
.status-success { background: rgba(52, 199, 89, 0.12); color: var(--state-success); }
.order-scroll-wrap { overflow: hidden; }
.rank-list { display: flex; flex-direction: column; gap: 12px; padding: 8px 0; }
.rank-item { display: flex; align-items: center; gap: 12px; }
.rank-num { width: 24px; height: 24px; border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: var(--text-500); background: var(--background-100); }
.rank-num.rank-top { background: #f5222d; color: #fff; }
.rank-name { flex: 1; font-size: 14px; color: var(--text-700); }
.rank-sales { font-size: 13px; color: var(--text-400); font-weight: 500; }
</style>
