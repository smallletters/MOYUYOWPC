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

const kpi = reactive({
  onlineUsers: 1283,
  todayVisitors: '12,580',
  todayOrders: 356,
  todaySales: '45,680.00'
})

const realtimeOrders = ref([
  { orderNo: 'ORD-20260717-0001', user: '张*', amount: '299.00', status: '支付成功', time: '14:32:18' },
  { orderNo: 'ORD-20260717-0002', user: '李*', amount: '159.00', status: '支付成功', time: '14:31:45' },
  { orderNo: 'ORD-20260717-0003', user: '王*', amount: '899.00', status: '待支付', time: '14:31:02' },
  { orderNo: 'ORD-20260717-0004', user: '赵*', amount: '59.90', status: '支付成功', time: '14:30:30' },
  { orderNo: 'ORD-20260717-0005', user: '孙*', amount: '1,299.00', status: '支付成功', time: '14:29:58' },
  { orderNo: 'ORD-20260717-0006', user: '周*', amount: '445.00', status: '待支付', time: '14:29:12' },
  { orderNo: 'ORD-20260717-0007', user: '吴*', amount: '89.00', status: '支付成功', time: '14:28:40' },
  { orderNo: 'ORD-20260717-0008', user: '郑*', amount: '689.00', status: '支付成功', time: '14:28:05' },
])

const hotProducts = ref([
  { id: 1, name: '纯棉圆领T恤', sales: 1280 },
  { id: 2, name: '无线蓝牙耳机', sales: 856 },
  { id: 3, name: '不锈钢保温杯', sales: 756 },
  { id: 4, name: '运动速干短裤', sales: 567 },
  { id: 5, name: '机械键盘 87键', sales: 445 },
  { id: 6, name: 'USB-C 扩展坞', sales: 410 },
  { id: 7, name: '简约双肩背包', sales: 334 },
  { id: 8, name: '智能手环', sales: 189 },
])

let timer = null

function simulateRealtime() {
  kpi.onlineUsers += Math.floor(Math.random() * 5) - 2
  if (kpi.onlineUsers < 0) kpi.onlineUsers = 0
  // 模拟新订单
  if (Math.random() > 0.6) {
    const idx = Math.floor(Math.random() * 8) + 1
    realtimeOrders.value.unshift({
      orderNo: `ORD-20260717-${String(idx + 8).padStart(4, '0')}`,
      user: '匿名用户',
      amount: (Math.random() * 1000 + 50).toFixed(2),
      status: Math.random() > 0.3 ? '支付成功' : '待支付',
      time: new Date().toLocaleTimeString()
    })
    if (realtimeOrders.value.length > 20) realtimeOrders.value.pop()
  }
}

onMounted(() => {
  timer = setInterval(simulateRealtime, 5000)
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
