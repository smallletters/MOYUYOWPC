<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单拦截</h2>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="拦截状态">
          <el-select v-model="filters.interceptStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待拦截" value="待拦截" />
            <el-option label="已拦截" value="已拦截" />
            <el-option label="已放行" value="已放行" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="product" label="商品" min-width="160" />
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="currentStatus" label="当前状态" width="100" />
        <el-table-column prop="interceptStatus" label="拦截状态" width="110">
          <template #default="{ row }">
            <el-tag :type="interceptTag(row.interceptStatus)" size="small">{{ row.interceptStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="handleIntercept(row)" :disabled="row.interceptStatus !== '待拦截'">拦截</el-button>
            <el-button size="small" type="info" @click="handleRelease(row)" :disabled="row.interceptStatus !== '已拦截'">放行</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  interceptStatus: ''
})

const mockData = [
  { id: 1, orderNo: 'MOY20260717001', product: '无线蓝牙耳机 Pro', amount: 298, currentStatus: '待发货', interceptStatus: '待拦截', orderTime: '2026-07-17 10:30:00' },
  { id: 2, orderNo: 'MOY20260717002', product: '智能手表S3', amount: 678, currentStatus: '已付款', interceptStatus: '待拦截', orderTime: '2026-07-17 11:20:00' },
  { id: 3, orderNo: 'MOY20260716003', product: '运动休闲鞋', amount: 398, currentStatus: '已发货', interceptStatus: '已拦截', orderTime: '2026-07-16 14:45:00' },
  { id: 4, orderNo: 'MOY20260715004', product: '不锈钢保温杯', amount: 118, currentStatus: '待发货', interceptStatus: '已放行', orderTime: '2026-07-15 09:10:00' },
  { id: 5, orderNo: 'MOY20260714005', product: '轻薄笔记本电脑包', amount: 377, currentStatus: '已付款', interceptStatus: '待拦截', orderTime: '2026-07-14 16:30:00' }
]

const tableData = ref([])

function interceptTag(status) {
  const map = { '待拦截': 'warning', '已拦截': 'danger', '已放行': 'success' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.orderNo.toLowerCase().includes(kw)) return false
    if (filters.interceptStatus && d.interceptStatus !== filters.interceptStatus) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; filters.interceptStatus = ''; handleSearch() }

function handleIntercept(row) {
  ElMessageBox.confirm('确认拦截订单 ' + row.orderNo + ' 吗？', '提示', { type: 'warning' }).then(() => {
    row.interceptStatus = '已拦截'
    loadData()
    ElMessage.success('已成功拦截')
  }).catch(() => {})
}

function handleRelease(row) {
  ElMessageBox.confirm('确认放行订单 ' + row.orderNo + ' 吗？', '提示', { type: 'info' }).then(() => {
    row.interceptStatus = '已放行'
    loadData()
    ElMessage.success('已放行')
  }).catch(() => {})
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
