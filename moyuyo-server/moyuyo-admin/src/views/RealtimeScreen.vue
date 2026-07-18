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
    <!-- 下方面板 -->
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
import { getRealtimeData, getRealtimeOrderFlow, getRealtimeTopProducts } from '../api/admin'

const kpi = reactive({
  onlineUsers: '—',
  todayVisitors: '—',
  todayOrders: '—',
  todaySales: '—'
})

const realtimeOrders = ref([])
const hotProducts = ref([])

let timer = null

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
  }
}

async function loadOrders() {
  try {
    const res = await getRealtimeOrderFlow()
    realtimeOrders.value = res || []
  } catch (err) {
    console.error('获取实时订单流失败', err)
  }
}

async function loadHotProducts() {
  try {
    const res = await getRealtimeTopProducts()
    hotProducts.value = res || []
  } catch (err) {
    console.error('获取热门商品失败', err)
  }
}

onMounted(() => {
  loadKpi()
  loadOrders()
  loadHotProducts()
  // 定时刷新
  timer = setInterval(() => {
    loadKpi()
    loadOrders()
    loadHotProducts()
  }, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.live-dot { display: inline-block; width: 8px; height: 8px; background: #52c41a; border-radius: 50%; margin-right: 6px; vertical-align: middle; animation: blink 1s infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
.kpi-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.live-value { color: #52c41a; }
.order-scroll-wrap { overflow: hidden; }
.rank-list { display: flex; flex-direction: column; gap: 12px; padding: 8px 0; }
.rank-item { display: flex; align-items: center; gap: 12px; }
.rank-num { width: 24px; height: 24px; border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: var(--text-500); background: var(--background-100); }
.rank-num.rank-top { background: #f5222d; color: #fff; }
.rank-name { flex: 1; font-size: 14px; color: var(--text-700); }
.rank-sales { font-size: 13px; color: var(--text-400); font-weight: 500; }
</style>
