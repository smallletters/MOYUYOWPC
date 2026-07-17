<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单监控</h2>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">今日订单</div>
            <div class="kpi-value" style="color:#409eff">{{ kpiData.todayOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">待发货</div>
            <div class="kpi-value" style="color:#e6a23c">{{ kpiData.pendingShip }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">异常订单</div>
            <div class="kpi-value" style="color:#f56c6c">{{ kpiData.abnormalOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">拦截中</div>
            <div class="kpi-value" style="color:#f59e0b">{{ kpiData.intercepting }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="product" label="商品" min-width="160" />
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="abnormalType" label="异常类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.abnormalType" type="danger" size="small">{{ row.abnormalType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const kpiData = reactive({
  todayOrders: 186,
  pendingShip: 42,
  abnormalOrders: 7,
  intercepting: 3
})

const mockData = [
  { id: 1, orderNo: 'MOY20260717001', product: '无线蓝牙耳机 Pro', amount: 298, status: '待发货', abnormalType: '', updateTime: '2026-07-17 10:30:00' },
  { id: 2, orderNo: 'MOY20260717002', product: '智能手表S3', amount: 678, status: '已付款', abnormalType: '支付异常', updateTime: '2026-07-17 11:20:00' },
  { id: 3, orderNo: 'MOY20260717003', product: '运动休闲鞋', amount: 398, status: '已拦截', abnormalType: '地址异常', updateTime: '2026-07-17 09:15:00' },
  { id: 4, orderNo: 'MOY20260716004', product: '不锈钢保温杯', amount: 118, status: '已完成', abnormalType: '', updateTime: '2026-07-16 18:00:00' },
  { id: 5, orderNo: 'MOY20260716005', product: '轻薄笔记本电脑包', amount: 377, status: '待发货', abnormalType: '缺货', updateTime: '2026-07-16 14:30:00' }
]

const tableData = ref([])

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  total.value = mockData.length
  tableData.value = mockData.slice(start, start + pageSize.value)
}

function handleDetail(row) {
  ElMessage.info('订单详情：' + row.orderNo)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
