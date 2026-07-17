<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>商品报表</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleExport">导出报表</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
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
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="sales" label="销量" width="80" sortable />
        <el-table-column prop="revenue" label="销售额" width="120" sortable>
          <template #default="{ row }">¥{{ row.revenue.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="profit" label="利润" width="120" sortable>
          <template #default="{ row }">¥{{ row.profit.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="profitRate" label="利润率" width="100" sortable>
          <template #default="{ row }">{{ row.profitRate }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
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
const dateRange = ref(null)

const filters = reactive({
  keyword: ''
})

const mockData = [
  { id: 1, productName: '无线蓝牙耳机 Pro', sku: 'BT-PRO-001', sales: 1200, revenue: 179880, profit: 53964, profitRate: 30 },
  { id: 2, productName: '智能手表S3', sku: 'SW-S3-002', sales: 680, revenue: 231200, profit: 69360, profitRate: 30 },
  { id: 3, productName: '运动休闲鞋', sku: 'SNEAKER-003', sales: 890, revenue: 106800, profit: 32040, profitRate: 30 },
  { id: 4, productName: '不锈钢保温杯', sku: 'CUP-004', sales: 1500, revenue: 89700, profit: 31395, profitRate: 35 },
  { id: 5, productName: '轻薄笔记本电脑包', sku: 'BAG-005', sales: 420, revenue: 62700, profit: 18810, profitRate: 30 }
]

const tableData = ref([])

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.productName.toLowerCase().includes(kw)) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; dateRange.value = null; handleSearch() }

function handleExport() {
  ElMessage.success('报表导出中，请稍后下载')
}

function handleDetail(row) {
  ElMessage.info('查看报表详情：' + row.productName)
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
