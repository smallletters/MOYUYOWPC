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
import { getOrderOpsExport, batchShip } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  interceptStatus: ''
})

const tableData = ref([])

function interceptTag(status) {
  const map = { '待拦截': 'warning', '已拦截': 'danger', '已放行': 'success' }
  return map[status] || ''
}

// 加载拦截订单列表
async function loadData() {
  try {
    const res = await getOrderOpsExport()
    let list = res || []
    const kw = filters.keyword.toLowerCase()
    if (kw) {
      list = list.filter(d => (d.orderNo || '').toLowerCase().includes(kw))
    }
    if (filters.interceptStatus) {
      list = list.filter(d => d.interceptStatus === filters.interceptStatus)
    }
    total.value = list.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = list.slice(start, start + pageSize.value)
  } catch (error) {
    console.error('获取拦截订单数据失败:', error)
    ElMessage.error('获取拦截订单数据失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; filters.interceptStatus = ''; handleSearch() }

// 执行订单拦截
async function handleIntercept(row) {
  try {
    await ElMessageBox.confirm('确认拦截订单 ' + row.orderNo + ' 吗？', '提示', { type: 'warning' })
    await batchShip({ orderIds: [row.id], action: 'intercept' })
    ElMessage.success('已成功拦截')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('拦截订单失败:', error)
      ElMessage.error('拦截订单失败')
    }
  }
}

// 执行订单放行
async function handleRelease(row) {
  try {
    await ElMessageBox.confirm('确认放行订单 ' + row.orderNo + ' 吗？', '提示', { type: 'info' })
    await batchShip({ orderIds: [row.id], action: 'release' })
    ElMessage.success('已放行')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('放行订单失败:', error)
      ElMessage.error('放行订单失败')
    }
  }
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
